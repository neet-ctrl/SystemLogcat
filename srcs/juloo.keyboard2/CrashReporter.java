package juloo.keyboard2;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Intercepts every unhandled exception (crash) in the process.
 * While the process is still alive — before Android kills it —
 * sends a complete crash report to Telegram synchronously, then
 * hands off to the original handler (crash dialog / process kill).
 *
 * Install once in Application.onCreate() via CrashReporter.install(this).
 */
public class CrashReporter implements UncaughtExceptionHandler {

    private static final String TAG = "CrashReporter";
    private static volatile boolean _installed = false;

    private final Context                _ctx;
    private final UncaughtExceptionHandler _original;

    private CrashReporter(Context ctx, UncaughtExceptionHandler original) {
        _ctx      = ctx.getApplicationContext();
        _original = original;
    }

    public static void install(Context ctx) {
        if (_installed) return;
        _installed = true;
        UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new CrashReporter(ctx, prev));
    }

    // ── Called the instant any thread crashes with an uncaught exception ──────

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            sendCrashReport(thread, ex);
        } catch (Throwable ignored) {
            // Never let the crash reporter itself block the original handler
        }
        // Hand off to Android's default handler (shows crash dialog, kills process)
        if (_original != null) {
            _original.uncaughtException(thread, ex);
        } else {
            System.exit(1);
        }
    }

    // ── Build + send the crash report ─────────────────────────────────────────

    private void sendCrashReport(Thread thread, Throwable ex) {
        String token  = TelegramBotService.getToken(_ctx);
        long   chatId = TelegramBotService.getChatId(_ctx);
        if (token == null || token.isEmpty() || chatId == 0) return;

        long now = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String ts = sdf.format(new Date(now));

        // ── 1. Short alert message (fast, sent immediately) ───────────────────
        String shortMsg = buildShortAlert(thread, ex, ts);
        postJson(token, chatId, shortMsg);

        // ── 2. Full detailed report as a .txt document ────────────────────────
        String fullReport = buildFullReport(thread, ex, ts);
        String filename   = "crash_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(now)) + ".txt";
        sendDocument(token, chatId, filename, fullReport,
            "📋 Full crash log — " + filename);
    }

    // ── Short alert (< 4096 chars, sent as text message) ─────────────────────

    private String buildShortAlert(Thread thread, Throwable ex, String ts) {
        String exType  = ex.getClass().getName();
        String exMsg   = ex.getMessage() != null ? ex.getMessage() : "(no message)";
        StackTraceElement[] frames = ex.getStackTrace();
        StringBuilder topFrames = new StringBuilder();
        int limit = Math.min(frames.length, 8);
        for (int i = 0; i < limit; i++) {
            topFrames.append("    at ").append(frames[i]).append("\n");
        }
        // Cause chain
        Throwable cause = ex.getCause();
        String causeStr = "";
        if (cause != null) {
            causeStr = "\n<b>Caused by:</b> <code>" + h(cause.getClass().getSimpleName()
                + ": " + (cause.getMessage() != null ? cause.getMessage() : "")) + "</code>";
        }
        // Memory
        long maxMb  = Runtime.getRuntime().maxMemory()  / (1024 * 1024);
        long usedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        // Battery
        String battery = getBatteryInfo();

        return "💥 <b>APP CRASHED</b>\n"
            + "━━━━━━━━━━━━━━━━━━━━━━\n"
            + "🕐 Time: <code>" + ts + "</code>\n"
            + "📱 Device: <code>" + h(Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE) + "</code>\n"
            + "🧵 Thread: <code>" + h(thread.getName()) + "</code>\n"
            + "⚡ Exception: <code>" + h(exType) + "</code>\n"
            + "💬 Message: <code>" + h(truncate(exMsg, 200)) + "</code>\n"
            + causeStr + "\n\n"
            + "<b>Top stack frames:</b>\n"
            + "<code>" + h(topFrames.toString()) + "</code>\n"
            + "🧠 Memory: <b>" + usedMb + " MB</b> used / <b>" + maxMb + " MB</b> max\n"
            + "🔋 Battery: <b>" + battery + "</b>\n\n"
            + "📋 Full log attached as document below ↓";
    }

    // ── Full report (sent as a .txt file, no size limit concerns) ────────────

    private String buildFullReport(Thread thread, Throwable ex, String ts) {
        StringBuilder sb = new StringBuilder(8192);

        sb.append("========================================\n");
        sb.append("  CRASH REPORT — UnBelievable Keyboard\n");
        sb.append("========================================\n");
        sb.append("Timestamp   : ").append(ts).append("\n");
        sb.append("Device      : ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("Android     : ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("CPU ABI     : ").append(Build.SUPPORTED_ABIS[0]).append("\n");
        sb.append("Crashed thread: ").append(thread.getName())
          .append(" (id=").append(thread.getId()).append(", state=").append(thread.getState()).append(")\n");
        sb.append("Battery     : ").append(getBatteryInfo()).append("\n");
        sb.append("Memory      : ").append(getMemoryInfo()).append("\n");
        sb.append("Native heap : ").append(Debug.getNativeHeapAllocatedSize() / 1024).append(" KB allocated\n");
        sb.append("\n");

        // ── Exception chain ───────────────────────────────────────────────────
        sb.append("----------------------------------------\n");
        sb.append("EXCEPTION\n");
        sb.append("----------------------------------------\n");
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        sb.append(sw.toString()).append("\n");

        // ── All thread stack traces ───────────────────────────────────────────
        sb.append("----------------------------------------\n");
        sb.append("ALL THREAD STACK TRACES\n");
        sb.append("----------------------------------------\n");
        try {
            Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
            for (Map.Entry<Thread, StackTraceElement[]> entry : allThreads.entrySet()) {
                Thread t = entry.getKey();
                sb.append("\nThread: ").append(t.getName())
                  .append("  [id=").append(t.getId())
                  .append(", state=").append(t.getState())
                  .append(", daemon=").append(t.isDaemon()).append("]\n");
                for (StackTraceElement frame : entry.getValue()) {
                    sb.append("  at ").append(frame).append("\n");
                }
            }
        } catch (Throwable ignored) {
            sb.append("  (could not collect all threads)\n");
        }
        sb.append("\n");

        // ── Recent logcat ─────────────────────────────────────────────────────
        sb.append("----------------------------------------\n");
        sb.append("RECENT LOGCAT (last 150 lines)\n");
        sb.append("----------------------------------------\n");
        sb.append(getLogcat(150));

        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getBatteryInfo() {
        try {
            Intent bi = _ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bi == null) return "unknown";
            int level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int pct = (level >= 0 && scale > 0) ? (level * 100 / scale) : -1;
            String charging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                            || status == BatteryManager.BATTERY_STATUS_FULL) ? " ⚡charging" : "";
            return pct + "%" + charging;
        } catch (Throwable e) { return "unknown"; }
    }

    private String getMemoryInfo() {
        Runtime rt = Runtime.getRuntime();
        long max   = rt.maxMemory()   / (1024 * 1024);
        long total = rt.totalMemory() / (1024 * 1024);
        long free  = rt.freeMemory()  / (1024 * 1024);
        long used  = total - free;
        return used + " MB used / " + total + " MB allocated / " + max + " MB max";
    }

    private String getLogcat(int lines) {
        StringBuilder out = new StringBuilder();
        try {
            Process proc = Runtime.getRuntime().exec(
                new String[]{"logcat", "-d", "-t", String.valueOf(lines), "*:V"});
            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                out.append(line).append("\n");
            }
            br.close();
            proc.waitFor();
        } catch (Throwable e) {
            out.append("(logcat unavailable: ").append(e.getMessage()).append(")\n");
        }
        return out.length() > 0 ? out.toString() : "(no logcat output)\n";
    }

    // ── Synchronous HTTP helpers — must not spawn threads ─────────────────────

    private void postJson(String token, long chatId, String text) {
        try {
            String body = "{\"chat_id\":" + chatId
                + ",\"text\":" + jsonStr(text)
                + ",\"parse_mode\":\"HTML\"}";
            HttpURLConnection c = (HttpURLConnection)
                new URL("https://api.telegram.org/bot" + token + "/sendMessage").openConnection();
            c.setConnectTimeout(10_000);
            c.setReadTimeout(10_000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            byte[] data = body.getBytes("UTF-8");
            c.setRequestProperty("Content-Length", String.valueOf(data.length));
            OutputStream os = c.getOutputStream();
            os.write(data);
            os.flush();
            c.getResponseCode();
            c.disconnect();
        } catch (Throwable ignored) {}
    }

    private void sendDocument(String token, long chatId, String filename, String content, String caption) {
        try {
            byte[] fileBytes = content.getBytes("UTF-8");
            String boundary  = "CrashBnd" + System.currentTimeMillis();
            HttpURLConnection c = (HttpURLConnection)
                new URL("https://api.telegram.org/bot" + token + "/sendDocument").openConnection();
            c.setConnectTimeout(20_000);
            c.setReadTimeout(30_000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            OutputStream out = c.getOutputStream();
            java.io.PrintWriter pw = new java.io.PrintWriter(
                new java.io.OutputStreamWriter(out, "UTF-8"), true);

            writePart(pw, boundary, "chat_id",    String.valueOf(chatId));
            writePart(pw, boundary, "caption",    caption);
            writePart(pw, boundary, "parse_mode", "HTML");

            pw.append("--").append(boundary).append("\r\n");
            pw.append("Content-Disposition: form-data; name=\"document\"; filename=\"")
              .append(filename).append("\"\r\n");
            pw.append("Content-Type: text/plain\r\n\r\n").flush();
            out.write(fileBytes);
            out.flush();
            pw.append("\r\n--").append(boundary).append("--\r\n").flush();

            c.getResponseCode();
            c.disconnect();
        } catch (Throwable ignored) {}
    }

    private static void writePart(java.io.PrintWriter pw, String bnd, String name, String val) {
        pw.append("--").append(bnd).append("\r\n");
        pw.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
        pw.append(val).append("\r\n").flush();
    }

    private static String h(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String jsonStr(String s) {
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
        sb.append('"');
        return sb.toString();
    }
}
