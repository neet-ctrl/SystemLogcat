package juloo.sysconsole;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AppDetailsActivity extends Activity {

    public static final String EXTRA_PKG = "pkg";

    private SecurityUiHelper mUi;
    private AppSecurityInfo  mApp;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        mUi = new SecurityUiHelper(this);
        String pkg = getIntent().getStringExtra(EXTRA_PKG);
        if (pkg != null) {
            for (AppSecurityInfo a : SecurityHub.getManager(this).getCachedApps()) {
                if (a.packageName.equals(pkg)) { mApp = a; break; }
            }
        }
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SecurityUiHelper.CLR_BG);
        root.addView(buildTopBar());

        if (mApp == null) {
            TextView tv = mUi.label("App not found.", 16f,
                    SecurityUiHelper.CLR_SECONDARY, false);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, mUi.dp(48), 0, 0);
            root.addView(tv);
            setContentView(root);
            return;
        }

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(SecurityUiHelper.CLR_BG);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(mUi.dp(16), mUi.dp(12), mUi.dp(16), mUi.dp(40));

        // Threat banner (most prominent — show first if threat detected)
        if (mApp.threatLevel > SpyDetectionEngine.THREAT_NONE) {
            content.addView(buildThreatBanner());
            content.addView(mUi.spacer(12));
        }

        content.addView(buildHeaderCard());
        content.addView(mUi.spacer(12));

        if (mApp.isBankingRisk || mApp.isBankingApp) {
            content.addView(buildBankingCard());
            content.addView(mUi.spacer(12));
        }

        content.addView(buildPermissionsCard());
        content.addView(mUi.spacer(12));
        content.addView(buildShizukuGrantedCard());
        content.addView(mUi.spacer(12));
        content.addView(buildRiskFactorsCard());
        content.addView(mUi.spacer(12));
        content.addView(buildBehaviorCard());
        content.addView(mUi.spacer(12));
        content.addView(buildNetworkCard());

        if (mApp.isDeviceAdmin || mApp.isAccessibilityService
                || mApp.isNotificationListener || mApp.isVpnService) {
            content.addView(mUi.spacer(12));
            content.addView(buildPrivilegeCard());
        }

        if (!mApp.isSystemApp) {
            content.addView(mUi.spacer(16));
            content.addView(buildUninstallButton());
        }

        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) result = getResources().getDimensionPixelSize(resId);
        return Math.max(result, mUi.dp(24));
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(SecurityUiHelper.CLR_NAV_BG);
        bar.setPadding(mUi.dp(16), mUi.dp(10) + getStatusBarHeight(), mUi.dp(16), mUi.dp(10));

        Button back = new Button(this);
        back.setText("←");
        back.setTextSize(18f);
        back.setTextColor(SecurityUiHelper.CLR_TEAL);
        back.setBackground(null);
        back.setAllCaps(false);
        back.setPadding(0, 0, mUi.dp(8), 0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        String title = (mApp != null && mApp.appName != null) ? mApp.appName : "App Details";
        TextView tvTitle = mUi.label(title, 17f, SecurityUiHelper.CLR_TEXT, true);
        bar.addView(tvTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    private View buildThreatBanner() {
        int color = SpyDetectionEngine.threatColor(mApp.threatLevel);
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(mUi.dp(16), mUi.dp(14), mUi.dp(16), mUi.dp(14));

        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(color & 0x22FFFFFF);
        bg.setCornerRadius(mUi.dp(12));
        bg.setStroke(mUi.dp(1), color);
        banner.setBackground(bg);

        LinearLayout headerRow = mUi.row(true);
        headerRow.addView(mUi.label("☠  ", 18f, color, true));
        headerRow.addView(mUi.label(
                SpyDetectionEngine.threatLevelName(mApp.threatLevel) + " DETECTED",
                14f, color, true));
        banner.addView(headerRow);

        banner.addView(mUi.spacer(6));
        banner.addView(mUi.label(mApp.threatReason, 12f,
                SecurityUiHelper.CLR_TEXT, false));

        if (!mApp.isSystemApp) {
            banner.addView(mUi.spacer(10));
            Button remove = mUi.primaryButton("Remove App Now", color);
            remove.setOnClickListener(v -> {
                Uri uri = Uri.parse("package:" + mApp.packageName);
                startActivity(new Intent(Intent.ACTION_DELETE, uri));
            });
            banner.addView(remove);
        }
        return banner;
    }

    private View buildHeaderCard() {
        LinearLayout card = mUi.card(16, 20);

        LinearLayout headerRow = mUi.row(true);
        if (mApp.icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(mApp.icon);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    mUi.dp(52), mUi.dp(52));
            ilp.rightMargin = mUi.dp(14);
            headerRow.addView(iv, ilp);
        }

        LinearLayout textPart = new LinearLayout(this);
        textPart.setOrientation(LinearLayout.VERTICAL);
        textPart.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        textPart.addView(mUi.label(mApp.appName, 17f, SecurityUiHelper.CLR_TEXT, true));
        textPart.addView(mUi.label(mApp.packageName, 11f, SecurityUiHelper.CLR_SECONDARY, false));
        textPart.addView(mUi.label("v" + mApp.versionName, 11f,
                SecurityUiHelper.CLR_SECONDARY, false));

        // Tags row
        LinearLayout tagsRow = mUi.row(false);
        tagsRow.setPadding(0, mUi.dp(6), 0, 0);
        if (mApp.isBankingApp) tagsRow.addView(mUi.badge("Banking", 0x330080FF, 0xFF60A5FA, 20));
        if (mApp.isSystemApp)  tagsRow.addView(addTagGap(mUi.badge("System", 0x33475569, SecurityUiHelper.CLR_SECONDARY, 20)));
        if (mApp.isDeviceAdmin) tagsRow.addView(addTagGap(mUi.badge("Admin", 0x33E63946, 0xFFE63946, 20)));
        if (mApp.isVpnService)  tagsRow.addView(addTagGap(mUi.badge("VPN", 0x33FF9F1C, 0xFFFF9F1C, 20)));
        textPart.addView(tagsRow);

        headerRow.addView(textPart);
        card.addView(headerRow);
        card.addView(mUi.divider());

        // Score + status row
        LinearLayout scoreRow = mUi.row(true);
        scoreRow.setGravity(Gravity.CENTER_VERTICAL);
        scoreRow.addView(buildScoreGauge(mApp.riskScore, mApp.riskColor()));

        LinearLayout scoreMeta = new LinearLayout(this);
        scoreMeta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams smLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        smLp.leftMargin = mUi.dp(16);
        scoreMeta.setLayoutParams(smLp);

        scoreMeta.addView(mUi.label("Risk Score", 11f, SecurityUiHelper.CLR_SECONDARY, false));
        scoreMeta.addView(mUi.spacer(4));
        scoreMeta.addView(mUi.riskBadge(mApp));
        scoreMeta.addView(mUi.spacer(6));

        String statusText = mApp.isRunning ? "⬤  Currently Running" : "○  Not Running";
        int statusColor = mApp.isRunning ? SecurityUiHelper.CLR_GREEN : SecurityUiHelper.CLR_SECONDARY;
        scoreMeta.addView(mUi.label(statusText, 12f, statusColor, false));

        scoreRow.addView(scoreMeta);
        card.addView(scoreRow);
        return card;
    }

    private View buildBankingCard() {
        LinearLayout card = mUi.card(12, 14);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(mApp.isBankingApp ? 0xFF1A2540 : 0xFF2A1A1A);
        bg.setCornerRadius(mUi.dp(12));
        bg.setStroke(mUi.dp(1), mApp.isBankingApp ? 0xFF3B82F6 : 0xFFFF9F1C);
        card.setBackground(bg);

        card.addView(mUi.label(
                mApp.isBankingApp ? "🏦 BANKING APP" : "⚠ BANKING SECURITY RISK",
                11f,
                mApp.isBankingApp ? 0xFF60A5FA : SecurityUiHelper.CLR_ORANGE,
                true));
        card.addView(mUi.spacer(8));

        if (mApp.isBankingApp) {
            card.addView(mUi.label(
                    "This is a banking or finance app. It handles sensitive\nfinancial data — ensure it has no suspicious permissions.",
                    12f, SecurityUiHelper.CLR_TEXT, false));
        } else {
            card.addView(mUi.label(
                    "This app poses a risk to banking apps installed on this device.",
                    12f, SecurityUiHelper.CLR_TEXT, false));
        }
        return card;
    }

    private View buildPermissionsCard() {
        LinearLayout card = mUi.card();
        card.addView(mUi.label("PERMISSION ANALYSIS", 11f,
                SecurityUiHelper.CLR_SECONDARY, true));
        card.addView(mUi.spacer(10));

        addPermRow(card, "Camera",              "Take photos and record video",
                mApp.hasCamera,            mApp.cameraUsedRecently);
        card.addView(mUi.divider());
        addPermRow(card, "Microphone",          "Record audio",
                mApp.hasMicrophone,        mApp.micUsedRecently);
        card.addView(mUi.divider());
        addPermRow(card, "Location",            "Access device location",
                mApp.hasLocation,          mApp.locationUsedRecently);
        card.addView(mUi.divider());
        addPermRow(card, "Contacts",            "Read your contacts list",
                mApp.hasContacts,          false);
        card.addView(mUi.divider());
        addPermRow(card, "Storage",             "Read/write files on device",
                mApp.hasStorage,           false);
        card.addView(mUi.divider());
        addPermRow(card, "SMS",                 "Read and send text messages",
                mApp.hasSms,               false);
        card.addView(mUi.divider());
        addPermRow(card, "Phone & Calls",       "Make calls and read call log",
                mApp.hasPhone,             false);
        card.addView(mUi.divider());
        addPermRow(card, "Clipboard",           "Read clipboard contents",
                mApp.clipboardUsedRecently,mApp.clipboardUsedRecently);
        card.addView(mUi.divider());
        addPermRow(card, "Accessibility",       "Read screen content & simulate input",
                mApp.hasAccessibility,     false);
        card.addView(mUi.divider());
        addPermRow(card, "Draw Over Apps",      "Overlay UI on top of other apps",
                mApp.hasOverlay,           false);
        card.addView(mUi.divider());
        addPermRow(card, "VPN Service",         "Can intercept all network traffic",
                mApp.hasVpn,               false);
        card.addView(mUi.divider());
        addPermRow(card, "Notification Access", "Read all app notifications",
                mApp.hasNotificationAccess,false);
        card.addView(mUi.divider());
        addPermRow(card, "Internet",            "Network access",
                mApp.hasInternet,          false);
        card.addView(mUi.divider());
        addPermRow(card, "Boot Start",          "Starts automatically on device boot",
                mApp.hasBootReceiver,      false);

        return card;
    }

    private View buildShizukuGrantedCard() {
        LinearLayout card = mUi.card();
        boolean shizukuOk = ShizukuCommandHelper.isAvailable();

        card.addView(mUi.label("GRANTED RUNTIME PERMISSIONS"
                + (shizukuOk ? "" : " (Shizuku required)"),
                11f, SecurityUiHelper.CLR_SECONDARY, true));
        card.addView(mUi.spacer(8));

        if (!shizukuOk) {
            card.addView(mUi.label(
                    "Connect Shizuku to see exactly which runtime permissions\nare actually GRANTED (not just requested).",
                    12f, SecurityUiHelper.CLR_SECONDARY, false));
        } else if (mApp.grantedPermissions.isEmpty()) {
            card.addView(mUi.label(
                    "No dangerous runtime permissions granted.",
                    12f, SecurityUiHelper.CLR_GREEN, false));
        } else {
            for (String perm : mApp.grantedPermissions) {
                LinearLayout row = mUi.row(true);
                row.setPadding(0, mUi.dp(3), 0, mUi.dp(3));
                row.addView(mUi.colorDot(SecurityUiHelper.CLR_ORANGE, 6));
                String shortPerm = perm.replace("android.permission.", "");
                row.addView(mUi.label(shortPerm, 12f, SecurityUiHelper.CLR_TEXT, false));
                card.addView(row);
            }
        }
        return card;
    }

    private View buildRiskFactorsCard() {
        LinearLayout card = mUi.card();
        card.addView(mUi.label("RISK FACTORS", 11f, SecurityUiHelper.CLR_SECONDARY, true));
        card.addView(mUi.spacer(10));

        if (mApp.riskFactors.isEmpty()) {
            LinearLayout row = mUi.row(true);
            row.addView(mUi.colorDot(SecurityUiHelper.CLR_GREEN, 8));
            row.addView(mUi.label("No significant risk factors detected.",
                    13f, SecurityUiHelper.CLR_GREEN, false));
            card.addView(row);
        } else {
            for (String factor : mApp.riskFactors) {
                LinearLayout row = mUi.row(true);
                row.setPadding(0, mUi.dp(4), 0, mUi.dp(4));
                boolean critical = factor.startsWith("⚠") || factor.contains("STALKER")
                        || factor.contains("Admin") || factor.contains("wipe");
                int dotColor = critical ? SecurityUiHelper.CLR_RED : SecurityUiHelper.CLR_ORANGE;
                row.addView(mUi.colorDot(dotColor, 6));
                TextView tv = mUi.label(factor, 12f, SecurityUiHelper.CLR_TEXT, false);
                tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(tv);
                card.addView(row);
            }
        }
        return card;
    }

    private View buildBehaviorCard() {
        LinearLayout card = mUi.card();
        card.addView(mUi.label("BEHAVIOR INSIGHTS", 11f,
                SecurityUiHelper.CLR_SECONDARY, true));
        card.addView(mUi.spacer(10));

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

        addInfoRow(card, "Last Used",
                mApp.lastUsed > 0 ? sdf.format(new Date(mApp.lastUsed)) : "Unknown",
                SecurityUiHelper.CLR_TEXT);
        card.addView(mUi.divider());
        addInfoRow(card, "Installed",
                sdf.format(new Date(mApp.installTime)), SecurityUiHelper.CLR_TEXT);
        card.addView(mUi.divider());
        addInfoRow(card, "Background Time",
                mApp.backgroundTimeMs > 0
                        ? formatDuration(mApp.backgroundTimeMs) + " (today)"
                        : "Unknown",
                mApp.backgroundTimeMs > 3600000
                        ? SecurityUiHelper.CLR_ORANGE : SecurityUiHelper.CLR_TEXT);
        card.addView(mUi.divider());
        addInfoRow(card, "App Type",
                mApp.isSystemApp ? "System App" : "User App",
                mApp.isSystemApp
                        ? SecurityUiHelper.CLR_SECONDARY : SecurityUiHelper.CLR_TEAL);
        card.addView(mUi.divider());
        addInfoRow(card, "Version", mApp.versionName + " (" + mApp.versionCode + ")",
                SecurityUiHelper.CLR_SECONDARY);
        card.addView(mUi.divider());
        addInfoRow(card, "Package", mApp.packageName, SecurityUiHelper.CLR_SECONDARY);

        return card;
    }

    private View buildNetworkCard() {
        LinearLayout card = mUi.card();
        card.addView(mUi.label("NETWORK USAGE (7 DAYS)", 11f,
                SecurityUiHelper.CLR_SECONDARY, true));
        card.addView(mUi.spacer(8));

        if (mApp.networkBytesTotal <= 0) {
            card.addView(mUi.label(
                    "No network usage data available.\nGrant Usage Access permission to see data.",
                    12f, SecurityUiHelper.CLR_SECONDARY, false));
        } else {
            LinearLayout row = mUi.row(true);
            row.addView(mUi.label("Total Data Used", 13f, SecurityUiHelper.CLR_SECONDARY, false));
            View spacer = new View(this);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(spacer);

            long bytes = mApp.networkBytesTotal;
            int networkRiskColor = bytes > 500L * 1024 * 1024
                    ? SecurityUiHelper.CLR_RED
                    : bytes > 100L * 1024 * 1024
                    ? SecurityUiHelper.CLR_ORANGE
                    : SecurityUiHelper.CLR_TEXT;
            row.addView(mUi.label(mApp.networkUsageFormatted(), 13f, networkRiskColor, true));
            card.addView(row);

            if (bytes > 100L * 1024 * 1024 && !mApp.isSystemApp) {
                card.addView(mUi.spacer(6));
                card.addView(mUi.label(
                        "⚠ High network usage — possible data exfiltration if combined with\ncamera, mic, or location access.",
                        12f, SecurityUiHelper.CLR_ORANGE, false));
            }
        }
        return card;
    }

    private View buildPrivilegeCard() {
        LinearLayout card = mUi.card(12, 14);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF2A1A1A);
        bg.setCornerRadius(mUi.dp(12));
        bg.setStroke(mUi.dp(1), 0xFFE63946);
        card.setBackground(bg);

        card.addView(mUi.label("ELEVATED PRIVILEGES", 11f, 0xFFE63946, true));
        card.addView(mUi.spacer(8));

        if (mApp.isDeviceAdmin) {
            addPrivRow(card, "Device Administrator",
                    "Can wipe, lock, or control this device remotely", 0xFFE63946);
        }
        if (mApp.isAccessibilityService) {
            addPrivRow(card, "Accessibility Service",
                    "Can read screen content and simulate user input", 0xFFFF9F1C);
        }
        if (mApp.isNotificationListener) {
            addPrivRow(card, "Notification Listener",
                    "Can read all notifications from every app", 0xFFFF9F1C);
        }
        if (mApp.isVpnService) {
            addPrivRow(card, "VPN Service",
                    "Can intercept and inspect all network traffic", 0xFFFF9F1C);
        }
        return card;
    }

    private void addPrivRow(LinearLayout card, String name, String desc, int color) {
        LinearLayout row = mUi.row(true);
        row.setPadding(0, mUi.dp(4), 0, mUi.dp(4));
        row.addView(mUi.colorDot(color, 8));
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        textCol.addView(mUi.label(name, 13f, color, true));
        textCol.addView(mUi.label(desc, 11f, SecurityUiHelper.CLR_SECONDARY, false));
        row.addView(textCol);
        card.addView(row);
    }

    private View buildUninstallButton() {
        Button btn = mUi.primaryButton(
                mApp.isSuspectedSpyware ? "⚠ Remove Suspected Spyware" : "Uninstall App",
                mApp.isSuspectedSpyware ? 0xFFFF0055 : SecurityUiHelper.CLR_RED);
        btn.setOnClickListener(v -> {
            Uri uri = Uri.parse("package:" + mApp.packageName);
            startActivity(new Intent(Intent.ACTION_DELETE, uri));
        });
        return btn;
    }

    // ── Gauge ─────────────────────────────────────────────────────────────────

    private View buildScoreGauge(int score, int color) {
        final int gaugeColor = color;
        View gauge = new View(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                float cx = getWidth() / 2f, cy = getHeight() / 2f;
                float r  = Math.min(cx, cy) - mUi.dp(4);
                Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
                track.setStyle(Paint.Style.STROKE);
                track.setColor(SecurityUiHelper.CLR_DIVIDER);
                track.setStrokeWidth(mUi.dp(6));
                canvas.drawCircle(cx, cy, r, track);
                if (score > 0) {
                    Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
                    arc.setStyle(Paint.Style.STROKE);
                    arc.setColor(gaugeColor);
                    arc.setStrokeWidth(mUi.dp(6));
                    arc.setStrokeCap(Paint.Cap.ROUND);
                    RectF rf = new RectF(cx - r, cy - r, cx + r, cy + r);
                    canvas.drawArc(rf, -90f, 360f * score / 100f, false, arc);
                }
                Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
                text.setColor(gaugeColor);
                text.setTextSize(mUi.dp(16));
                text.setTypeface(Typeface.DEFAULT_BOLD);
                text.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(String.valueOf(score), cx, cy + mUi.dp(6), text);
            }
        };
        gauge.setLayoutParams(new LinearLayout.LayoutParams(mUi.dp(72), mUi.dp(72)));
        return gauge;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void addPermRow(LinearLayout card, String name, String desc,
                            boolean granted, boolean usedRecently) {
        card.addView(mUi.permissionRow(name, desc, granted, usedRecently));
    }

    private void addInfoRow(LinearLayout card, String key, String value, int valueColor) {
        LinearLayout row = mUi.row(true);
        row.setPadding(0, mUi.dp(5), 0, mUi.dp(5));
        row.addView(mUi.label(key, 13f, SecurityUiHelper.CLR_SECONDARY, false));
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(spacer);
        row.addView(mUi.label(value, 13f, valueColor, false));
        card.addView(row);
    }

    private View addTagGap(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = mUi.dp(6);
        v.setLayoutParams(lp);
        return v;
    }

    private String formatDuration(long ms) {
        long secs = ms / 1000;
        if (secs < 60)   return secs + "s";
        long mins = secs / 60;
        if (mins < 60)   return mins + "m " + (secs % 60) + "s";
        return (mins / 60) + "h " + (mins % 60) + "m";
    }
}
