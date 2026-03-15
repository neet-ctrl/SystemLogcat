package juloo.sysconsole;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SecurityDashboardActivity extends Activity {

    private SecurityUiHelper    mUi;
    private SecurityScanManager mMgr;
    private AppInstallReceiver  mInstallReceiver;

    private LinearLayout mAlertsContainer;
    private LinearLayout mRunningContainer;
    private LinearLayout mElevatedContainer;
    private LinearLayout mSpywareContainer;
    private TextView     mTvRiskLevel;
    private TextView     mTvAppCount;
    private TextView     mTvLastScan;
    private View         mRiskDot;
    private TextView     mTvCameraCount;
    private TextView     mTvMicCount;
    private TextView     mTvLocCount;
    private TextView     mTvRunCount;
    private TextView     mTvHighCount;
    private TextView     mTvSpyCount;
    private LinearLayout mStatsRow;
    private LinearLayout mScanOverlay;
    private TextView     mScanPhaseText;
    private TextView     mScanPercentText;
    private ProgressBar  mScanProgress;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        mMgr = SecurityHub.getManager(this);
        mUi  = new SecurityUiHelper(this);
        buildUi();
        registerInstallReceiver();
        loadCachedData();
        if (mMgr.getCachedApps().isEmpty()) {
            // Auto-scan on first open so the user immediately sees data
            mHandler.postDelayed(this::startScan, 400);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(mInstallReceiver); } catch (Exception ignored) {}
    }

    private void registerInstallReceiver() {
        mInstallReceiver = new AppInstallReceiver(this, new AppInstallReceiver.InstallListener() {
            @Override
            public void onAppInstalled(String pkg, String name,
                                       AppSecurityInfo info, SecurityAlert alert) {
                mHandler.post(() -> {
                    mMgr.getCachedAlerts().add(0, alert);
                    mMgr.getCachedApps().add(0, info);
                    refreshWithData(mMgr.getCachedApps(), mMgr.getCachedAlerts());
                });
            }
            @Override
            public void onAppUninstalled(String pkg, String name) {
                mHandler.post(() -> refreshWithData(
                        mMgr.getCachedApps(), mMgr.getCachedAlerts()));
            }
            @Override
            public void onAppUpdated(String pkg, String name) {}
        });
        try {
            registerReceiver(mInstallReceiver, AppInstallReceiver.makeFilter());
        } catch (Exception ignored) {}
    }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private void buildUi() {
        RelativeLayout frame = new RelativeLayout(this);
        frame.setBackgroundColor(SecurityUiHelper.CLR_BG);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(SecurityUiHelper.CLR_BG);
        RelativeLayout.LayoutParams svLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        svLp.addRule(RelativeLayout.ABOVE, R.id.sec_nav_bar);
        sv.setLayoutParams(svLp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(mUi.dp(16), 0, mUi.dp(16), mUi.dp(16));

        content.addView(buildTopBar());
        content.addView(mUi.spacer(12));
        content.addView(buildStatusCard());
        content.addView(mUi.spacer(12));
        content.addView(buildQuickStats());
        content.addView(mUi.spacer(14));
        content.addView(buildSpywareSection());
        content.addView(mUi.spacer(14));
        content.addView(buildElevatedAppsSection());
        content.addView(mUi.spacer(14));
        content.addView(buildRecentAlertsSection());
        content.addView(mUi.spacer(14));
        content.addView(buildActiveAppsSection());
        content.addView(mUi.spacer(14));
        content.addView(buildExportSection());

        sv.addView(content);
        frame.addView(sv);

        View navBar = buildNavBar();
        navBar.setId(R.id.sec_nav_bar);
        RelativeLayout.LayoutParams navLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, mUi.dp(58));
        navLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        frame.addView(navBar, navLp);

        mScanOverlay = buildScanOverlay();
        mScanOverlay.setVisibility(View.GONE);
        frame.addView(mScanOverlay, new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT));

        setContentView(frame);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, mUi.dp(16), 0, mUi.dp(4));

        LinearLayout titlePart = new LinearLayout(this);
        titlePart.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titlePart.setLayoutParams(tlp);

        titlePart.addView(mUi.label("🛡 Family Security Auditor",
                18f, SecurityUiHelper.CLR_TEXT, true));
        titlePart.addView(mUi.label(
                ShizukuCommandHelper.isAvailable()
                        ? "Shizuku: Deep access active ✓"
                        : "Shizuku: Basic mode",
                11f,
                ShizukuCommandHelper.isAvailable()
                        ? SecurityUiHelper.CLR_TEAL
                        : SecurityUiHelper.CLR_SECONDARY,
                false));
        bar.addView(titlePart);

        // Schedule button
        Button schedule = new Button(this);
        schedule.setText("⏰");
        schedule.setTextSize(16f);
        schedule.setBackground(null);
        schedule.setAllCaps(false);
        schedule.setPadding(mUi.dp(6), mUi.dp(6), mUi.dp(6), mUi.dp(6));
        schedule.setOnClickListener(v -> showScheduleScanDialog());
        bar.addView(schedule);

        // Scan now button
        Button refresh = mUi.primaryButton("⟳ Deep Scan", SecurityUiHelper.CLR_TEAL);
        refresh.setTextSize(12f);
        refresh.setPadding(mUi.dp(14), mUi.dp(8), mUi.dp(14), mUi.dp(8));
        refresh.setOnClickListener(v -> startScan());
        bar.addView(refresh);
        return bar;
    }

    private void showScheduleScanDialog() {
        String[] options = {
            "Scan in 15 minutes",
            "Scan in 30 minutes",
            "Scan in 1 hour",
            "Scan in 2 hours",
            "Scan in 6 hours",
            "Scan in 12 hours"
        };
        long[] delayMs = { 15 * 60_000L, 30 * 60_000L, 60 * 60_000L,
                2 * 3600_000L, 6 * 3600_000L, 12 * 3600_000L };

        new AlertDialog.Builder(this)
            .setTitle("⏰  Schedule Deep Scan")
            .setMessage("Choose when to run the next deep scan automatically:")
            .setItems(options, (d, which) -> {
                scheduleScan(delayMs[which], options[which]);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void scheduleScan(long delayMs, String label) {
        // Use AlarmManager to trigger a broadcast that will start the scan
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ScheduledScanReceiver.class);
        int flags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, flags);
        if (am != null) {
            long triggerAt = System.currentTimeMillis() + delayMs;
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        }
        // Update status card to reflect upcoming scan
        if (mTvLastScan != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            long triggerAt = System.currentTimeMillis() + delayMs;
            mTvLastScan.setText("Scan scheduled: " + sdf.format(new Date(triggerAt)));
            mTvLastScan.setTextColor(SecurityUiHelper.CLR_TEAL);
        }
        android.widget.Toast.makeText(this, "✓ " + label + " scheduled", 
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private View buildStatusCard() {
        LinearLayout card = mUi.card(16, 18);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF1E3A5F);
        cardBg.setCornerRadius(mUi.dp(16));
        card.setBackground(cardBg);

        card.addView(mUi.label("OVERALL RISK LEVEL", 10f,
                SecurityUiHelper.CLR_SECONDARY, true));
        card.addView(mUi.spacer(8));

        LinearLayout riskRow = mUi.row(true);
        mRiskDot = mUi.colorDot(SecurityUiHelper.CLR_SECONDARY, 14);
        riskRow.addView(mRiskDot);
        mTvRiskLevel = mUi.label("Not scanned", 28f, SecurityUiHelper.CLR_TEXT, true);
        riskRow.addView(mTvRiskLevel);
        card.addView(riskRow);
        card.addView(mUi.spacer(10));

        mTvAppCount = mUi.label("Tap Deep Scan to analyze your device",
                12f, SecurityUiHelper.CLR_SECONDARY, false);
        card.addView(mTvAppCount);

        mTvLastScan = mUi.label("", 11f, SecurityUiHelper.CLR_SECONDARY, false);
        card.addView(mTvLastScan);
        return card;
    }

    private View buildQuickStats() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(mUi.sectionHeader("QUICK STATS"));

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        mStatsRow = new LinearLayout(this);
        mStatsRow.setOrientation(LinearLayout.HORIZONTAL);
        mStatsRow.setPadding(0, mUi.dp(4), mUi.dp(16), mUi.dp(4));

        mTvCameraCount = addStatCard(mStatsRow, "—", "Camera\nApps",   SecurityUiHelper.CLR_ORANGE);
        mTvMicCount    = addStatCard(mStatsRow, "—", "Mic\nApps",       SecurityUiHelper.CLR_ORANGE);
        mTvLocCount    = addStatCard(mStatsRow, "—", "Location\nApps",  SecurityUiHelper.CLR_TEAL);
        mTvRunCount    = addStatCard(mStatsRow, "—", "Running\nApps",   SecurityUiHelper.CLR_GREEN);
        mTvHighCount   = addStatCard(mStatsRow, "—", "High\nRisk",      SecurityUiHelper.CLR_RED);
        mTvSpyCount    = addStatCard(mStatsRow, "—", "Spy/Threat\nApps",0xFFFF0055);

        hsv.addView(mStatsRow);
        section.addView(hsv);
        return section;
    }

    private TextView addStatCard(LinearLayout row, String value, String label, int color) {
        LinearLayout card = mUi.statCard(value, label, color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                mUi.dp(90), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = mUi.dp(10);
        row.addView(card, lp);
        return (TextView) card.getChildAt(0);
    }

    private View buildSpywareSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(mUi.sectionHeader("⚠ SPYWARE & THREAT DETECTION"));

        mSpywareContainer = new LinearLayout(this);
        mSpywareContainer.setOrientation(LinearLayout.VERTICAL);

        LinearLayout placeholderCard = mUi.card();
        placeholderCard.addView(mUi.label(
                "Run a Deep Scan to check for spyware, stalkerware, and\nsuspicious apps on this device.",
                12f, SecurityUiHelper.CLR_SECONDARY, false));
        mSpywareContainer.addView(placeholderCard);
        section.addView(mSpywareContainer);
        return section;
    }

    private View buildElevatedAppsSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(mUi.sectionHeader("ELEVATED PRIVILEGE APPS"));

        mElevatedContainer = new LinearLayout(this);
        mElevatedContainer.setOrientation(LinearLayout.VERTICAL);

        LinearLayout placeholderCard = mUi.card();
        placeholderCard.addView(mUi.label(
                "Device admins, accessibility services, VPN and notification listener\napps will appear here.",
                12f, SecurityUiHelper.CLR_SECONDARY, false));
        mElevatedContainer.addView(placeholderCard);
        section.addView(mElevatedContainer);
        return section;
    }

    private View buildRecentAlertsSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout headerRow = mUi.row(true);
        headerRow.addView(mUi.sectionHeader("RECENT ALERTS"));
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(spacer);

        Button seeAll = new Button(this);
        seeAll.setText("See all →");
        seeAll.setTextSize(11f);
        seeAll.setTextColor(SecurityUiHelper.CLR_TEAL);
        seeAll.setBackground(null);
        seeAll.setAllCaps(false);
        seeAll.setOnClickListener(v ->
                startActivity(new Intent(this, AlertsActivity.class)));
        headerRow.addView(seeAll);
        section.addView(headerRow);

        mAlertsContainer = new LinearLayout(this);
        mAlertsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout placeholder = mUi.card();
        placeholder.addView(mUi.label("Run a scan to see alerts.",
                13f, SecurityUiHelper.CLR_SECONDARY, false));
        mAlertsContainer.addView(placeholder);
        section.addView(mAlertsContainer);
        return section;
    }

    private View buildActiveAppsSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(mUi.sectionHeader("CURRENTLY RUNNING APPS"));

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        mRunningContainer = new LinearLayout(this);
        mRunningContainer.setOrientation(LinearLayout.HORIZONTAL);
        mRunningContainer.setPadding(0, mUi.dp(4), mUi.dp(8), mUi.dp(4));
        mRunningContainer.addView(mUi.label("Scan to see running apps.",
                12f, SecurityUiHelper.CLR_SECONDARY, false));

        hsv.addView(mRunningContainer);
        section.addView(hsv);
        return section;
    }

    private View buildExportSection() {
        LinearLayout card = mUi.card(14, 16);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF162030);
        bg.setCornerRadius(mUi.dp(14));
        card.setBackground(bg);

        card.addView(mUi.label("Export Security Report", 14f,
                SecurityUiHelper.CLR_TEXT, true));
        card.addView(mUi.spacer(4));
        card.addView(mUi.label(
                "Share a full audit report (device info, all risks, alerts, threat detections)\nwith tech support or save for reference.",
                12f, SecurityUiHelper.CLR_SECONDARY, false));
        card.addView(mUi.spacer(12));

        Button exportBtn = mUi.primaryButton("📋  Export & Share Report", SecurityUiHelper.CLR_TEAL);
        exportBtn.setOnClickListener(v -> {
            List<AppSecurityInfo> apps   = mMgr.getCachedApps();
            List<SecurityAlert>   alerts = mMgr.getCachedAlerts();
            List<DeviceSecurityHelper.ElevatedApp> elevated = mMgr.getElevatedApps();
            if (apps.isEmpty()) {
                exportBtn.setText("Run a scan first!");
            } else {
                SecurityReportExporter.shareReport(this, apps, alerts, elevated);
            }
        });
        card.addView(exportBtn);
        return card;
    }

    private View buildNavBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(SecurityUiHelper.CLR_NAV_BG);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        String[][] items = {
            {"🛡", "Dashboard"}, {"📦", "Apps"},
            {"📡", "Monitor"},   {"⚙",  "Settings"}
        };
        Class<?>[] activities = {
            null,
            AppsListActivity.class,
            MonitorActivity.class,
            SecuritySettingsActivity.class
        };

        for (int i = 0; i < items.length; i++) {
            final Class<?> dest = activities[i];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, mUi.dp(6), 0, mUi.dp(6));

            boolean isActive = (i == 0);
            int color = isActive ? SecurityUiHelper.CLR_TEAL : SecurityUiHelper.CLR_SECONDARY;

            TextView icon = mUi.label(items[i][0], 16f, color, false);
            icon.setGravity(Gravity.CENTER);
            TextView lbl  = mUi.label(items[i][1], 9f, color, isActive);
            lbl.setGravity(Gravity.CENTER);

            item.addView(icon);
            item.addView(lbl);
            item.setOnClickListener(v -> {
                if (dest != null) startActivity(new Intent(this, dest));
            });
            item.setClickable(true);
            item.setFocusable(true);

            bar.addView(item, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }
        return bar;
    }

    private LinearLayout buildScanOverlay() {
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(0xF00A1622);

        // Center card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(mUi.dp(32), mUi.dp(32), mUi.dp(32), mUi.dp(32));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF0F1E2D);
        cardBg.setCornerRadius(mUi.dp(20));
        cardBg.setStroke(mUi.dp(1), SecurityUiHelper.CLR_TEAL & 0x55FFFFFF | 0xFF000000);
        card.setBackground(cardBg);

        TextView icon = mUi.label("🔍", 44f, SecurityUiHelper.CLR_TEAL, false);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon);
        card.addView(mUi.spacer(14));

        TextView title = mUi.label("Deep Security Scan", 20f, SecurityUiHelper.CLR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title);
        card.addView(mUi.spacer(4));

        // Phase description
        mScanPhaseText = mUi.label("Initializing…", 13f, SecurityUiHelper.CLR_SECONDARY, false);
        mScanPhaseText.setGravity(Gravity.CENTER);
        card.addView(mScanPhaseText);
        card.addView(mUi.spacer(20));

        // Progress bar + percentage row
        LinearLayout progressRow = mUi.row(true);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);

        mScanProgress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        mScanProgress.setIndeterminate(false);
        mScanProgress.setMax(100);
        mScanProgress.setProgress(0);
        if (Build.VERSION.SDK_INT >= 21) {
            mScanProgress.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(SecurityUiHelper.CLR_TEAL));
            mScanProgress.setProgressBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF1E3A5F));
        }
        LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(0, mUi.dp(8), 1f);
        mScanProgress.setLayoutParams(pblp);
        progressRow.addView(mScanProgress);

        mScanPercentText = mUi.label("  0%", 13f, SecurityUiHelper.CLR_TEAL, true);
        progressRow.addView(mScanPercentText);
        card.addView(progressRow);
        card.addView(mUi.spacer(16));

        // 9-stage scan phases reference
        LinearLayout stagesGrid = new LinearLayout(this);
        stagesGrid.setOrientation(LinearLayout.VERTICAL);
        String[][] phases = {
            {"📦", "Package List"},    {"🔑", "AppOps Usage"},
            {"📊", "Usage Stats"},     {"⚙",  "Processes"},
            {"🌐", "Network Data"},    {"🛡",  "Shizuku Deep Scan"},
            {"🕵", "Spy Detection"},   {"🏦",  "Banking Risk"},
            {"⬆",  "Privilege Check"}
        };
        LinearLayout row1 = mUi.row(false); row1.setGravity(Gravity.CENTER);
        LinearLayout row2 = mUi.row(false); row2.setGravity(Gravity.CENTER);
        LinearLayout row3 = mUi.row(false); row3.setGravity(Gravity.CENTER);
        for (int i = 0; i < phases.length; i++) {
            LinearLayout ph = buildPhaseChip(phases[i][0], phases[i][1]);
            if (i < 3)      row1.addView(ph);
            else if (i < 6) row2.addView(ph);
            else            row3.addView(ph);
        }
        stagesGrid.addView(row1);
        stagesGrid.addView(mUi.spacer(6));
        stagesGrid.addView(row2);
        stagesGrid.addView(mUi.spacer(6));
        stagesGrid.addView(row3);
        card.addView(stagesGrid);
        card.addView(mUi.spacer(18));

        // Mode label
        boolean shizukuOk = ShizukuCommandHelper.isAvailable();
        String modeText = shizukuOk
                ? "✓ Shizuku: Full depth — AppOps, processes, granted permissions"
                : "⚠ Basic mode — connect Shizuku for deeper analysis";
        TextView modeLbl = mUi.label(modeText, 11f,
                shizukuOk ? SecurityUiHelper.CLR_TEAL : SecurityUiHelper.CLR_ORANGE, false);
        modeLbl.setGravity(Gravity.CENTER);
        card.addView(modeLbl);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                mUi.dp(300), LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        overlay.addView(card, cardLp);
        return overlay;
    }

    private LinearLayout buildPhaseChip(String emoji, String label) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(mUi.dp(8), mUi.dp(6), mUi.dp(8), mUi.dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = mUi.dp(6);
        chip.setLayoutParams(lp);

        TextView te = mUi.label(emoji, 14f, SecurityUiHelper.CLR_SECONDARY, false);
        te.setGravity(Gravity.CENTER);
        chip.addView(te);

        TextView tl = mUi.label(label, 8f, 0xFF475569, false);
        tl.setGravity(Gravity.CENTER);
        chip.addView(tl);
        return chip;
    }

    // ── Scan ──────────────────────────────────────────────────────────────────

    private void startScan() {
        mScanOverlay.setVisibility(View.VISIBLE);
        if (mScanPhaseText    != null) mScanPhaseText.setText("Initializing…");
        if (mScanProgress     != null) mScanProgress.setProgress(0);
        if (mScanPercentText  != null) mScanPercentText.setText("  0%");

        mMgr.scanAsync(new SecurityScanManager.ScanCallback() {
            @Override
            public void onProgress(int current, int total, String phase) {
                mHandler.post(() -> {
                    if (mScanPhaseText   != null) mScanPhaseText.setText(phase);
                    if (mScanProgress    != null) mScanProgress.setProgress(current);
                    if (mScanPercentText != null)
                        mScanPercentText.setText("  " + current + "%");
                });
            }
            @Override
            public void onComplete(List<AppSecurityInfo> apps, List<SecurityAlert> alerts) {
                mHandler.post(() -> {
                    if (mScanProgress    != null) mScanProgress.setProgress(100);
                    if (mScanPercentText != null) mScanPercentText.setText("  100%");
                    mHandler.postDelayed(() -> {
                        mScanOverlay.setVisibility(View.GONE);
                        if (mTvLastScan != null) mTvLastScan.setTextColor(
                                SecurityUiHelper.CLR_SECONDARY);
                        refreshWithData(apps, alerts);
                    }, 400);
                });
            }
        });
    }

    private void loadCachedData() {
        if (!mMgr.getCachedApps().isEmpty()) {
            refreshWithData(mMgr.getCachedApps(), mMgr.getCachedAlerts());
        }
    }

    private void refreshWithData(List<AppSecurityInfo> apps, List<SecurityAlert> alerts) {
        if (apps.isEmpty()) return;

        // Overall risk level
        int highCount = SecurityScanManager.countWithPerm(apps, "high");
        int medCount  = SecurityScanManager.countWithPerm(apps, "medium");
        int spyCount  = SecurityScanManager.countWithPerm(apps, "spyware");

        String riskLabel;
        int    riskColor;
        if (spyCount > 0 || highCount > 0) {
            riskLabel = spyCount > 0 ? "CRITICAL" : "HIGH";
            riskColor = spyCount > 0 ? 0xFFFF0055 : SecurityUiHelper.CLR_RED;
        } else if (medCount > 0) {
            riskLabel = "MEDIUM";
            riskColor = SecurityUiHelper.CLR_ORANGE;
        } else {
            riskLabel = "LOW";
            riskColor = SecurityUiHelper.CLR_GREEN;
        }

        mTvRiskLevel.setText(riskLabel);
        mTvRiskLevel.setTextColor(riskColor);
        ((GradientDrawable) mRiskDot.getBackground()).setColor(riskColor);
        mTvAppCount.setText(apps.size() + " apps analyzed");

        if (mMgr.getLastScanTime() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
            mTvLastScan.setText("Last scan: " + sdf.format(new Date(mMgr.getLastScanTime())));
        }

        // Stats
        mTvCameraCount.setText(String.valueOf(SecurityScanManager.countWithPerm(apps, "camera")));
        mTvMicCount.setText(String.valueOf(SecurityScanManager.countWithPerm(apps, "mic")));
        mTvLocCount.setText(String.valueOf(SecurityScanManager.countWithPerm(apps, "location")));
        mTvRunCount.setText(String.valueOf(SecurityScanManager.countWithPerm(apps, "running")));
        mTvHighCount.setText(String.valueOf(highCount));
        mTvSpyCount.setText(String.valueOf(spyCount));

        refreshSpywareSection(apps);
        refreshElevatedAppsSection();
        refreshAlerts(alerts);
        refreshRunningApps(apps);
    }

    // ── Section refreshers ────────────────────────────────────────────────────

    private void refreshSpywareSection(List<AppSecurityInfo> apps) {
        mSpywareContainer.removeAllViews();
        LinearLayout card = mUi.card();

        boolean any = false;
        for (AppSecurityInfo a : apps) {
            if (a.threatLevel <= SpyDetectionEngine.THREAT_NONE) continue;
            if (any) card.addView(mUi.divider());
            card.addView(buildThreatRow(a));
            any = true;
        }

        if (!any) {
            LinearLayout row = mUi.row(true);
            row.addView(mUi.colorDot(SecurityUiHelper.CLR_GREEN, 8));
            row.addView(mUi.label("✓  No spyware or stalkerware detected on this device.",
                    13f, SecurityUiHelper.CLR_GREEN, false));
            card.addView(row);
        }
        mSpywareContainer.addView(card);
    }

    private View buildThreatRow(AppSecurityInfo a) {
        LinearLayout row = mUi.row(true);
        row.setPadding(0, mUi.dp(6), 0, mUi.dp(6));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            Intent i = new Intent(this, AppDetailsActivity.class);
            i.putExtra(AppDetailsActivity.EXTRA_PKG, a.packageName);
            startActivity(i);
        });

        int color = SpyDetectionEngine.threatColor(a.threatLevel);
        row.addView(mUi.colorDot(color, 10));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(lp);
        textCol.addView(mUi.label(a.appName, 13f, SecurityUiHelper.CLR_TEXT, true));
        textCol.addView(mUi.label(a.threatReason, 11f, SecurityUiHelper.CLR_SECONDARY, false));
        row.addView(textCol);

        row.addView(mUi.badge(SpyDetectionEngine.threatLevelName(a.threatLevel),
                color & 0x33FFFFFF | 0xFF000000, color, 20));
        return row;
    }

    private void refreshElevatedAppsSection() {
        mElevatedContainer.removeAllViews();
        List<DeviceSecurityHelper.ElevatedApp> elevated = mMgr.getElevatedApps();

        LinearLayout card = mUi.card();
        if (elevated == null || elevated.isEmpty()) {
            LinearLayout row = mUi.row(true);
            row.addView(mUi.colorDot(SecurityUiHelper.CLR_GREEN, 8));
            row.addView(mUi.label(
                    "✓  No device admins, VPN, or elevated-access apps found.",
                    13f, SecurityUiHelper.CLR_GREEN, false));
            card.addView(row);
        } else {
            boolean first = true;
            for (DeviceSecurityHelper.ElevatedApp ea : elevated) {
                if (!first) card.addView(mUi.divider());
                card.addView(buildElevatedRow(ea));
                first = false;
            }
        }
        mElevatedContainer.addView(card);
    }

    private View buildElevatedRow(DeviceSecurityHelper.ElevatedApp ea) {
        LinearLayout row = mUi.row(true);
        row.setPadding(0, mUi.dp(6), 0, mUi.dp(6));

        row.addView(mUi.colorDot(ea.riskColor, 10));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(lp);
        textCol.addView(mUi.label(ea.appName, 13f, SecurityUiHelper.CLR_TEXT, true));
        textCol.addView(mUi.label(ea.description, 11f, SecurityUiHelper.CLR_SECONDARY, false));
        row.addView(textCol);

        row.addView(mUi.badge(ea.privilege.split(" ")[0],
                ea.riskColor & 0x33FFFFFF | 0xFF000000, ea.riskColor, 20));
        return row;
    }

    private void refreshAlerts(List<SecurityAlert> alerts) {
        mAlertsContainer.removeAllViews();
        LinearLayout card = mUi.card();

        if (alerts == null || alerts.isEmpty()) {
            card.addView(mUi.label("✅  No alerts detected — device looks clean.",
                    13f, SecurityUiHelper.CLR_GREEN, false));
            mAlertsContainer.addView(card);
            return;
        }

        int shown = 0;
        for (SecurityAlert alert : alerts) {
            if (shown >= 5) break;
            if (shown > 0) card.addView(mUi.divider());
            card.addView(buildAlertRow(alert));
            shown++;
        }
        mAlertsContainer.addView(card);
    }

    private View buildAlertRow(SecurityAlert alert) {
        LinearLayout row = mUi.row(true);
        row.setPadding(0, mUi.dp(6), 0, mUi.dp(6));
        row.setClickable(true);
        row.setOnClickListener(v ->
                startActivity(new Intent(this, AlertsActivity.class)));

        row.addView(mUi.colorDot(alert.severityColor(), 8));
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        textCol.addView(mUi.label(alert.title, 13f, SecurityUiHelper.CLR_TEXT, true));
        textCol.addView(mUi.label(alert.description, 11f,
                SecurityUiHelper.CLR_SECONDARY, false));
        row.addView(textCol);
        row.addView(mUi.label("›", 18f, SecurityUiHelper.CLR_SECONDARY, false));
        return row;
    }

    private void refreshRunningApps(List<AppSecurityInfo> apps) {
        mRunningContainer.removeAllViews();
        int shown = 0;
        for (AppSecurityInfo a : apps) {
            if (!a.isRunning) continue;
            if (shown >= 12) break;
            mRunningContainer.addView(buildRunningChip(a));
            shown++;
        }
        if (shown == 0) {
            mRunningContainer.addView(mUi.label(
                    "No running process data. Shizuku enables full process list.",
                    12f, SecurityUiHelper.CLR_SECONDARY, false));
        }
    }

    private View buildRunningChip(AppSecurityInfo a) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(mUi.dp(10), mUi.dp(10), mUi.dp(10), mUi.dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(a.isSuspectedSpyware ? 0xFF2D1A1A : SecurityUiHelper.CLR_CARD);
        bg.setCornerRadius(mUi.dp(12));
        if (a.isSuspectedSpyware) bg.setStroke(mUi.dp(1), 0xFFE63946);
        chip.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                mUi.dp(72), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = mUi.dp(10);
        chip.setLayoutParams(lp);
        chip.setClickable(true);
        chip.setOnClickListener(v -> {
            Intent i = new Intent(this, AppDetailsActivity.class);
            i.putExtra(AppDetailsActivity.EXTRA_PKG, a.packageName);
            startActivity(i);
        });

        if (a.icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            iv.setLayoutParams(new LinearLayout.LayoutParams(mUi.dp(38), mUi.dp(38)));
            chip.addView(iv);
        } else {
            chip.addView(mUi.label("📦", 22f, SecurityUiHelper.CLR_SECONDARY, false));
        }

        chip.addView(mUi.spacer(4));
        String name = a.appName.length() > 9 ? a.appName.substring(0, 8) + "…" : a.appName;
        TextView tvName = mUi.label(name, 9f, SecurityUiHelper.CLR_TEXT, false);
        tvName.setGravity(Gravity.CENTER);
        chip.addView(tvName);

        chip.addView(mUi.spacer(3));
        int badgeColor = a.isSuspectedSpyware ? 0xFFE63946 : SecurityUiHelper.CLR_TEAL;
        chip.addView(mUi.badge(a.isSuspectedSpyware ? "⚠ Spy" : "Live",
                badgeColor & 0x44FFFFFF | 0xFF000000, badgeColor, 20));
        return chip;
    }
}
