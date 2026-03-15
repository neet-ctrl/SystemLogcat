package juloo.sysconsole;

import android.content.pm.PackageManager;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import rikka.shizuku.Shizuku;

/**
 * Runs privileged shell commands via Shizuku's newProcess() API.
 * This gives us access to system-level data that normal apps cannot see:
 *   - AppOps per-package access timestamps (camera, mic, location, clipboard...)
 *   - Full running process list (ps -A)
 *   - dumpsys appops — all app operation states
 *   - dumpsys activity processes — foreground/background service info
 *   - cmd package list packages — with disabled/enabled state
 */
public class ShizukuCommandHelper {

    private static final int TIMEOUT_MS = 8000;

    public static boolean isAvailable() {
        try {
            return Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Run a privileged shell command and return its stdout as a single string.
     * Returns empty string on failure.
     */
    public static String run(String... args) {
        if (!isAvailable()) return "";
        try {
            Process process = Shizuku.newProcess(args, null, null);
            StringBuilder sb = new StringBuilder();
            try (InputStream is = process.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                String line;
                long deadline = System.currentTimeMillis() + TIMEOUT_MS;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                    if (System.currentTimeMillis() > deadline) break;
                }
            }
            process.destroy();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Run command and return output as a list of lines.
     */
    public static List<String> runLines(String... args) {
        String raw = run(args);
        List<String> lines = new ArrayList<>();
        if (raw.isEmpty()) return lines;
        for (String l : raw.split("\n")) {
            String trimmed = l.trim();
            if (!trimmed.isEmpty()) lines.add(trimmed);
        }
        return lines;
    }

    // ── Specific privileged queries ──────────────────────────────────────────

    /**
     * Get AppOps state for a specific package.
     * Uses: cmd appops get <pkg>
     * Returns raw dump string.
     */
    public static String getAppOpsForPackage(String pkg) {
        return run("cmd", "appops", "get", pkg);
    }

    /**
     * Dump ALL AppOps for all packages.
     * Uses: dumpsys appops
     */
    public static String dumpAllAppOps() {
        return run("dumpsys", "appops");
    }

    /**
     * Get full process list including UID, PID, name.
     * Uses: ps -A
     */
    public static List<String> getAllProcesses() {
        return runLines("ps", "-A");
    }

    /**
     * Get running app processes from ActivityManager via dumpsys.
     */
    public static String dumpActivityProcesses() {
        return run("dumpsys", "activity", "processes");
    }

    /**
     * Get foreground services currently running.
     */
    public static String dumpRunningServices() {
        return run("dumpsys", "activity", "services");
    }

    /**
     * Get package details including granted permissions.
     * Uses: dumpsys package <pkg>
     */
    public static String dumpPackage(String pkg) {
        return run("dumpsys", "package", pkg);
    }

    /**
     * Get all packages with their install state.
     * Uses: pm list packages -f
     */
    public static List<String> listAllPackages() {
        return runLines("pm", "list", "packages", "-f");
    }

    /**
     * Get battery stats for all apps.
     */
    public static String dumpBatteryStats() {
        return run("dumpsys", "batterystats", "--charged");
    }

    /**
     * Get device admin list.
     * Uses: dumpsys device_policy
     */
    public static String dumpDevicePolicy() {
        return run("dumpsys", "device_policy");
    }

    /**
     * Get accessibility services.
     */
    public static String dumpAccessibility() {
        return run("dumpsys", "accessibility");
    }

    /**
     * Get notification policy — which apps have notification listener access.
     */
    public static String dumpNotificationPolicy() {
        return run("dumpsys", "notification", "--noredact");
    }

    /**
     * Parse AppOps dump to check if a specific op was used recently.
     * Looks for non-zero lastAccess timestamps in the dump output.
     *
     * @param appOpsDump  Output of getAppOpsForPackage() or dumpAllAppOps()
     * @param opName      Op name to search for (e.g. "CAMERA", "RECORD_AUDIO",
     *                    "FINE_LOCATION", "READ_CLIPBOARD", "TOAST_WINDOW")
     * @param windowMs    Time window in milliseconds to check
     * @return true if the op was accessed within the window
     */
    public static boolean wasOpUsedRecently(String appOpsDump, String opName, long windowMs) {
        if (appOpsDump == null || appOpsDump.isEmpty()) return false;
        long threshold = System.currentTimeMillis() - windowMs;
        String searchUpper = opName.toUpperCase();
        String[] lines = appOpsDump.split("\n");
        boolean inOp = false;
        for (String line : lines) {
            String upper = line.toUpperCase();
            if (upper.contains(searchUpper) && upper.contains("OP_")) {
                inOp = true;
            }
            if (inOp && (upper.contains("ACCESS") || upper.contains("TIME="))) {
                // Try to parse a timestamp from the line
                long ts = extractTimestamp(line);
                if (ts > threshold) return true;
                inOp = false;
            }
        }
        return false;
    }

    /**
     * Parse process list to check if a given package has a running process.
     * @param processes  Output of getAllProcesses()
     * @param pkg        Package name to search for
     */
    public static boolean isProcessRunning(List<String> processes, String pkg) {
        for (String line : processes) {
            if (line.contains(pkg)) return true;
        }
        return false;
    }

    /**
     * Parse dumpsys package output to find granted runtime permissions.
     */
    public static List<String> parseGrantedPermissions(String packageDump) {
        List<String> granted = new ArrayList<>();
        if (packageDump == null || packageDump.isEmpty()) return granted;
        for (String line : packageDump.split("\n")) {
            String t = line.trim();
            if (t.startsWith("android.permission.")
                    && (t.contains("granted=true") || t.contains(": granted"))) {
                String perm = t.split(":")[0].trim();
                if (!granted.contains(perm)) granted.add(perm);
            }
        }
        return granted;
    }

    /**
     * Parse dumpsys device_policy to find device admin packages.
     */
    public static List<String> parseDeviceAdminPackages(String policyDump) {
        List<String> admins = new ArrayList<>();
        if (policyDump == null || policyDump.isEmpty()) return admins;
        for (String line : policyDump.split("\n")) {
            String t = line.trim();
            if (t.startsWith("mAdminList") || t.startsWith("admin=")
                    || t.contains("DeviceAdminInfo")) {
                int pkgStart = t.indexOf("ComponentInfo{");
                if (pkgStart >= 0) {
                    String rest = t.substring(pkgStart + 14);
                    String pkg  = rest.split("[/}]")[0].trim();
                    if (!pkg.isEmpty() && !admins.contains(pkg)) admins.add(pkg);
                }
            }
        }
        return admins;
    }

    /**
     * Parse accessibility dump to find enabled accessibility services.
     */
    public static List<String> parseAccessibilityPackages(String accessDump) {
        List<String> pkgs = new ArrayList<>();
        if (accessDump == null || accessDump.isEmpty()) return pkgs;
        for (String line : accessDump.split("\n")) {
            String t = line.trim();
            if (t.startsWith("id=") || t.contains("ServiceInfo:")) {
                int start = t.indexOf("id=");
                if (start >= 0) {
                    String rest = t.substring(start + 3);
                    String[] parts = rest.split("\\s+|/");
                    if (parts.length > 0 && parts[0].contains(".")) {
                        String pkg = parts[0].trim();
                        if (!pkgs.contains(pkg)) pkgs.add(pkg);
                    }
                }
            }
        }
        return pkgs;
    }

    /**
     * Parse notification dump to find notification listener packages.
     */
    public static List<String> parseNotificationListeners(String notifDump) {
        List<String> pkgs = new ArrayList<>();
        if (notifDump == null || notifDump.isEmpty()) return pkgs;
        boolean inListeners = false;
        for (String line : notifDump.split("\n")) {
            String t = line.trim();
            if (t.contains("Notification List") || t.contains("mEnabledListeners")
                    || t.contains("Enabled listeners")) {
                inListeners = true;
            }
            if (inListeners && t.contains("ComponentInfo{")) {
                int pkgStart = t.indexOf("ComponentInfo{");
                if (pkgStart >= 0) {
                    String rest = t.substring(pkgStart + 14);
                    String pkg  = rest.split("[/}]")[0].trim();
                    if (!pkg.isEmpty() && !pkgs.contains(pkg)) pkgs.add(pkg);
                }
            }
            if (inListeners && t.isEmpty()) inListeners = false;
        }
        return pkgs;
    }

    private static long extractTimestamp(String line) {
        try {
            int eqIdx = line.indexOf('=');
            while (eqIdx >= 0) {
                String rest = line.substring(eqIdx + 1).trim();
                String num  = rest.replaceAll("[^0-9].*", "");
                if (num.length() >= 10) {
                    long val = Long.parseLong(num);
                    if (val > 1_000_000_000_000L) return val;
                }
                eqIdx = line.indexOf('=', eqIdx + 1);
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
