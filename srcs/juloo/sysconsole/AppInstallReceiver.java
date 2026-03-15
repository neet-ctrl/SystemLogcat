package juloo.sysconsole;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Real-time monitoring of app install/uninstall/replace events.
 *
 * Register this receiver in your Activity/Service with
 * {@link #makeFilter()} to get live notifications when:
 *   - A new app is installed
 *   - An app is uninstalled
 *   - An app is updated (replaced)
 *
 * Each event triggers the {@link InstallListener} callback with
 * an analysis of the newly installed package.
 */
public class AppInstallReceiver extends BroadcastReceiver {

    public interface InstallListener {
        void onAppInstalled(String packageName, String appName,
                            AppSecurityInfo info, SecurityAlert alert);
        void onAppUninstalled(String packageName, String appName);
        void onAppUpdated(String packageName, String appName);
    }

    private final InstallListener mListener;
    private final Context         mCtx;

    public AppInstallReceiver(Context ctx, InstallListener listener) {
        this.mCtx      = ctx.getApplicationContext();
        this.mListener = listener;
    }

    public static IntentFilter makeFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        return filter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getData() == null) return;

        String action  = intent.getAction();
        String pkg     = intent.getData().getSchemeSpecificPart();
        boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);

        if (pkg == null || pkg.isEmpty()) return;

        PackageManager pm = context.getPackageManager();
        String appName = getAppName(pm, pkg);

        if (Intent.ACTION_PACKAGE_REMOVED.equals(action) && !replacing) {
            if (mListener != null) mListener.onAppUninstalled(pkg, appName);
            return;
        }

        if (Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            if (mListener != null) mListener.onAppUpdated(pkg, appName);
            return;
        }

        if (Intent.ACTION_PACKAGE_ADDED.equals(action) && !replacing) {
            // New install — scan it
            AppSecurityInfo info = buildInfo(pm, pkg);
            SecurityAlert   alert = buildInstallAlert(pkg, appName, info);
            if (mListener != null) mListener.onAppInstalled(pkg, appName, info, alert);

            // Add to the global scan cache
            SecurityScanManager mgr = SecurityHub.getManager(mCtx);
            List<AppSecurityInfo> cached = mgr.getCachedApps();
            List<AppSecurityInfo> updated = new ArrayList<>(cached);
            updated.add(0, info);
        }
    }

    private AppSecurityInfo buildInfo(PackageManager pm, String pkg) {
        AppSecurityInfo info = new AppSecurityInfo();
        info.packageName  = pkg;
        info.installTime  = System.currentTimeMillis();
        try {
            PackageInfo pi = pm.getPackageInfo(pkg,
                    PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES);
            info.versionName = pi.versionName != null ? pi.versionName : "?";
            info.versionCode = pi.versionCode;
            info.isSystemApp = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            info.appName     = (String) pm.getApplicationLabel(pi.applicationInfo);
            info.icon        = pm.getApplicationIcon(pkg);
            analyzePermissions(info, pi);
        } catch (Exception e) {
            info.appName = pkg;
        }

        // Spy detection
        SpyDetectionEngine.ThreatResult threat = SpyDetectionEngine.analyze(info);
        if (threat.level != SpyDetectionEngine.THREAT_NONE) {
            info.riskFactors.add("⚠ " + threat.label + ": " + threat.reason);
        }

        // Risk score
        info.riskScore = Math.min(calculateBaseRisk(info) +
                SpyDetectionEngine.getThreatScoreBonus(info), 100);
        if (info.riskScore >= 50)      info.riskLevel = AppSecurityInfo.RISK_HIGH;
        else if (info.riskScore >= 20) info.riskLevel = AppSecurityInfo.RISK_MEDIUM;
        else                           info.riskLevel = AppSecurityInfo.RISK_LOW;

        return info;
    }

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
                case android.Manifest.permission.READ_SMS:
                case android.Manifest.permission.SEND_SMS:
                case android.Manifest.permission.RECEIVE_SMS:
                    info.hasSms = true; break;
                case android.Manifest.permission.CALL_PHONE:
                case android.Manifest.permission.READ_CALL_LOG:
                    info.hasPhone = true; break;
                case android.Manifest.permission.INTERNET:
                    info.hasInternet = true; break;
                case android.Manifest.permission.SYSTEM_ALERT_WINDOW:
                    info.hasOverlay = true; break;
            }
        }
    }

    private int calculateBaseRisk(AppSecurityInfo info) {
        int score = 0;
        if (info.hasCamera && info.hasInternet)     score += 25;
        if (info.hasMicrophone && info.hasInternet) score += 25;
        if (info.hasLocation && info.hasInternet)   score += 20;
        if (info.hasAccessibility)                   score += 30;
        if (info.hasOverlay)                         score += 15;
        if (info.hasSms)                             score += 20;
        return score;
    }

    private SecurityAlert buildInstallAlert(String pkg, String appName, AppSecurityInfo info) {
        int severity;
        String title, desc;

        SpyDetectionEngine.ThreatResult threat = SpyDetectionEngine.analyze(info);
        if (threat.level >= SpyDetectionEngine.THREAT_SPYWARE) {
            severity = SecurityAlert.SEV_CRITICAL;
            title    = "⚠ SPYWARE Installed: " + appName;
            desc     = threat.reason;
        } else if (info.riskLevel == AppSecurityInfo.RISK_HIGH) {
            severity = SecurityAlert.SEV_CRITICAL;
            title    = "High-risk app installed: " + appName;
            desc     = "Risk score " + info.riskScore + "/100 — review permissions immediately";
        } else if (info.riskLevel == AppSecurityInfo.RISK_MEDIUM) {
            severity = SecurityAlert.SEV_WARNING;
            title    = "New app installed: " + appName;
            desc     = "Medium risk (" + info.riskScore + "/100) — " +
                    info.permissionCount() + " permissions requested";
        } else {
            severity = SecurityAlert.SEV_INFO;
            title    = "New app installed: " + appName;
            desc     = "Low risk — " + info.permissionCount() + " permissions requested";
        }

        return new SecurityAlert(SecurityAlert.TYPE_INSTALL, severity,
                title, desc, pkg, appName, System.currentTimeMillis());
    }

    private static String getAppName(PackageManager pm, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return (String) pm.getApplicationLabel(ai);
        } catch (Exception e) {
            return pkg;
        }
    }
}
