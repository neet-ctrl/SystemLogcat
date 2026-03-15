package juloo.sysconsole;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Detects known spyware, stalkerware, and suspect apps by:
 *  1. Known package name database
 *  2. Suspicious permission combination rules
 *  3. Hidden/disguised app heuristics
 *  4. Remote-access tool detection
 */
public class SpyDetectionEngine {

    public static final int THREAT_NONE      = 0;
    public static final int THREAT_SUSPECT   = 1;
    public static final int THREAT_SPYWARE   = 2;
    public static final int THREAT_STALKER   = 3;

    public static class ThreatResult {
        public int    level;
        public String label;
        public String reason;
        public ThreatResult(int level, String label, String reason) {
            this.level  = level;
            this.label  = label;
            this.reason = reason;
        }
    }

    // Known stalkerware / commercial spyware package names
    private static final Map<String, ThreatResult> KNOWN_THREATS = new HashMap<>();

    // Suspicious permission combinations (spyware patterns)
    // Format: description of the pattern, checked in analyze()

    static {
        // Commercial stalkerware
        putThreat("com.flexispy.android",      THREAT_STALKER, "FlexiSPY",      "Known commercial spyware");
        putThreat("com.thetruthspy",            THREAT_STALKER, "Truth Spy",     "Known commercial stalkerware");
        putThreat("com.spy.phone",              THREAT_STALKER, "Spy Phone",     "Known stalkerware");
        putThreat("com.hoverwatch",             THREAT_STALKER, "Hoverwatch",    "Known stalkerware");
        putThreat("com.highster.phone",         THREAT_STALKER, "Highster",      "Commercial spyware");
        putThreat("com.spyera.android",         THREAT_STALKER, "Spyera",        "Known commercial spyware");
        putThreat("com.mspy.android",           THREAT_STALKER, "mSpy",          "Known commercial stalkerware");
        putThreat("com.ikeymonitor",            THREAT_STALKER, "iKeyMonitor",   "Keylogger & spy app");
        putThreat("com.spyzie",                 THREAT_STALKER, "Spyzie",        "Known stalkerware");
        putThreat("com.clevguard.xnspy",        THREAT_STALKER, "XNSPY",         "Known commercial spyware");
        putThreat("com.cocospy.android",        THREAT_STALKER, "Cocospy",       "Known stalkerware");
        putThreat("com.fonelab.android",        THREAT_STALKER, "FoneLab",       "Device monitor/spy app");
        putThreat("com.cell.track.android",     THREAT_STALKER, "CellTracker",   "Cell tracking spyware");
        putThreat("com.trackview",              THREAT_STALKER, "TrackView",     "Hidden surveillance app");
        putThreat("com.retina-x.mobistealth",   THREAT_STALKER, "MobiStealth",   "Known commercial spyware");
        putThreat("com.android.chrome.monitor", THREAT_STALKER, "Fake Chrome",   "Disguised as Chrome");
        putThreat("com.spy.app",                THREAT_STALKER, "Spy App",       "Generic spyware");
        putThreat("net.cybersoft.phone",        THREAT_STALKER, "Phone Monitor", "Known spy tool");
        putThreat("com.ino.android",            THREAT_STALKER, "iN0S Spy",      "Known spyware");
        putThreat("com.android.sysmanager",     THREAT_SUSPECT, "Fake System Mgr","Disguised as system app");

        // Remote access / RAT tools
        putThreat("org.teamviewer.teamviewer",  THREAT_SUSPECT, "TeamViewer",    "Remote access tool");
        putThreat("com.anydesk.anydeskandroid", THREAT_SUSPECT, "AnyDesk",       "Remote access tool");
        putThreat("com.logmein.rescuemobile",   THREAT_SUSPECT, "LogMeIn Rescue","Remote access tool");
        putThreat("com.airdroid.android",       THREAT_SUSPECT, "AirDroid",      "Full remote device access");
        putThreat("com.adobe.connectmobile",    THREAT_SUSPECT, "Adobe Connect", "Screen sharing/remote tool");
        putThreat("com.rsupport.mobizen.global",THREAT_SUSPECT, "Mobizen",       "Screen recorder & remote");

        // Keyloggers / input monitors
        putThreat("com.system.android.keyboard",THREAT_STALKER, "System Keyboard","Keylogger disguise");
        putThreat("com.keylogger.android",      THREAT_STALKER, "Keylogger",     "Explicit keylogger");

        // Known adware/spyware SDKs masquerading as apps
        putThreat("com.revmob.android.sdk",     THREAT_SUSPECT, "RevMob",        "Aggressive ad SDK");
        putThreat("com.airpush.android",        THREAT_SUSPECT, "AirPush",       "Push-notification adware");
    }

    private static void putThreat(String pkg, int level, String label, String reason) {
        KNOWN_THREATS.put(pkg, new ThreatResult(level, label, reason));
    }

    // Suspicious package name patterns (partial match)
    private static final String[] SUSPICIOUS_PKG_PATTERNS = {
        "spy", "track", "monitor", "stealth", "hidden", "invisible",
        "keylog", "surveillance", "snoop", "intercept", "stalker"
    };

    // Suspicious app name patterns (exact match on lower-cased name)
    private static final String[] SUSPICIOUS_NAME_PATTERNS = {
        "spy", "track my phone", "phone tracker", "kid tracker", "family tracker",
        "spy camera", "secret recorder", "invisible keylogger", "hidden camera"
    };

    /**
     * Main analysis entry point.
     * Returns a ThreatResult — check result.level != THREAT_NONE to flag.
     */
    public static ThreatResult analyze(AppSecurityInfo app) {

        // 1. Check known threat database
        ThreatResult known = KNOWN_THREATS.get(app.packageName);
        if (known != null) return known;

        // 2. Check partial package name patterns (non-system only)
        if (!app.isSystemApp) {
            String pkgLower = app.packageName.toLowerCase();
            for (String pat : SUSPICIOUS_PKG_PATTERNS) {
                if (pkgLower.contains(pat) && !isWhitelisted(app.packageName)) {
                    return new ThreatResult(THREAT_SUSPECT, "Suspect Package",
                            "Package name contains suspicious keyword: '" + pat + "'");
                }
            }
            String nameLower = app.appName.toLowerCase();
            for (String pat : SUSPICIOUS_NAME_PATTERNS) {
                if (nameLower.contains(pat)) {
                    return new ThreatResult(THREAT_SUSPECT, "Suspect Name",
                            "App name contains surveillance keyword: '" + pat + "'");
                }
            }
        }

        // 3. Spyware permission combo rules
        // A non-system, non-well-known app with ALL of: camera + mic + location + contacts + internet
        if (!app.isSystemApp && app.hasCamera && app.hasMicrophone
                && app.hasLocation && app.hasContacts && app.hasInternet) {
            return new ThreatResult(THREAT_SUSPECT, "Spyware Pattern",
                    "Has Camera + Mic + Location + Contacts + Internet — classic spy combo");
        }

        // SMS + Contacts + Internet (SMS exfiltration)
        if (!app.isSystemApp && app.hasSms && app.hasContacts && app.hasInternet
                && app.hasCamera) {
            return new ThreatResult(THREAT_SUSPECT, "Data Exfiltration Risk",
                    "Can read SMS, contacts, and photos — and upload them via Internet");
        }

        // Accessibility + Internet (screen reader + data exfiltration)
        if (!app.isSystemApp && app.hasAccessibility && app.hasInternet) {
            return new ThreatResult(THREAT_SUSPECT, "Screen Reading Risk",
                    "Accessibility service + Internet: can read your screen and send data out");
        }

        // Overlay + Accessibility (phishing toolkit)
        if (!app.isSystemApp && app.hasOverlay && app.hasAccessibility) {
            return new ThreatResult(THREAT_SUSPECT, "Phishing Toolkit",
                    "Can draw overlays and read screen — could intercept banking passwords");
        }

        // 4. Disguised as a system app
        if (!app.isSystemApp && isDisguisedAsSystem(app.packageName, app.appName)) {
            return new ThreatResult(THREAT_SUSPECT, "Disguised App",
                    "App pretends to be a system component but is user-installed");
        }

        return new ThreatResult(THREAT_NONE, "", "");
    }

    /**
     * Quick threat level check for the risk score calculation.
     */
    public static int getThreatScoreBonus(AppSecurityInfo app) {
        ThreatResult r = analyze(app);
        switch (r.level) {
            case THREAT_STALKER: return 60;
            case THREAT_SPYWARE: return 50;
            case THREAT_SUSPECT: return 30;
            default:             return 0;
        }
    }

    /**
     * Threat level display color.
     */
    public static int threatColor(int level) {
        switch (level) {
            case THREAT_STALKER: return 0xFFFF0055;
            case THREAT_SPYWARE: return 0xFFE63946;
            case THREAT_SUSPECT: return 0xFFFF9F1C;
            default:             return 0xFF94A3B8;
        }
    }

    public static String threatLevelName(int level) {
        switch (level) {
            case THREAT_STALKER: return "STALKERWARE";
            case THREAT_SPYWARE: return "SPYWARE";
            case THREAT_SUSPECT: return "SUSPICIOUS";
            default:             return "Clean";
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static final Set<String> WHITELIST = new HashSet<>(Arrays.asList(
            "org.teamviewer.teamviewer",
            "com.anydesk.anydeskandroid",
            "com.rsupport.mobizen.global"
    ));

    private static boolean isWhitelisted(String pkg) {
        return WHITELIST.contains(pkg);
    }

    private static final String[] SYSTEM_DISGUISE_NAMES = {
        "com.android.settings2", "com.android.setting",
        "com.google.android.gms2", "com.system.service",
        "com.android.systemui2", "android.system.service",
        "com.android.phone2", "com.system.monitor"
    };

    private static final String[] SYSTEM_DISGUISE_APP_NAMES = {
        "system service", "phone service", "system monitor",
        "android service", "google service", "device manager"
    };

    private static boolean isDisguisedAsSystem(String pkg, String name) {
        String pkgLower  = pkg.toLowerCase();
        String nameLower = name.toLowerCase();
        for (String d : SYSTEM_DISGUISE_NAMES) {
            if (pkgLower.equals(d)) return true;
        }
        for (String d : SYSTEM_DISGUISE_APP_NAMES) {
            if (nameLower.equals(d)) return true;
        }
        return false;
    }
}
