package juloo.sysconsole;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

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

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        mMgr = SecurityHub.getManager(this);
        mUi  = new SecurityUiHelper(this);
        buildUi();
        populateRunningApps();
        seedFeed();
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

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(SecurityUiHelper.CLR_NAV_BG);
        bar.setPadding(mUi.dp(16), mUi.dp(14), mUi.dp(16), mUi.dp(14));

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

        mRunningContainer = new LinearLayout(this);
        mRunningContainer.setOrientation(LinearLayout.HORIZONTAL);
        section.addView(mRunningContainer);
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
        if (shown == 0) {
            mRunningContainer.addView(mUi.label(
                    "No running app data available.", 12f,
                    SecurityUiHelper.CLR_SECONDARY, false));
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
        TextView tvName = mUi.label(
                a.appName.length() > 8 ? a.appName.substring(0, 7) + "…" : a.appName,
                9f, SecurityUiHelper.CLR_TEXT, false);
        tvName.setGravity(Gravity.CENTER);
        chip.addView(tvName);

        chip.addView(mUi.spacer(3));
        View liveBadge = mUi.badge("Live", SecurityUiHelper.CLR_TEAL & 0x33FFFFFF | 0xFF000000,
                SecurityUiHelper.CLR_TEAL, 20);
        chip.addView(liveBadge);

        return chip;
    }

    private void seedFeed() {
        List<AppSecurityInfo> apps = mMgr.getCachedApps();
        if (apps.isEmpty()) {
            addFeedEntry("📱 System", "Monitoring started", SecurityUiHelper.CLR_TEAL);
            return;
        }
        for (AppSecurityInfo a : apps) {
            if (a.cameraUsedRecently) {
                addFeedEntry("📷 " + a.appName, "Camera accessed recently",
                        SecurityUiHelper.CLR_ORANGE);
            }
            if (a.micUsedRecently) {
                addFeedEntry("🎙 " + a.appName, "Microphone accessed recently",
                        SecurityUiHelper.CLR_ORANGE);
            }
            if (a.locationUsedRecently) {
                addFeedEntry("📍 " + a.appName, "Location accessed recently",
                        SecurityUiHelper.CLR_SECONDARY);
            }
        }
        if (mFeedContainer.getChildCount() == 0) {
            addFeedEntry("✅ Monitor", "No suspicious activity detected",
                    SecurityUiHelper.CLR_GREEN);
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

    private void startPolling() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!mRunning) return;
                updateSensorStatus();
                mHandler.postDelayed(this, 5000);
            }
        }, 5000);
    }

    private void updateSensorStatus() {
        List<AppSecurityInfo> apps = mMgr.getCachedApps();
        boolean camActive = false, micActive = false, locActive = false;
        String camApp = "", micApp = "", locApp = "";

        for (AppSecurityInfo a : apps) {
            if (a.isRunning && a.cameraUsedRecently)   { camActive = true; camApp = a.appName; }
            if (a.isRunning && a.micUsedRecently)      { micActive = true; micApp = a.appName; }
            if (a.isRunning && a.locationUsedRecently) { locActive = true; locApp = a.appName; }
        }

        setSensorState(mCameraDot, mCameraStatus, camActive,
                camActive ? camApp : "Idle");
        setSensorState(mMicDot, mMicStatus, micActive,
                micActive ? micApp : "Idle");
        setSensorState(mLocationDot, mLocationStatus, locActive,
                locActive ? locApp : "Idle");
    }

    private void setSensorState(View dot, TextView label, boolean active, String text) {
        if (dot == null || label == null) return;
        int color = active ? SecurityUiHelper.CLR_RED : SecurityUiHelper.CLR_GREEN;
        ((GradientDrawable) dot.getBackground()).setColor(color);
        label.setText(text.length() > 10 ? text.substring(0, 9) + "…" : text);
        label.setTextColor(color);
    }
}
