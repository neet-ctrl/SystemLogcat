package juloo.sysconsole;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class SecuritySettingsActivity extends Activity {

    private static final String PREFS = "sec_prefs";
    private SecurityUiHelper mUi;
    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        mUi   = new SecurityUiHelper(this);
        mPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
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

        content.addView(buildGeneralSection());
        content.addView(mUi.spacer(16));
        content.addView(buildAlertSection());
        content.addView(mUi.spacer(16));
        content.addView(buildDataSection());
        content.addView(mUi.spacer(16));
        content.addView(buildShizukuSection());
        content.addView(mUi.spacer(16));
        content.addView(buildAboutSection());

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

        TextView title = mUi.label("Settings", 17f, SecurityUiHelper.CLR_TEXT, true);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    private View buildGeneralSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(mUi.sectionHeader("GENERAL"));

        LinearLayout card = mUi.card();
        addToggle(card, "Real-Time Monitoring", "Continuously watch sensor access",
                "pref_realtime", true);
        card.addView(mUi.divider());
        addToggle(card, "Background Scanning", "Run scans when app is in background",
                "pref_bg_scan", false);
        card.addView(mUi.divider());
        addInfoRow(card, "Scan Frequency", "Manual (tap Refresh)");

        section.addView(card);
        return section;
    }

    private View buildAlertSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(mUi.sectionHeader("ALERT PREFERENCES"));

        LinearLayout card = mUi.card();
        addToggle(card, "Camera Access Alerts", "Notify when apps use camera",
                "pref_alert_camera", true);
        card.addView(mUi.divider());
        addToggle(card, "Microphone Access Alerts", "Notify when apps use mic",
                "pref_alert_mic", true);
        card.addView(mUi.divider());
        addToggle(card, "Location Access Alerts", "Notify on location use",
                "pref_alert_location", true);
        card.addView(mUi.divider());
        addToggle(card, "High-Risk App Alerts", "Notify when a risky app is detected",
                "pref_alert_highrisk", true);
        card.addView(mUi.divider());
        addToggle(card, "New App Install Alerts", "Notify when a new app is installed",
                "pref_alert_install", false);

        section.addView(card);
        return section;
    }

    private View buildDataSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(mUi.sectionHeader("DATA MANAGEMENT"));

        LinearLayout card = mUi.card();

        Button clearLogs = makeSettingsButton("Clear Scan History", SecurityUiHelper.CLR_ORANGE);
        clearLogs.setOnClickListener(v -> {
            SecurityHub.reset();
            clearLogs.setText("✓ History Cleared");
            clearLogs.setTextColor(SecurityUiHelper.CLR_GREEN);
        });
        card.addView(clearLogs);

        card.addView(mUi.divider());

        addInfoRow(card, "Data Storage", "On-device only — no cloud upload");
        card.addView(mUi.divider());
        addInfoRow(card, "Privacy", "No personal data accessed");

        section.addView(card);
        return section;
    }

    private View buildShizukuSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(mUi.sectionHeader("SHIZUKU STATUS"));

        LinearLayout card = mUi.card();

        boolean binderAlive = false;
        try {
            binderAlive = rikka.shizuku.Shizuku.pingBinder();
        } catch (Exception ignored) {}

        LinearLayout statusRow = mUi.row(true);
        View dot = mUi.colorDot(
                binderAlive ? SecurityUiHelper.CLR_GREEN : SecurityUiHelper.CLR_RED, 10);
        statusRow.addView(dot);
        String statusText = binderAlive
                ? "Shizuku: Connected ✓"
                : "Shizuku: Not Connected";
        statusRow.addView(mUi.label(statusText, 13f,
                binderAlive ? SecurityUiHelper.CLR_GREEN : SecurityUiHelper.CLR_RED, true));
        card.addView(statusRow);

        card.addView(mUi.divider());

        addInfoRow(card, "Role", "Deep system access for AppOps & process data");
        card.addView(mUi.divider());
        addInfoRow(card, "Permission", "No root required — uses Wireless Debugging");

        section.addView(card);
        return section;
    }

    private View buildAboutSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(mUi.sectionHeader("ABOUT"));

        LinearLayout card = mUi.card();
        addInfoRow(card, "App", "System Console + Security Auditor");
        card.addView(mUi.divider());
        addInfoRow(card, "Version", "1.0.0");
        card.addView(mUi.divider());
        addInfoRow(card, "License", "Open Source");
        card.addView(mUi.divider());
        addInfoRow(card, "Powered by", "Shizuku API");

        section.addView(card);
        return section;
    }

    private void addToggle(LinearLayout card, String title, String desc,
                           String prefKey, boolean defVal) {
        LinearLayout row = mUi.row(true);
        row.setPadding(0, mUi.dp(6), 0, mUi.dp(6));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(lp);
        textCol.addView(mUi.label(title, 13f, SecurityUiHelper.CLR_TEXT, false));
        textCol.addView(mUi.label(desc, 11f, SecurityUiHelper.CLR_SECONDARY, false));
        row.addView(textCol);

        Switch sw = new Switch(this);
        sw.setChecked(mPrefs.getBoolean(prefKey, defVal));
        sw.setOnCheckedChangeListener((btn, checked) ->
                mPrefs.edit().putBoolean(prefKey, checked).apply());
        row.addView(sw);

        card.addView(row);
    }

    private void addInfoRow(LinearLayout card, String key, String value) {
        LinearLayout row = mUi.row(true);
        row.setPadding(0, mUi.dp(6), 0, mUi.dp(6));
        row.addView(mUi.label(key, 13f, SecurityUiHelper.CLR_SECONDARY, false));
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(spacer);
        row.addView(mUi.label(value, 13f, SecurityUiHelper.CLR_TEXT, false));
        card.addView(row);
    }

    private Button makeSettingsButton(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(13f);
        btn.setTextColor(color);
        btn.setBackground(null);
        btn.setAllCaps(false);
        btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        btn.setPadding(0, mUi.dp(4), 0, mUi.dp(4));
        return btn;
    }
}
