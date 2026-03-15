package juloo.sysconsole;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.pm.PackageManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MonitorActivity extends Activity {

    private SecurityUiHelper mUi;
    private SecurityScanManager mMgr;
    private LinearLayout mFeedContainer;
    private LinearLayout mRunningContainer;
    private TextView mCameraStatus;
    private TextView mMicStatus;
    private TextView mLocationStatus;
    private View mCameraDot;
    private View mMicDot;
    private View mLocationDot;
    private boolean mRunning = true;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat mSdf =
            new SimpleDateFormat("h:mm:ss a", Locale.getDefault());
    private final SimpleDateFormat mSdfFull =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        mMgr = SecurityHub.getManager(this);
        mUi  = new SecurityUiHelper(this);
        buildUi();
        populateRunningApps();
        loadInitialFeed();
        startPolling();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRunning = false;
        mHandler.removeCallbacksAndMessages(null);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SecurityUiHelper.CLR_BG);

        root.addView(buildTopBar());

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(SecurityUiHelper.CLR_BG);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(mUi.dp(16), mUi.dp(12), mUi.dp(16), mUi.dp(32));

        content.addView(buildSensorStatusSection());
        content.addView(mUi.spacer(16));
        content.addView(buildRunningAppsSection());
        content.addView(mUi.spacer(16));
        content.addView(buildLiveFeedSection());

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

        TextView title = mUi.label("Real-Time Monitor", 17f,
                SecurityUiHelper.CLR_TEXT, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(title, tlp);

        View liveDot = new View(this);
        GradientDrawable ld = new GradientDrawable();
        ld.setShape(GradientDrawable.OVAL);
        ld.setColor(SecurityUiHelper.CLR_RED);
        liveDot.setBackground(ld);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                mUi.dp(8), mUi.dp(8));
        dotLp.rightMargin = mUi.dp(6);
        dotLp.gravity = Gravity.CENTER_VERTICAL;
        bar.addView(liveDot, dotLp);

        bar.addView(mUi.label("LIVE", 11f, SecurityUiHelper.CLR_RED, true));
        return bar;
    }

    private View buildSensorStatusSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(mUi.sectionHeader("SENSOR STATUS"));

        LinearLayout row = mUi.row(false);
        row.setGravity(Gravity.FILL_HORIZONTAL);

        LinearLayout camCard = buildSensorCard("Camera", "📷");
        mCameraDot   = camCard.findViewWithTag("dot");
        mCameraStatus= (TextView) camCard.findViewWithTag("status");
        LinearLayout micCard = buildSensorCard("Microphone", "🎙");
        mMicDot     = micCard.findViewWithTag("dot");
        mMicStatus  = (TextView) micCard.findViewWithTag("status");
        LinearLayout locCard = buildSensorCard("Location", "📍");
        mLocationDot    = locCard.findViewWithTag("dot");
        mLocationStatus = (TextView) locCard.findViewWithTag("status");

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cp.rightMargin = mUi.dp(8);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mp.rightMargin = mUi.dp(8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

        row.addView(camCard, cp);
        row.addView(micCard, mp);
        row.addView(locCard, lp);
        section.addView(row);
        return section;
    }

    private LinearLayout buildSensorCard(String name, String emoji) {
        LinearLayout card = mUi.card(12, 12);
        card.setGravity(Gravity.CENTER);

        TextView tvEmoji = mUi.label(emoji, 20f, SecurityUiHelper.CLR_TEXT, false);
        tvEmoji.setGravity(Gravity.CENTER);
        card.addView(tvEmoji);

        card.addView(mUi.spacer(4));

        TextView tvName = mUi.label(name, 10f, SecurityUiHelper.CLR_SECONDARY, false);
        tvName.setGravity(Gravity.CENTER);
        card.addView(tvName);

        card.addView(mUi.spacer(6));

        View dot = new View(this);
        dot.setTag("dot");
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(SecurityUiHelper.CLR_GREEN);
        dot.setBackground(d);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                mUi.dp(10), mUi.dp(10));
        dlp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(dot, dlp);

        card.addView(mUi.spacer(4));

        TextView status = mUi.label("Idle", 10f, SecurityUiHelper.CLR_GREEN, false);
        status.setTag("status");
        status.setGravity(Gravity.CENTER);
        card.addView(status);

        return card;
    }

    private View buildRunningAppsSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(mUi.sectionHeader("CURRENTLY RUNNING APPS"));

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);

        mRunningContainer = new LinearLayout(this);
        mRunningContainer.setOrientation(LinearLayout.HORIZONTAL);
        mRunningContainer.setPadding(0, mUi.dp(4), mUi.dp(8), mUi.dp(4));

        hsv.addView(mRunningContainer);
        section.addView(hsv);
        return section;
    }

    private View buildLiveFeedSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(mUi.sectionHeader("LIVE FEED"));

        LinearLayout feedCard = mUi.card(12, 12);
        mFeedContainer = new LinearLayout(this);
        mFeedContainer.setOrientation(LinearLayout.VERTICAL);
        feedCard.addView(mFeedContainer);
        section.addView(feedCard);
        return section;
    }

    private void populateRunningApps() {
        List<AppSecurityInfo> apps = mMgr.getCachedApps();
        mRunningContainer.removeAllViews();
        int shown = 0;
        for (AppSecurityInfo a : apps) {
            if (!a.isRunning) continue;
            if (shown >= 8) break;
            mRunningContainer.addView(buildRunningAppChip(a));
            shown++;
        }
        final int shownFinal = shown;
        if (shownFinal == 0) {
            mRunningContainer.addView(mUi.label(
                    "Loading…", 12f, SecurityUiHelper.CLR_SECONDARY, false));
        }
        // Supplement with Shizuku foreground service list in the background
        if (ShizukuCommandHelper.isAvailable()) {
            new Thread(() -> {
                List<String> fgPkgs = ShizukuCommandHelper.getForegroundServicePackages();
                if (!fgPkgs.isEmpty()) {
                    mHandler.post(() -> updateRunningAppsFromShizuku(fgPkgs));
                } else if (shownFinal == 0) {
                    mHandler.post(() -> {
                        mRunningContainer.removeAllViews();
                        mRunningContainer.addView(mUi.label(
                                "No foreground services detected.",
                                12f, SecurityUiHelper.CLR_SECONDARY, false));
                    });
                }
            }).start();
        } else if (shown == 0) {
            mRunningContainer.removeAllViews();
            mRunningContainer.addView(mUi.label(
                    "Run a scan to see running apps. Enable Shizuku for live list.",
                    12f, SecurityUiHelper.CLR_SECONDARY, false));
        }
    }

    private View buildRunningAppChip(AppSecurityInfo a) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(mUi.dp(8), mUi.dp(8), mUi.dp(8), mUi.dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SecurityUiHelper.CLR_CARD2);
        bg.setCornerRadius(mUi.dp(10));
        chip.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                mUi.dp(64), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = mUi.dp(8);
        chip.setLayoutParams(lp);

        if (a.icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            iv.setLayoutParams(new LinearLayout.LayoutParams(mUi.dp(36), mUi.dp(36)));
            chip.addView(iv);
        } else {
            chip.addView(mUi.label("📦", 22f, SecurityUiHelper.CLR_SECONDARY, false));
        }

        chip.addView(mUi.spacer(4));
        String safeAppName = a.appName != null ? a.appName : a.packageName;
        TextView tvName = mUi.label(
                safeAppName.length() > 8 ? safeAppName.substring(0, 7) + "…" : safeAppName,
                9f, SecurityUiHelper.CLR_TEXT, false);
        tvName.setGravity(Gravity.CENTER);
        chip.addView(tvName);

        chip.addView(mUi.spacer(3));
        View liveBadge = mUi.badge("Live", SecurityUiHelper.CLR_TEAL & 0x33FFFFFF,
                SecurityUiHelper.CLR_TEAL, 20);
        chip.addView(liveBadge);

        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> {
            Intent i = new Intent(this, AppDetailsActivity.class);
            i.putExtra(AppDetailsActivity.EXTRA_PKG, a.packageName);
            startActivity(i);
        });

        return chip;
    }

    /**
     * Loads real sensor events from Shizuku AppOps (privileged) or falls back
     * to cached app data.  Runs on a background thread to avoid blocking UI.
     */
    private void loadInitialFeed() {
        addFeedEntry("📡 Monitor", "Loading sensor history…", SecurityUiHelper.CLR_TEAL);
        new Thread(() -> {
            List<ShizukuCommandHelper.SensorEvent> events = new ArrayList<>();
            if (ShizukuCommandHelper.isAvailable()) {
                // 60-minute window via Shizuku AppOps dump
                events = ShizukuCommandHelper.getRecentSensorEvents(60 * 60 * 1000L);
            }
            // Build fallback entries from cached app data if Shizuku is unavailable
            if (events.isEmpty()) {
                List<AppSecurityInfo> apps = mMgr.getCachedApps();
                for (AppSecurityInfo a : apps) {
                    if (a.cameraUsedRecently)
                        events.add(new ShizukuCommandHelper.SensorEvent(
                                a.packageName, "CAMERA", "📷", System.currentTimeMillis()));
                    if (a.micUsedRecently)
                        events.add(new ShizukuCommandHelper.SensorEvent(
                                a.packageName, "MIC", "🎙", System.currentTimeMillis()));
                    if (a.locationUsedRecently)
                        events.add(new ShizukuCommandHelper.SensorEvent(
                                a.packageName, "LOCATION", "📍", System.currentTimeMillis()));
                }
            }
            final List<ShizukuCommandHelper.SensorEvent> finalEvents = events;
            mHandler.post(() -> {
                mFeedContainer.removeAllViews();
                if (finalEvents.isEmpty()) {
                    addFeedEntry("✅ Monitor",
                            ShizukuCommandHelper.isAvailable()
                                    ? "No sensor activity in last 60 minutes"
                                    : "Run a scan first to see activity here",
                            SecurityUiHelper.CLR_GREEN);
                    return;
                }
                for (ShizukuCommandHelper.SensorEvent e : finalEvents) {
                    String label = resolveAppName(e.packageName);
                    String desc  = sensorDesc(e.sensorType);
                    int color    = sensorColor(e.sensorType);
                    addFeedEntryWithTime(e.emoji + " " + label, desc, color, e.timestamp);
                }
            });
        }).start();
    }

    private String resolveAppName(String pkg) {
        // Try to get a friendly name from cached apps first
        for (AppSecurityInfo a : mMgr.getCachedApps()) {
            if (a.packageName.equals(pkg)) return a.appName;
        }
        // Fallback: try PackageManager
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg.contains(".") ? pkg.substring(pkg.lastIndexOf('.') + 1) : pkg;
        }
    }

    private String sensorDesc(String type) {
        switch (type) {
            case "CAMERA":   return "Camera accessed";
            case "MIC":      return "Microphone accessed";
            case "LOCATION": return "Location accessed";
            case "SMS":      return "SMS read";
            case "CONTACTS": return "Contacts read";
            case "CALL_LOG": return "Call log read";
            default:         return "Sensor accessed";
        }
    }

    private int sensorColor(String type) {
        switch (type) {
            case "CAMERA":   return SecurityUiHelper.CLR_ORANGE;
            case "MIC":      return SecurityUiHelper.CLR_RED;
            case "LOCATION": return SecurityUiHelper.CLR_SECONDARY;
            case "SMS":
            case "CONTACTS":
            case "CALL_LOG": return 0xFFBB86FC;
            default:         return SecurityUiHelper.CLR_TEAL;
        }
    }

    private void addFeedEntry(String source, String msg, int color) {
        LinearLayout row = mUi.row(true);
        row.setPadding(0, mUi.dp(6), 0, mUi.dp(6));

        View dot = mUi.colorDot(color, 6);
        row.addView(dot);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(lp);
        textCol.addView(mUi.label(source, 12f, SecurityUiHelper.CLR_TEXT, true));
        textCol.addView(mUi.label(msg, 11f, SecurityUiHelper.CLR_SECONDARY, false));
        row.addView(textCol);

        row.addView(mUi.label(mSdf.format(new Date()), 10f,
                SecurityUiHelper.CLR_SECONDARY, false));

        if (mFeedContainer.getChildCount() > 0) {
            mFeedContainer.addView(mUi.divider(), 0);
        }
        mFeedContainer.addView(row, 0);

        while (mFeedContainer.getChildCount() > 30) {
            mFeedContainer.removeViewAt(mFeedContainer.getChildCount() - 1);
        }
    }

    // Track last seen events to only show NEW ones in the live feed
    private long mLastPollTime = System.currentTimeMillis();

    private void startPolling() {
        // Status update every 5s
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!mRunning) return;
                updateSensorStatus();
                mHandler.postDelayed(this, 5000);
            }
        }, 5000);

        // Live feed refresh every 30s (Shizuku AppOps) on background thread
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!mRunning) return;
                final long since = mLastPollTime;
                mLastPollTime = System.currentTimeMillis();
                new Thread(() -> {
                    List<ShizukuCommandHelper.SensorEvent> newEvents = new ArrayList<>();
                    if (ShizukuCommandHelper.isAvailable()) {
                        List<ShizukuCommandHelper.SensorEvent> all =
                                ShizukuCommandHelper.getRecentSensorEvents(30 * 60 * 1000L);
                        for (ShizukuCommandHelper.SensorEvent e : all) {
                            if (e.timestamp > since) newEvents.add(e);
                        }
                    }
                    // Also refresh running apps from Shizuku process list
                    List<String> fgPkgs = ShizukuCommandHelper.isAvailable()
                            ? ShizukuCommandHelper.getForegroundServicePackages()
                            : new ArrayList<>();
                    mHandler.post(() -> {
                        for (ShizukuCommandHelper.SensorEvent e : newEvents) {
                            String label = resolveAppName(e.packageName);
                            addFeedEntryWithTime(e.emoji + " " + label,
                                    sensorDesc(e.sensorType), sensorColor(e.sensorType),
                                    e.timestamp);
                        }
                        if (!fgPkgs.isEmpty()) updateRunningAppsFromShizuku(fgPkgs);
                    });
                }).start();
                mHandler.postDelayed(this, 30_000);
            }
        }, 30_000);
    }

    private void updateSensorStatus() {
        new Thread(() -> {
            boolean camActive = false, micActive = false, locActive = false;
            String camApp = "", micApp = "", locApp = "";

            if (ShizukuCommandHelper.isAvailable()) {
                // Use real-time Shizuku AppOps (5-minute window = "currently in use")
                List<ShizukuCommandHelper.SensorEvent> recent =
                        ShizukuCommandHelper.getRecentSensorEvents(5 * 60 * 1000L);
                for (ShizukuCommandHelper.SensorEvent e : recent) {
                    String name = resolveAppName(e.packageName);
                    switch (e.sensorType) {
                        case "CAMERA":   camActive = true; if (camApp.isEmpty()) camApp = name; break;
                        case "MIC":      micActive = true; if (micApp.isEmpty()) micApp = name; break;
                        case "LOCATION": locActive = true; if (locApp.isEmpty()) locApp = name; break;
                    }
                }
            } else {
                // Fallback: use cached scan data
                for (AppSecurityInfo a : mMgr.getCachedApps()) {
                    if (a.isRunning && a.cameraUsedRecently)   { camActive = true; if (camApp.isEmpty()) camApp = a.appName; }
                    if (a.isRunning && a.micUsedRecently)      { micActive = true; if (micApp.isEmpty()) micApp = a.appName; }
                    if (a.isRunning && a.locationUsedRecently) { locActive = true; if (locApp.isEmpty()) locApp = a.appName; }
                }
            }

            final boolean fc = camActive, fm = micActive, fl = locActive;
            final String fca = camApp, fma = micApp, fla = locApp;
            mHandler.post(() -> {
                setSensorState(mCameraDot,   mCameraStatus,   fc, fc ? fca : "Idle");
                setSensorState(mMicDot,      mMicStatus,      fm, fm ? fma : "Idle");
                setSensorState(mLocationDot, mLocationStatus, fl, fl ? fla : "Idle");
            });
        }).start();
    }

    private void updateRunningAppsFromShizuku(List<String> fgPkgs) {
        // Only update if Shizuku is providing new data beyond the cached scan
        mRunningContainer.removeAllViews();
        List<AppSecurityInfo> cached = mMgr.getCachedApps();
        int shown = 0;
        // Show Shizuku foreground service packages first
        for (String pkg : fgPkgs) {
            if (shown >= 10) break;
            AppSecurityInfo match = null;
            for (AppSecurityInfo a : cached) {
                if (a.packageName.equals(pkg)) { match = a; break; }
            }
            if (match != null) {
                mRunningContainer.addView(buildRunningAppChip(match));
            } else {
                mRunningContainer.addView(buildShizukuAppChip(pkg));
            }
            shown++;
        }
        // Fill remaining from cached
        for (AppSecurityInfo a : cached) {
            if (shown >= 10) break;
            if (!a.isRunning) continue;
            boolean already = false;
            for (String p : fgPkgs) { if (p.equals(a.packageName)) { already = true; break; } }
            if (!already) { mRunningContainer.addView(buildRunningAppChip(a)); shown++; }
        }
        if (shown == 0) {
            mRunningContainer.addView(mUi.label(
                    "No running app data. Enable Shizuku for full process list.",
                    12f, SecurityUiHelper.CLR_SECONDARY, false));
        }
    }

    private View buildShizukuAppChip(String pkg) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(mUi.dp(8), mUi.dp(8), mUi.dp(8), mUi.dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SecurityUiHelper.CLR_CARD2);
        bg.setCornerRadius(mUi.dp(10));
        chip.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(mUi.dp(64),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = mUi.dp(8);
        chip.setLayoutParams(lp);
        chip.addView(mUi.label("📦", 22f, SecurityUiHelper.CLR_SECONDARY, false));
        chip.addView(mUi.spacer(4));
        String shortName = pkg.contains(".") ? pkg.substring(pkg.lastIndexOf('.') + 1) : pkg;
        if (shortName.length() > 8) shortName = shortName.substring(0, 7) + "…";
        TextView tvName = mUi.label(shortName, 9f, SecurityUiHelper.CLR_TEXT, false);
        tvName.setGravity(Gravity.CENTER);
        chip.addView(tvName);
        chip.addView(mUi.spacer(3));
        chip.addView(mUi.badge("Svc", SecurityUiHelper.CLR_TEAL & 0x33FFFFFF,
                SecurityUiHelper.CLR_TEAL, 20));

        final String finalPkg = pkg;
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> {
            Intent i = new Intent(this, AppDetailsActivity.class);
            i.putExtra(AppDetailsActivity.EXTRA_PKG, finalPkg);
            startActivity(i);
        });
        return chip;
    }

    private void setSensorState(View dot, TextView label, boolean active, String text) {
        if (dot == null || label == null) return;
        int color = active ? SecurityUiHelper.CLR_RED : SecurityUiHelper.CLR_GREEN;
        ((GradientDrawable) dot.getBackground()).setColor(color);
        label.setText(text.length() > 10 ? text.substring(0, 9) + "…" : text);
        label.setTextColor(color);
    }

    /** Adds a feed entry with a specific historical timestamp (not "now"). */
    private void addFeedEntryWithTime(String source, String msg, int color, long epochMs) {
        LinearLayout row = mUi.row(true);
        row.setPadding(0, mUi.dp(6), 0, mUi.dp(6));

        View dot = mUi.colorDot(color, 6);
        row.addView(dot);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(lp);
        textCol.addView(mUi.label(source, 12f, SecurityUiHelper.CLR_TEXT, true));
        textCol.addView(mUi.label(msg, 11f, SecurityUiHelper.CLR_SECONDARY, false));
        row.addView(textCol);

        row.addView(mUi.label(mSdfFull.format(new Date(epochMs)), 10f,
                SecurityUiHelper.CLR_SECONDARY, false));

        if (mFeedContainer.getChildCount() > 0) {
            mFeedContainer.addView(mUi.divider(), mFeedContainer.getChildCount());
        }
        mFeedContainer.addView(row);

        while (mFeedContainer.getChildCount() > 60) {
            mFeedContainer.removeViewAt(mFeedContainer.getChildCount() - 1);
        }
    }
}
