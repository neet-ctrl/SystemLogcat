package juloo.sysconsole;

import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.List;

public class AppSecurityInfo {

    public static final int RISK_LOW    = 0;
    public static final int RISK_MEDIUM = 1;
    public static final int RISK_HIGH   = 2;

    // ── Identity ──────────────────────────────────────────────────────────────
    public String   packageName;
    public String   appName;
    public String   versionName;
    public int      versionCode;
    public boolean  isSystemApp;
    public long     installTime;
    public long     lastUsed;
    public Drawable icon;

    // ── Requested permissions ─────────────────────────────────────────────────
    public boolean hasCamera;
    public boolean hasMicrophone;
    public boolean hasLocation;
    public boolean hasContacts;
    public boolean hasStorage;
    public boolean hasSms;
    public boolean hasPhone;
    public boolean hasCalendar;
    public boolean hasBodySensors;
    public boolean hasAccessibility;
    public boolean hasInternet;
    public boolean hasOverlay;
    public boolean hasVpn;
    public boolean hasNotificationAccess;
    public boolean hasReadMediaImages;
    public boolean hasProcessOutgoingCalls;
    public boolean hasBootReceiver;

    // ── Granted runtime permissions (from Shizuku dumpsys) ───────────────────
    public List<String> grantedPermissions = new ArrayList<>();

    // ── AppOps usage (from Shizuku or AppOpsManager) ─────────────────────────
    public boolean cameraUsedRecently;
    public boolean micUsedRecently;
    public boolean locationUsedRecently;
    public boolean clipboardUsedRecently;
    public boolean screenshotTakenRecently;

    // ── Process & runtime state ───────────────────────────────────────────────
    public boolean isRunning;
    public boolean isForegrounded;
    public int     backgroundRunCount;
    public long    backgroundTimeMs;

    // ── Network usage (last 7 days) ───────────────────────────────────────────
    public long networkBytesTotal;

    // ── Privilege / elevation ─────────────────────────────────────────────────
    public boolean isDeviceAdmin;
    public boolean isAccessibilityService;
    public boolean isNotificationListener;
    public boolean isVpnService;

    // ── Threat detection ─────────────────────────────────────────────────────
    public int    threatLevel;    // SpyDetectionEngine.THREAT_*
    public String threatLabel;
    public String threatReason;
    public boolean isSuspectedSpyware;
    public boolean isBankingApp;
    public boolean isBankingRisk;

    // ── Risk ──────────────────────────────────────────────────────────────────
    public int          riskScore;
    public int          riskLevel;
    public List<String> riskFactors = new ArrayList<>();

    // ── Helpers ───────────────────────────────────────────────────────────────

    public int permissionCount() {
        int c = 0;
        if (hasCamera)            c++;
        if (hasMicrophone)        c++;
        if (hasLocation)          c++;
        if (hasContacts)          c++;
        if (hasStorage)           c++;
        if (hasSms)               c++;
        if (hasPhone)             c++;
        if (hasCalendar)          c++;
        if (hasBodySensors)       c++;
        if (hasAccessibility)     c++;
        if (hasOverlay)           c++;
        if (hasVpn)               c++;
        if (hasNotificationAccess)c++;
        if (hasReadMediaImages)   c++;
        if (hasProcessOutgoingCalls) c++;
        return c;
    }

    public String riskLevelLabel() {
        switch (riskLevel) {
            case RISK_HIGH:   return "High";
            case RISK_MEDIUM: return "Medium";
            default:          return "Low";
        }
    }

    public int riskColor() {
        switch (riskLevel) {
            case RISK_HIGH:   return 0xFFE63946;
            case RISK_MEDIUM: return 0xFFFF9F1C;
            default:          return 0xFF00A896;
        }
    }

    public String networkUsageFormatted() {
        return NetworkUsageHelper.formatBytes(networkBytesTotal);
    }
}
