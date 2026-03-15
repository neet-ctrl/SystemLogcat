package juloo.sysconsole;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Master security scan engine.
 *
 * Data sources (in order of depth):
 *  1. PackageManager          — installed apps, requested permissions, services
 *  2. AppOpsManager           — recent camera/mic/location usage (own-app access only)
 *  3. UsageStatsManager       — last used timestamps, foreground/background time
 *  4. ActivityManager         — running processes list
 *  5. NetworkUsageHelper      — per-app network bytes (7-day window)
 *  6. ShizukuCommandHelper    — privileged: all AppOps, full process list, granted
 *                               permissions, device admin, accessibility, notif listeners
 *  7. SpyDetectionEngine      — known-threat DB + permission-combo heuristics
 *  8. BankingRiskAnalyzer     — banking app + OTP-interception + overlay-phishing checks
 *  9. DeviceSecurityHelper    — elevated-privilege app enumeration
 */
public class SecurityScanManager {

    private final Context ctx;
    private final PackageManager pm;

    private List<AppSecurityInfo>               mCachedApps     = new ArrayList<>();
    private List<SecurityAlert>                 mCachedAlerts   = new ArrayList<>();
    private List<DeviceSecurityHelper.ElevatedApp> mElevatedApps = new ArrayList<>();
    private long                                mLastScanTime   = 0;

    private static final String CACHE_FILE = "scan_cache.json";

    public SecurityScanManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.pm  = ctx.getPackageManager();
        loadFromCache();
    }

    public interface ScanCallback {
        void onProgress(int current, int total, String phase);
        void onComplete(List<AppSecurityInfo> apps, List<SecurityAlert> alerts);
    }

    public void scanAsync(ScanCallback cb) {
        new Thread(() -> {
            List<AppSecurityInfo> results = doScan(cb);
            List<SecurityAlert>   alerts  = buildAlerts(results);
            mCachedApps   = results;
            mCachedAlerts = alerts;
            mLastScanTime = System.currentTimeMillis();
            saveToCache();
            if (cb != null) cb.onComplete(results, alerts);
        }).start();
    }

    public List<AppSecurityInfo>               getCachedApps()     { return mCachedApps; }
    public List<SecurityAlert>                 getCachedAlerts()   { return mCachedAlerts; }
    public List<DeviceSecurityHelper.ElevatedApp> getElevatedApps(){ return mElevatedApps; }
    public long                                getLastScanTime()   { return mLastScanTime; }

    // ── Main scan pipeline ────────────────────────────────────────────────────

    private List<AppSecurityInfo> doScan(ScanCallback cb) {

        // Phase 1: Package list
        progress(cb, 0, 100, "Loading installed apps…");
        List<PackageInfo> pkgs;
        try {
            pkgs = pm.getInstalledPackages(
                    PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES);
        } catch (Exception e) {
            return new ArrayList<>();
        }

        // Phase 2: Parallel data collection
        progress(cb, 10, 100, "Reading usage stats…");
        Map<String, Long>    lastUsedMap  = getLastUsedMap();
        Map<String, Long>    bgTimeMap    = getBackgroundTimeMap();

        progress(cb, 25, 100, "Checking running processes…");
        Map<String, Boolean> runningMap   = getRunningApps();
        List<String>         processList  = getShizukuProcessList();

        progress(cb, 35, 100, "Reading AppOps data…");
        Map<String, int[]>   appOpsMap    = getAppOpsData();
        Map<String, int[]>   shizukuOpsMap = getShizukuAppOpsMap(pkgs);

        progress(cb, 50, 100, "Checking network usage…");
        Map<String, Long>    netUsageMap  = NetworkUsageHelper.getWeeklyNetworkUsage(ctx);

        // Phase 3: Elevated-privilege apps (async, non-blocking for scan)
        progress(cb, 55, 100, "Detecting elevated-privilege apps…");
        mElevatedApps = DeviceSecurityHelper.getElevatedApps(ctx);

        // Phase 4: Per-app analysis
        progress(cb, 60, 100, "Analyzing apps…");
        List<AppSecurityInfo> list = new ArrayList<>();
        int total = pkgs.size();
        int idx   = 0;

        for (PackageInfo pi : pkgs) {
            idx++;
            if (idx % 15 == 0) {
                progress(cb, 60 + (idx * 35 / total), 100,
                        "Scanning " + idx + "/" + total + " apps…");
            }

            AppSecurityInfo info = new AppSecurityInfo();
            info.packageName = pi.packageName;
            info.versionName = pi.versionName != null ? pi.versionName : "?";
            info.versionCode = pi.versionCode;
            info.installTime = pi.firstInstallTime;
            info.isSystemApp = (pi.applicationInfo != null)
                    && ((pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);

            try {
                info.appName = (String) pm.getApplicationLabel(pi.applicationInfo);
                info.icon    = pm.getApplicationIcon(pi.packageName);
            } catch (Exception e) {
                info.appName = pi.packageName;
            }

            info.lastUsed         = orDefault(lastUsedMap, pi.packageName, 0L);
            info.backgroundTimeMs = orDefault(bgTimeMap,   pi.packageName, 0L);
            info.networkBytesTotal= orDefault(netUsageMap, pi.packageName, 0L);

            // Process running state: use both ActivityManager and Shizuku process list
            info.isRunning = runningMap.containsKey(pi.packageName)
                    ? runningMap.get(pi.packageName)
                    : ShizukuCommandHelper.isProcessRunning(processList, pi.packageName);

            // Permission analysis
            analyzePermissions(info, pi);

            // AppOps — prefer Shizuku (full) over normal (partial)
            int[] ops = shizukuOpsMap.containsKey(pi.packageName)
                    ? shizukuOpsMap.get(pi.packageName)
                    : appOpsMap.get(pi.packageName);
            if (ops != null) applyAppOps(info, ops);

            // Granted runtime permissions via Shizuku
            if (ShizukuCommandHelper.isAvailable()) {
                applyGrantedPermissions(info);
            }

            // Check if this app is in elevated apps list
            for (DeviceSecurityHelper.ElevatedApp ea : mElevatedApps) {
                if (ea.packageName.equals(info.packageName)) {
                    if (ea.privilege.contains("Device Admin"))  info.isDeviceAdmin         = true;
                    if (ea.privilege.contains("Accessibility")) info.isAccessibilityService = true;
                    if (ea.privilege.contains("Notification"))  info.isNotificationListener = true;
                    if (ea.privilege.contains("VPN"))           info.isVpnService           = true;
                }
            }

            // Spy/stalkerware detection
            SpyDetectionEngine.ThreatResult threat = SpyDetectionEngine.analyze(info);
            info.threatLevel  = threat.level;
            info.threatLabel  = threat.label;
            info.threatReason = threat.reason;
            info.isSuspectedSpyware = threat.level >= SpyDetectionEngine.THREAT_SUSPECT;

            // Banking analysis
            info.isBankingApp = BankingRiskAnalyzer.isBankingApp(info);

            // Risk calculation (must be last — uses all fields)
            calculateRisk(info, list);

            list.add(info);
        }

        // Second pass: banking cross-app risks (needs full app list)
        progress(cb, 96, 100, "Analyzing banking risks…");
        for (AppSecurityInfo info : list) {
            BankingRiskAnalyzer.BankingRisk br =
                    BankingRiskAnalyzer.analyze(info, false, false, false);
            if (br.hasCriticalRisk) {
                info.isBankingRisk = true;
                info.riskFactors.addAll(br.riskWarnings);
                info.riskScore = Math.min(info.riskScore + br.riskBonus, 100);
                if (info.riskScore >= 50) info.riskLevel = AppSecurityInfo.RISK_HIGH;
            }

            if (BankingRiskAnalyzer.isOtpInterceptionRisk(info, list)
                    && !info.riskFactors.contains("Can intercept OTP codes from bank SMS")) {
                info.riskFactors.add(
                        "Can intercept OTP codes from bank SMS and upload them remotely");
                info.isBankingRisk = true;
                info.riskScore = Math.min(info.riskScore + 25, 100);
                if (info.riskScore >= 50) info.riskLevel = AppSecurityInfo.RISK_HIGH;
            }

            if (BankingRiskAnalyzer.isOverlayPhishingRisk(info, list)
                    && !info.riskFactors.contains("Can overlay banking apps")) {
                info.riskFactors.add(
                        "Can show fake login screens over banking apps (overlay phishing)");
                info.isBankingRisk = true;
                info.riskScore = Math.min(info.riskScore + 20, 100);
                if (info.riskScore >= 50) info.riskLevel = AppSecurityInfo.RISK_HIGH;
            }
        }

        Collections.sort(list, (a, b) -> {
            if (a.threatLevel != b.threatLevel) return b.threatLevel - a.threatLevel;
            return b.riskScore - a.riskScore;
        });

        progress(cb, 100, 100, "Scan complete");
        return list;
    }

    // ── Permission analysis ────────────────────────────────────────────────────

    private void analyzePermissions(AppSecurityInfo info, PackageInfo pi) {
        if (pi.requestedPermissions == null) return;
        for (String perm : pi.requestedPermissions) {
            if (perm == null) continue;
            switch (perm) {
                case android.Manifest.permission.CAMERA:
                    info.hasCamera = true; break;
                case android.Manifest.permission.RECORD_AUDIO:
                    info.hasMicrophone = true; break;
                case android.Manifest.permission.ACCESS_FINE_LOCATION:
                case android.Manifest.permission.ACCESS_COARSE_LOCATION:
                    info.hasLocation = true; break;
                case android.Manifest.permission.READ_CONTACTS:
                    info.hasContacts = true; break;
                case android.Manifest.permission.READ_EXTERNAL_STORAGE:
                case android.Manifest.permission.WRITE_EXTERNAL_STORAGE:
                    info.hasStorage = true; break;
                case android.Manifest.permission.READ_SMS:
                case android.Manifest.permission.SEND_SMS:
                case android.Manifest.permission.RECEIVE_SMS:
                    info.hasSms = true; break;
                case android.Manifest.permission.CALL_PHONE:
                case android.Manifest.permission.READ_CALL_LOG:
                    info.hasPhone = true; break;
                case android.Manifest.permission.READ_CALENDAR:
                    info.hasCalendar = true; break;
                case android.Manifest.permission.BODY_SENSORS:
                    info.hasBodySensors = true; break;
                case android.Manifest.permission.INTERNET:
                    info.hasInternet = true; break;
                case android.Manifest.permission.SYSTEM_ALERT_WINDOW:
                    info.hasOverlay = true; break;
                case "android.permission.BIND_VPN_SERVICE":
                    info.hasVpn = true; break;
                case "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE":
                    info.hasNotificationAccess = true; break;
                case "android.permission.READ_MEDIA_IMAGES":
                    info.hasReadMediaImages = true; break;
                case android.Manifest.permission.PROCESS_OUTGOING_CALLS:
                    info.hasProcessOutgoingCalls = true; break;
                case android.Manifest.permission.RECEIVE_BOOT_COMPLETED:
                    info.hasBootReceiver = true; break;
            }
        }

        if (pi.services != null) {
            for (ServiceInfo si : pi.services) {
                if (si.permission != null && si.permission.contains("ACCESSIBILITY")) {
                    info.hasAccessibility = true;
                }
                if (si.permission != null && si.permission.contains("VPN")) {
                    info.hasVpn = true;
                }
                if (si.permission != null && si.permission.contains("NOTIFICATION_LISTENER")) {
                    info.hasNotificationAccess = true;
                }
            }
        }
    }

    private void applyAppOps(AppSecurityInfo info, int[] ops) {
        if (ops.length > 0 && ops[0] == 1) info.cameraUsedRecently   = true;
        if (ops.length > 1 && ops[1] == 1) info.micUsedRecently      = true;
        if (ops.length > 2 && ops[2] == 1) info.locationUsedRecently = true;
        if (ops.length > 3 && ops[3] == 1) info.clipboardUsedRecently = true;
    }

    private void applyGrantedPermissions(AppSecurityInfo info) {
        try {
            String dump = ShizukuCommandHelper.dumpPackage(info.packageName);
            if (dump.isEmpty()) return;
            info.grantedPermissions =
                    ShizukuCommandHelper.parseGrantedPermissions(dump);
        } catch (Exception ignored) {}
    }

    // ── Risk calculation ───────────────────────────────────────────────────────

    private void calculateRisk(AppSecurityInfo info, List<AppSecurityInfo> existingList) {
        int score = 0;
        List<String> factors = info.riskFactors;

        // Sensor combos
        if (info.hasCamera && info.hasInternet) {
            score += 25;
            factors.add("Camera + Internet: can upload photos remotely");
        } else if (info.hasCamera) {
            score += 10;
            factors.add("Camera access");
        }
        if (info.hasMicrophone && info.hasInternet) {
            score += 25;
            factors.add("Microphone + Internet: can record and upload audio");
        } else if (info.hasMicrophone) {
            score += 10;
            factors.add("Microphone access");
        }
        if (info.hasLocation && info.hasInternet) {
            score += 20;
            factors.add("Location + Internet: can track physical position remotely");
        } else if (info.hasLocation) {
            score += 8;
            factors.add("Location access");
        }

        // Privilege escalation
        if (info.hasAccessibility || info.isAccessibilityService) {
            score += 30;
            factors.add("Accessibility service: can read screen content and simulate input");
        }
        if (info.isDeviceAdmin) {
            score += 40;
            factors.add("Device Administrator: can wipe, lock, or control this device remotely");
        }
        if (info.isVpnService || info.hasVpn) {
            score += 25;
            factors.add("VPN Service: can intercept and inspect all network traffic");
        }
        if (info.isNotificationListener || info.hasNotificationAccess) {
            score += 20;
            factors.add("Notification listener: reads all notifications from every app");
        }

        // Overlay + phishing
        if (info.hasOverlay && !info.isSystemApp) {
            score += 15;
            factors.add("Draw-over-apps: can show fake UI on top of other apps");
        }

        // SMS / call exfiltration
        if (info.hasSms && !info.isSystemApp) {
            score += 20;
            factors.add("SMS access: can read and send text messages");
        }
        if (info.hasPhone) {
            score += 12;
            factors.add("Phone access: can make calls and read call log");
        }
        if (info.hasContacts) {
            score += 8;
            factors.add("Contacts access: can read your address book");
        }
        if (info.hasProcessOutgoingCalls && !info.isSystemApp) {
            score += 10;
            factors.add("Can intercept and redirect outgoing phone calls");
        }

        // Boot persistence
        if (info.hasBootReceiver && !info.isSystemApp
                && (info.hasCamera || info.hasMicrophone || info.hasSms)) {
            score += 8;
            factors.add("Starts on device boot — persistent background access");
        }

        // AppOps evidence (recent actual usage)
        if (info.cameraUsedRecently && !info.isSystemApp) {
            score += 12;
            factors.add("Camera actually used in last 7 days");
        }
        if (info.micUsedRecently && !info.isSystemApp) {
            score += 12;
            factors.add("Microphone actually used in last 7 days");
        }
        if (info.locationUsedRecently && !info.isSystemApp) {
            score += 6;
            factors.add("Location actually accessed in last 7 days");
        }
        if (info.clipboardUsedRecently && info.hasInternet && !info.isSystemApp) {
            score += 15;
            factors.add("Clipboard read + Internet access: can steal copied passwords/tokens");
        }

        // Threat detection bonus
        score += SpyDetectionEngine.getThreatScoreBonus(info);
        if (info.isSuspectedSpyware && !factors.contains("Flagged as suspected spyware")) {
            factors.add("⚠ Flagged as suspected " + SpyDetectionEngine.threatLevelName(info.threatLevel)
                    + ": " + info.threatReason);
        }

        // Network usage spike (> 100 MB in 7 days for non-media apps)
        if (info.networkBytesTotal > 100L * 1024 * 1024
                && !info.isSystemApp && !isKnownHighNetworkApp(info.packageName)) {
            score += 5;
            factors.add("High network usage: " + info.networkUsageFormatted() + " in 7 days");
        }

        info.riskScore = Math.min(score, 100);
        if (info.riskScore >= 50)      info.riskLevel = AppSecurityInfo.RISK_HIGH;
        else if (info.riskScore >= 20) info.riskLevel = AppSecurityInfo.RISK_MEDIUM;
        else                           info.riskLevel = AppSecurityInfo.RISK_LOW;
    }

    // ── Data collection helpers ───────────────────────────────────────────────

    private Map<String, Long> getLastUsedMap() {
        Map<String, Long> map = new HashMap<>();
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return map;
            long now   = System.currentTimeMillis();
            long start = now - 7L * 24 * 3600 * 1000;
            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, start, now);
            if (stats == null) return map;
            for (UsageStats us : stats) {
                String pkg  = us.getPackageName();
                long   last = us.getLastTimeUsed();
                if (!map.containsKey(pkg) || map.get(pkg) < last) map.put(pkg, last);
            }
        } catch (Exception ignored) {}
        return map;
    }

    private Map<String, Long> getBackgroundTimeMap() {
        Map<String, Long> map = new HashMap<>();
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return map;
            long now   = System.currentTimeMillis();
            long start = now - 24L * 3600 * 1000;
            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, start, now);
            if (stats == null) return map;
            for (UsageStats us : stats) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    long bg = us.getTotalTimeInForeground();
                    if (bg > 0) map.put(us.getPackageName(), bg);
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    private Map<String, Boolean> getRunningApps() {
        Map<String, Boolean> map = new HashMap<>();
        try {
            ActivityManager am = (ActivityManager)
                    ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return map;
            List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
            if (procs == null) return map;
            for (ActivityManager.RunningAppProcessInfo info : procs) {
                if (info.pkgList == null) continue;
                for (String pkg : info.pkgList) map.put(pkg, true);
            }
        } catch (Exception ignored) {}
        return map;
    }

    private List<String> getShizukuProcessList() {
        if (!ShizukuCommandHelper.isAvailable()) return new ArrayList<>();
        return ShizukuCommandHelper.getAllProcesses();
    }

    /**
     * Standard AppOps query using reflection to access the hidden API
     * (getPackagesForOps, PackageOps, OpEntry are all @hide in the SDK).
     *
     * OP integer constants (stable across API 21-35):
     *   OP_FINE_LOCATION  = 1
     *   OP_CAMERA         = 26
     *   OP_RECORD_AUDIO   = 27
     *   OP_READ_CLIPBOARD = 29
     */
    private Map<String, int[]> getAppOpsData() {
        Map<String, int[]> map = new HashMap<>();
        try {
            AppOpsManager aom = (AppOpsManager)
                    ctx.getSystemService(Context.APP_OPS_SERVICE);
            if (aom == null) return map;

            long weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;

            // Hidden ops — use raw int values (stable in AOSP since API 19)
            // Index: 0=camera, 1=mic, 2=location, 3=clipboard
            int[] opsToCheck = { 26, 27, 1, 29 };

            // Reflect: AppOpsManager.getPackagesForOps(int[])
            Method getPackagesForOps = AppOpsManager.class.getDeclaredMethod(
                    "getPackagesForOps", int[].class);
            getPackagesForOps.setAccessible(true);

            for (int opIdx = 0; opIdx < opsToCheck.length; opIdx++) {
                try {
                    Object rawList = getPackagesForOps.invoke(
                            aom, new int[]{ opsToCheck[opIdx] });
                    if (!(rawList instanceof List)) continue;

                    for (Object packageOps : (List<?>) rawList) {
                        // PackageOps.getPackageName()
                        String pkg = (String) packageOps.getClass()
                                .getMethod("getPackageName").invoke(packageOps);

                        // PackageOps.getOps() → List<OpEntry>
                        Object opsList = packageOps.getClass()
                                .getMethod("getOps").invoke(packageOps);
                        if (!(opsList instanceof List)) continue;

                        for (Object opEntry : (List<?>) opsList) {
                            long lastAccess = getOpLastAccess(opEntry);
                            if (lastAccess > weekAgo) {
                                int[] results = map.containsKey(pkg)
                                        ? map.get(pkg) : new int[4];
                                if (opIdx < results.length) results[opIdx] = 1;
                                map.put(pkg, results);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return map;
    }

    /**
     * Extract last-access timestamp from a reflected OpEntry object.
     * Tries the API-29+ getLastAccessTime(int flags) first, then
     * falls back to the older no-arg getTime().
     */
    private static long getOpLastAccess(Object opEntry) {
        try {
            // API 29+: getLastAccessTime(int flags)  — flags 0xF = all sources
            Method getLastAccessTime = opEntry.getClass()
                    .getMethod("getLastAccessTime", int.class);
            return (long) getLastAccessTime.invoke(opEntry, 0xF);
        } catch (Exception e1) {
            try {
                // Pre-API-29: getTime()
                return (long) opEntry.getClass().getMethod("getTime").invoke(opEntry);
            } catch (Exception e2) {
                return 0L;
            }
        }
    }

    /**
     * Shizuku-powered AppOps — reads ALL packages' ops including camera, mic,
     * location, AND clipboard.
     */
    private Map<String, int[]> getShizukuAppOpsMap(List<PackageInfo> pkgs) {
        Map<String, int[]> map = new HashMap<>();
        if (!ShizukuCommandHelper.isAvailable()) return map;

        try {
            String fullDump = ShizukuCommandHelper.dumpAllAppOps();
            if (fullDump.isEmpty()) return map;
            long weekMs = 7L * 24 * 3600 * 1000;

            for (PackageInfo pi : pkgs) {
                int[] results = new int[4]; // camera, mic, location, clipboard
                String pkgSection = extractPackageSection(fullDump, pi.packageName);
                if (pkgSection.isEmpty()) continue;

                results[0] = ShizukuCommandHelper.wasOpUsedRecently(pkgSection, "CAMERA",    weekMs) ? 1 : 0;
                results[1] = ShizukuCommandHelper.wasOpUsedRecently(pkgSection, "RECORD_AUDIO", weekMs) ? 1 : 0;
                results[2] = ShizukuCommandHelper.wasOpUsedRecently(pkgSection, "FINE_LOCATION", weekMs) ? 1 : 0;
                results[3] = ShizukuCommandHelper.wasOpUsedRecently(pkgSection, "READ_CLIPBOARD", weekMs) ? 1 : 0;

                map.put(pi.packageName, results);
            }
        } catch (Exception ignored) {}
        return map;
    }

    private String extractPackageSection(String fullDump, String pkg) {
        int start = fullDump.indexOf("Package " + pkg);
        if (start < 0) start = fullDump.indexOf(pkg + ":");
        if (start < 0) return "";
        int end = fullDump.indexOf("\nPackage ", start + 1);
        if (end < 0) end = Math.min(start + 4000, fullDump.length());
        return fullDump.substring(start, end);
    }

    // ── Alerts ────────────────────────────────────────────────────────────────

    private List<SecurityAlert> buildAlerts(List<AppSecurityInfo> apps) {
        List<SecurityAlert> alerts = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (AppSecurityInfo app : apps) {
            // Spyware/stalkerware — highest priority
            if (app.threatLevel >= SpyDetectionEngine.THREAT_STALKER) {
                alerts.add(0, new SecurityAlert(
                        SecurityAlert.TYPE_HIGH_RISK, SecurityAlert.SEV_CRITICAL,
                        "☠ STALKERWARE: " + app.appName,
                        app.threatReason + " — remove immediately",
                        app.packageName, app.appName, now));
            } else if (app.threatLevel >= SpyDetectionEngine.THREAT_SUSPECT) {
                alerts.add(new SecurityAlert(
                        SecurityAlert.TYPE_HIGH_RISK, SecurityAlert.SEV_CRITICAL,
                        "⚠ Suspicious app: " + app.appName,
                        app.threatReason,
                        app.packageName, app.appName, now));
            }

            if (app.isSystemApp) continue;

            if (app.cameraUsedRecently) {
                alerts.add(new SecurityAlert(SecurityAlert.TYPE_CAMERA,
                        SecurityAlert.SEV_WARNING,
                        "Camera used: " + app.appName,
                        app.appName + " accessed the camera in the last 7 days",
                        app.packageName, app.appName, now));
            }
            if (app.micUsedRecently) {
                alerts.add(new SecurityAlert(SecurityAlert.TYPE_MIC,
                        SecurityAlert.SEV_WARNING,
                        "Mic used: " + app.appName,
                        app.appName + " accessed the microphone in the last 7 days",
                        app.packageName, app.appName, now));
            }
            if (app.clipboardUsedRecently && app.hasInternet) {
                alerts.add(new SecurityAlert(SecurityAlert.TYPE_HIGH_RISK,
                        SecurityAlert.SEV_CRITICAL,
                        "Clipboard theft risk: " + app.appName,
                        app.appName + " read your clipboard and has Internet access",
                        app.packageName, app.appName, now));
            }
            if (app.isDeviceAdmin) {
                alerts.add(new SecurityAlert(SecurityAlert.TYPE_HIGH_RISK,
                        SecurityAlert.SEV_CRITICAL,
                        "Device Admin: " + app.appName,
                        app.appName + " has Device Administrator privilege — can wipe/lock device",
                        app.packageName, app.appName, now));
            }
            if (app.isBankingRisk && app.riskLevel == AppSecurityInfo.RISK_HIGH) {
                alerts.add(new SecurityAlert(SecurityAlert.TYPE_HIGH_RISK,
                        SecurityAlert.SEV_CRITICAL,
                        "Banking threat: " + app.appName,
                        app.riskFactors.isEmpty() ? "Poses a risk to banking app security"
                                : app.riskFactors.get(0),
                        app.packageName, app.appName, now));
            }
            if (app.riskLevel == AppSecurityInfo.RISK_HIGH && !app.isSuspectedSpyware) {
                alerts.add(new SecurityAlert(SecurityAlert.TYPE_HIGH_RISK,
                        SecurityAlert.SEV_WARNING,
                        "High-risk app: " + app.appName,
                        "Risk score " + app.riskScore + "/100 — " +
                                (app.riskFactors.isEmpty() ? "multiple dangerous permissions"
                                        : app.riskFactors.get(0)),
                        app.packageName, app.appName, now));
            }
        }

        // Elevated privilege alerts
        for (DeviceSecurityHelper.ElevatedApp ea : mElevatedApps) {
            boolean alreadyAdded = false;
            for (SecurityAlert a : alerts) {
                if (a.packageName.equals(ea.packageName)) { alreadyAdded = true; break; }
            }
            if (!alreadyAdded && !ea.packageName.startsWith("com.android")
                    && !ea.packageName.startsWith("android")) {
                alerts.add(new SecurityAlert(SecurityAlert.TYPE_HIGH_RISK,
                        SecurityAlert.SEV_WARNING,
                        ea.privilege + ": " + ea.appName,
                        ea.description,
                        ea.packageName, ea.appName, now));
            }
        }

        return alerts;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    public static int countWithPerm(List<AppSecurityInfo> apps, String perm) {
        int c = 0;
        for (AppSecurityInfo a : apps) {
            switch (perm) {
                case "camera":   if (a.hasCamera)     c++; break;
                case "mic":      if (a.hasMicrophone) c++; break;
                case "location": if (a.hasLocation)   c++; break;
                case "running":  if (a.isRunning)     c++; break;
                case "high":     if (a.riskLevel == AppSecurityInfo.RISK_HIGH)   c++; break;
                case "medium":   if (a.riskLevel == AppSecurityInfo.RISK_MEDIUM) c++; break;
                case "spyware":  if (a.isSuspectedSpyware) c++; break;
                case "admin":    if (a.isDeviceAdmin) c++; break;
            }
        }
        return c;
    }

    private static long orDefault(Map<String, Long> map, String key, long def) {
        Long v = map.get(key);
        return v != null ? v : def;
    }

    private static boolean isKnownHighNetworkApp(String pkg) {
        return pkg.startsWith("com.google") || pkg.startsWith("com.netflix")
                || pkg.startsWith("com.spotify") || pkg.startsWith("com.youtube")
                || pkg.startsWith("com.amazon") || pkg.startsWith("com.facebook");
    }

    private static void progress(ScanCallback cb, int current, int total, String phase) {
        if (cb != null) cb.onProgress(current, total, phase);
    }

    // ── Persistence (JSON file in filesDir) ───────────────────────────────────

    private void saveToCache() {
        try {
            JSONObject root = new JSONObject();
            root.put("ts", mLastScanTime);

            JSONArray jApps = new JSONArray();
            for (AppSecurityInfo a : mCachedApps) jApps.put(appToJson(a));
            root.put("apps", jApps);

            JSONArray jAlerts = new JSONArray();
            for (SecurityAlert al : mCachedAlerts) jAlerts.put(alertToJson(al));
            root.put("alerts", jAlerts);

            File f = new File(ctx.getFilesDir(), CACHE_FILE);
            FileWriter fw = new FileWriter(f);
            fw.write(root.toString());
            fw.close();
        } catch (Exception ignored) {}
    }

    private void loadFromCache() {
        try {
            File f = new File(ctx.getFilesDir(), CACHE_FILE);
            if (!f.exists()) return;

            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONObject root = new JSONObject(sb.toString());
            mLastScanTime = root.optLong("ts", 0);

            JSONArray jApps = root.optJSONArray("apps");
            if (jApps != null) {
                List<AppSecurityInfo> apps = new ArrayList<>();
                for (int i = 0; i < jApps.length(); i++) {
                    try { apps.add(jsonToApp(jApps.getJSONObject(i))); }
                    catch (Exception ignored) {}
                }
                mCachedApps = apps;
            }

            JSONArray jAlerts = root.optJSONArray("alerts");
            if (jAlerts != null) {
                List<SecurityAlert> alerts = new ArrayList<>();
                for (int i = 0; i < jAlerts.length(); i++) {
                    try { alerts.add(jsonToAlert(jAlerts.getJSONObject(i))); }
                    catch (Exception ignored) {}
                }
                mCachedAlerts = alerts;
            }
        } catch (Exception ignored) {}
    }

    private JSONObject appToJson(AppSecurityInfo a) throws Exception {
        JSONObject o = new JSONObject();
        o.put("pkg",      a.packageName);
        o.put("name",     a.appName     != null ? a.appName     : "");
        o.put("ver",      a.versionName != null ? a.versionName : "");
        o.put("verCode",  a.versionCode);
        o.put("sys",      a.isSystemApp);
        o.put("install",  a.installTime);
        o.put("lastUsed", a.lastUsed);
        o.put("bgRuns",   a.backgroundRunCount);
        o.put("bgTime",   a.backgroundTimeMs);
        o.put("netBytes", a.networkBytesTotal);
        o.put("running",  a.isRunning);
        o.put("hasCamera",    a.hasCamera);
        o.put("hasMic",       a.hasMicrophone);
        o.put("hasLoc",       a.hasLocation);
        o.put("hasContacts",  a.hasContacts);
        o.put("hasStorage",   a.hasStorage);
        o.put("hasSms",       a.hasSms);
        o.put("hasPhone",     a.hasPhone);
        o.put("hasCal",       a.hasCalendar);
        o.put("hasBodSens",   a.hasBodySensors);
        o.put("hasAccess",    a.hasAccessibility);
        o.put("hasNet",       a.hasInternet);
        o.put("hasOverlay",   a.hasOverlay);
        o.put("hasVpn",       a.hasVpn);
        o.put("hasNotif",     a.hasNotificationAccess);
        o.put("hasMedia",     a.hasReadMediaImages);
        o.put("hasOutCall",   a.hasProcessOutgoingCalls);
        o.put("hasBoot",      a.hasBootReceiver);
        o.put("camRecent",    a.cameraUsedRecently);
        o.put("micRecent",    a.micUsedRecently);
        o.put("locRecent",    a.locationUsedRecently);
        o.put("isAdmin",      a.isDeviceAdmin);
        o.put("isAccSvc",     a.isAccessibilityService);
        o.put("isNotifLsn",   a.isNotificationListener);
        o.put("isVpnSvc",     a.isVpnService);
        o.put("threat",       a.threatLevel);
        o.put("threatLabel",  a.threatLabel  != null ? a.threatLabel  : "");
        o.put("threatReason", a.threatReason != null ? a.threatReason : "");
        o.put("isSpy",        a.isSuspectedSpyware);
        o.put("isBank",       a.isBankingApp);
        o.put("isBankRisk",   a.isBankingRisk);
        o.put("riskScore",    a.riskScore);
        o.put("riskLevel",    a.riskLevel);
        JSONArray factors = new JSONArray();
        for (String s : a.riskFactors) factors.put(s);
        o.put("factors", factors);
        JSONArray granted = new JSONArray();
        for (String s : a.grantedPermissions) granted.put(s);
        o.put("granted", granted);
        return o;
    }

    private AppSecurityInfo jsonToApp(JSONObject o) throws Exception {
        AppSecurityInfo a = new AppSecurityInfo();
        a.packageName    = o.optString("pkg");
        a.appName        = o.optString("name");
        a.versionName    = o.optString("ver");
        a.versionCode    = o.optInt("verCode");
        a.isSystemApp    = o.optBoolean("sys");
        a.installTime    = o.optLong("install");
        a.lastUsed       = o.optLong("lastUsed");
        a.backgroundRunCount = o.optInt("bgRuns");
        a.backgroundTimeMs   = o.optLong("bgTime");
        a.networkBytesTotal  = o.optLong("netBytes");
        a.isRunning           = o.optBoolean("running");
        a.hasCamera           = o.optBoolean("hasCamera");
        a.hasMicrophone       = o.optBoolean("hasMic");
        a.hasLocation         = o.optBoolean("hasLoc");
        a.hasContacts         = o.optBoolean("hasContacts");
        a.hasStorage          = o.optBoolean("hasStorage");
        a.hasSms              = o.optBoolean("hasSms");
        a.hasPhone            = o.optBoolean("hasPhone");
        a.hasCalendar         = o.optBoolean("hasCal");
        a.hasBodySensors      = o.optBoolean("hasBodSens");
        a.hasAccessibility    = o.optBoolean("hasAccess");
        a.hasInternet         = o.optBoolean("hasNet");
        a.hasOverlay          = o.optBoolean("hasOverlay");
        a.hasVpn              = o.optBoolean("hasVpn");
        a.hasNotificationAccess   = o.optBoolean("hasNotif");
        a.hasReadMediaImages      = o.optBoolean("hasMedia");
        a.hasProcessOutgoingCalls = o.optBoolean("hasOutCall");
        a.hasBootReceiver         = o.optBoolean("hasBoot");
        a.cameraUsedRecently      = o.optBoolean("camRecent");
        a.micUsedRecently         = o.optBoolean("micRecent");
        a.locationUsedRecently    = o.optBoolean("locRecent");
        a.isDeviceAdmin           = o.optBoolean("isAdmin");
        a.isAccessibilityService  = o.optBoolean("isAccSvc");
        a.isNotificationListener  = o.optBoolean("isNotifLsn");
        a.isVpnService            = o.optBoolean("isVpnSvc");
        a.threatLevel    = o.optInt("threat");
        a.threatLabel    = o.optString("threatLabel");
        a.threatReason   = o.optString("threatReason");
        a.isSuspectedSpyware = o.optBoolean("isSpy");
        a.isBankingApp       = o.optBoolean("isBank");
        a.isBankingRisk      = o.optBoolean("isBankRisk");
        a.riskScore  = o.optInt("riskScore");
        a.riskLevel  = o.optInt("riskLevel");
        JSONArray factors = o.optJSONArray("factors");
        if (factors != null)
            for (int i = 0; i < factors.length(); i++) a.riskFactors.add(factors.getString(i));
        JSONArray granted = o.optJSONArray("granted");
        if (granted != null)
            for (int i = 0; i < granted.length(); i++) a.grantedPermissions.add(granted.getString(i));
        try { a.icon = pm.getApplicationIcon(a.packageName); } catch (Exception ignored) {}
        return a;
    }

    private JSONObject alertToJson(SecurityAlert al) throws Exception {
        JSONObject o = new JSONObject();
        o.put("type",  al.type);
        o.put("sev",   al.severity);
        o.put("title", al.title      != null ? al.title       : "");
        o.put("desc",  al.description != null ? al.description : "");
        o.put("pkg",   al.packageName != null ? al.packageName : "");
        o.put("app",   al.appName    != null ? al.appName     : "");
        o.put("ts",    al.timestamp);
        o.put("dis",   al.dismissed);
        return o;
    }

    private SecurityAlert jsonToAlert(JSONObject o) throws Exception {
        SecurityAlert al = new SecurityAlert(
                o.optInt("type"), o.optInt("sev"),
                o.optString("title"), o.optString("desc"),
                o.optString("pkg"),   o.optString("app"),
                o.optLong("ts"));
        al.dismissed = o.optBoolean("dis");
        return al;
    }
}
