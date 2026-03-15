package juloo.sysconsole;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Exports a full-device security audit report as a plain-text file
 * and offers it for sharing via Android's share sheet.
 *
 * Includes:
 *  - Device info summary
 *  - Overall risk level
 *  - All high/medium risk apps with their risk factors
 *  - Elevated-privilege apps (device admins, accessibility, VPN)
 *  - Spyware/stalkerware detections
 *  - Banking risk analysis
 *  - Recent alerts
 */
public class SecurityReportExporter {

    public static File exportToFile(Context ctx,
                                     List<AppSecurityInfo> apps,
                                     List<SecurityAlert> alerts,
                                     List<DeviceSecurityHelper.ElevatedApp> elevated) {
        try {
            File dir  = new File(ctx.getFilesDir(), "reports");
            dir.mkdirs();
            File file = new File(dir, "security_report_" +
                    System.currentTimeMillis() + ".txt");

            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                writeReport(pw, apps, alerts, elevated);
            }
            return file;
        } catch (Exception e) {
            return null;
        }
    }

    public static void shareReport(Context ctx,
                                    List<AppSecurityInfo> apps,
                                    List<SecurityAlert> alerts,
                                    List<DeviceSecurityHelper.ElevatedApp> elevated) {
        File f = exportToFile(ctx, apps, alerts, elevated);
        if (f == null) return;
        try {
            Uri uri = FileProvider.getUriForFile(ctx,
                    ctx.getPackageName() + ".provider", f);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, "Device Security Audit Report");
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(share, "Share Security Report"));
        } catch (Exception ignored) {}
    }

    private static void writeReport(PrintWriter pw,
                                     List<AppSecurityInfo> apps,
                                     List<SecurityAlert> alerts,
                                     List<DeviceSecurityHelper.ElevatedApp> elevated) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String ts = sdf.format(new Date());

        pw.println("╔══════════════════════════════════════════════════════════╗");
        pw.println("║          DEVICE SECURITY AUDIT REPORT                   ║");
        pw.println("║          Family Security Auditor — System Console        ║");
        pw.println("╚══════════════════════════════════════════════════════════╝");
        pw.println();
        pw.println("Generated : " + ts);
        pw.println("Android   : " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        pw.println("Device    : " + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println("Shizuku   : " + (ShizukuCommandHelper.isAvailable() ? "Connected ✓" : "Not connected"));
        pw.println();

        // ── Overall Risk ─────────────────────────────────────────────────────
        int highCount = SecurityScanManager.countWithPerm(apps, "high");
        int midCount  = SecurityScanManager.countWithPerm(apps, "medium");
        String overall = highCount > 0 ? "HIGH" : midCount > 0 ? "MEDIUM" : "LOW";
        pw.println("─────────────────────────────────────────────────────────────");
        pw.println("OVERALL RISK: " + overall);
        pw.println("Total apps: " + apps.size());
        pw.println("High risk:  " + highCount);
        pw.println("Medium risk:" + midCount);
        pw.println("Low risk:   " + (apps.size() - highCount - midCount));
        pw.println();

        // ── Spyware / Threat Detections ──────────────────────────────────────
        pw.println("─────────────────────────────────────────────────────────────");
        pw.println("THREAT DETECTIONS");
        pw.println();
        boolean anyThreats = false;
        for (AppSecurityInfo a : apps) {
            if (a.threatLevel > SpyDetectionEngine.THREAT_NONE) {
                anyThreats = true;
                pw.println("  [" + SpyDetectionEngine.threatLevelName(a.threatLevel) + "]  "
                        + a.appName + " (" + a.packageName + ")");
                pw.println("  Reason: " + a.threatReason);
                pw.println();
            }
        }
        if (!anyThreats) pw.println("  No threats detected.");
        pw.println();

        // ── Elevated Privilege Apps ───────────────────────────────────────────
        pw.println("─────────────────────────────────────────────────────────────");
        pw.println("ELEVATED PRIVILEGE APPS");
        pw.println();
        if (elevated == null || elevated.isEmpty()) {
            pw.println("  None found.");
        } else {
            for (DeviceSecurityHelper.ElevatedApp ea : elevated) {
                pw.println("  [" + ea.privilege + "]  " + ea.appName
                        + " (" + ea.packageName + ")");
                pw.println("  " + ea.description);
                pw.println();
            }
        }
        pw.println();

        // ── High Risk Apps ────────────────────────────────────────────────────
        pw.println("─────────────────────────────────────────────────────────────");
        pw.println("HIGH RISK APPS  (Risk Score ≥ 50/100)");
        pw.println();
        boolean anyHigh = false;
        for (AppSecurityInfo a : apps) {
            if (a.riskLevel != AppSecurityInfo.RISK_HIGH) continue;
            anyHigh = true;
            pw.println("  " + a.appName + " [" + a.riskScore + "/100]");
            pw.println("  Package: " + a.packageName);
            pw.println("  Type: " + (a.isSystemApp ? "System" : "User") + " App");
            pw.println("  Network: " + a.networkUsageFormatted() + " (7 days)");
            pw.println("  Risk factors:");
            for (String f : a.riskFactors) pw.println("    • " + f);
            pw.println();
        }
        if (!anyHigh) pw.println("  No high-risk apps found.");
        pw.println();

        // ── Banking Risk Analysis ─────────────────────────────────────────────
        pw.println("─────────────────────────────────────────────────────────────");
        pw.println("BANKING RISK ANALYSIS");
        pw.println();
        boolean anyBankingRisk = false;
        for (AppSecurityInfo a : apps) {
            if (!a.isBankingRisk && !a.isBankingApp) continue;
            if (a.riskFactors.isEmpty()) continue;
            anyBankingRisk = true;
            pw.println("  " + (a.isBankingApp ? "[BANKING APP]" : "[THREAT TO BANKING]")
                    + "  " + a.appName);
            for (String f : a.riskFactors) {
                if (f.contains("bank") || f.contains("OTP") || f.contains("overlay")
                        || f.contains("SMS") || f.contains("phish")) {
                    pw.println("    ⚠ " + f);
                }
            }
            pw.println();
        }
        if (!anyBankingRisk) pw.println("  No banking-related risks detected.");
        pw.println();

        // ── Alerts ────────────────────────────────────────────────────────────
        pw.println("─────────────────────────────────────────────────────────────");
        pw.println("SECURITY ALERTS  (" + (alerts != null ? alerts.size() : 0) + " total)");
        pw.println();
        if (alerts == null || alerts.isEmpty()) {
            pw.println("  No alerts.");
        } else {
            for (SecurityAlert al : alerts) {
                pw.println("  [" + al.typeLabel() + " | "
                        + (al.severity == SecurityAlert.SEV_CRITICAL ? "CRITICAL"
                           : al.severity == SecurityAlert.SEV_WARNING ? "WARNING" : "INFO")
                        + "]");
                pw.println("  " + al.title);
                pw.println("  " + al.description);
                pw.println();
            }
        }

        pw.println("─────────────────────────────────────────────────────────────");
        pw.println("END OF REPORT");
        pw.println("All data analyzed on-device. No data was uploaded.");
    }
}
