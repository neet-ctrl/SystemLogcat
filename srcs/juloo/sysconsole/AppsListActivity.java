package juloo.sysconsole;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Full app inventory with expandable dropdown cards.
 *
 * Each card shows:
 *  HEADER   — icon, name, package, risk badge, permission icon strip, chevron
 *  EXPANDED — Overview | Permissions Requested | Granted (Shizuku) | Risk Factors | Privileges | Actions
 */
public class AppsListActivity extends Activity {

    private static final int SORT_NAME    = 0;
    private static final int SORT_RISK    = 1;
    private static final int SORT_INSTALL = 2;
    private static final int SORT_USED    = 3;

    private SecurityUiHelper      mUi;
    private SecurityScanManager   mMgr;
    private LinearLayout          mListContainer;
    private List<AppSecurityInfo> mAll      = new ArrayList<>();
    private List<AppSecurityInfo> mFiltered = new ArrayList<>();
    private String                mFilter       = "";
    private int                   mRiskFilter   = -1;
    private int                   mSortMode     = SORT_RISK;
    private boolean               mSystemFilter = false;
    private final Set<String>     mExpanded = new HashSet<>();
    private final Handler         mHandler  = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat mSdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        mMgr = SecurityHub.getManager(this);
        mUi  = new SecurityUiHelper(this);
        mAll = new ArrayList<>(mMgr.getCachedApps());
        buildUi();
    }

    // ── Root UI ───────────────────────────────────────────────────────────────

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SecurityUiHelper.CLR_BG);

        root.addView(buildTopBar());
        root.addView(buildSearchBar());
        root.addView(buildFilterBar());

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(SecurityUiHelper.CLR_BG);
        sv.setFillViewport(true);
        mListContainer = new LinearLayout(this);
        mListContainer.setOrientation(LinearLayout.VERTICAL);
        mListContainer.setPadding(mUi.dp(12), mUi.dp(8), mUi.dp(12), mUi.dp(32));
        sv.addView(mListContainer);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        applyFilterAndSort();
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) result = getResources().getDimensionPixelSize(resId);
        return Math.max(result, mUi.dp(24));
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(SecurityUiHelper.CLR_NAV_BG);
        bar.setPadding(mUi.dp(14), mUi.dp(10) + getStatusBarHeight(), mUi.dp(14), mUi.dp(10));

        Button back = new Button(this);
        back.setText("←");
        back.setTextSize(18f);
        back.setTextColor(SecurityUiHelper.CLR_TEAL);
        back.setBackground(null);
        back.setAllCaps(false);
        back.setPadding(0, 0, mUi.dp(6), 0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleCol.addView(mUi.label("All Installed Apps", 17f, SecurityUiHelper.CLR_TEXT, true));
        titleCol.addView(mUi.label(mAll.size() + " apps  •  tap any row to expand",
                11f, SecurityUiHelper.CLR_SECONDARY, false));
        bar.addView(titleCol);

        Button expandAll = new Button(this);
        expandAll.setText("⊞ All");
        expandAll.setTextSize(11f);
        expandAll.setTextColor(SecurityUiHelper.CLR_TEAL);
        expandAll.setBackground(null);
        expandAll.setAllCaps(false);
        expandAll.setOnClickListener(v -> {
            if (mExpanded.size() >= mFiltered.size()) {
                mExpanded.clear();
            } else {
                for (AppSecurityInfo a : mFiltered) mExpanded.add(a.packageName);
            }
            rebuildList();
        });
        bar.addView(expandAll);
        return bar;
    }

    // ── Search bar ────────────────────────────────────────────────────────────

    private View buildSearchBar() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setBackgroundColor(SecurityUiHelper.CLR_NAV_BG);
        wrapper.setPadding(mUi.dp(14), 0, mUi.dp(14), mUi.dp(10));

        EditText et = new EditText(this);
        et.setHint("🔍  Search by name or package…");
        et.setHintTextColor(0xFF475569);
        et.setTextColor(SecurityUiHelper.CLR_TEXT);
        et.setTextSize(13f);
        et.setSingleLine(true);
        et.setPadding(mUi.dp(14), mUi.dp(10), mUi.dp(14), mUi.dp(10));
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(SecurityUiHelper.CLR_CARD);
        etBg.setCornerRadius(mUi.dp(10));
        et.setBackground(etBg);
        et.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int s, int co, int a) {}
            public void onTextChanged(CharSequence c, int s, int b, int a) {
                mFilter = c.toString().toLowerCase().trim();
                mHandler.removeCallbacksAndMessages(null);
                mHandler.postDelayed(() -> applyFilterAndSort(), 180);
            }
            public void afterTextChanged(Editable e) {}
        });
        wrapper.addView(et);
        return wrapper;
    }

    // ── Filter / sort bar ─────────────────────────────────────────────────────

    private View buildFilterBar() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setBackgroundColor(SecurityUiHelper.CLR_NAV_BG);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);
        wrapper.setPadding(mUi.dp(14), 0, mUi.dp(14), mUi.dp(12));

        addFilterChip(wrapper, "All",     -1);
        addFilterChip(wrapper, "🔴 High",  AppSecurityInfo.RISK_HIGH);
        addFilterChip(wrapper, "🟠 Medium",AppSecurityInfo.RISK_MEDIUM);
        addFilterChip(wrapper, "🟢 Low",   AppSecurityInfo.RISK_LOW);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        wrapper.addView(spacer);

        Button sort = new Button(this);
        sort.setText("⇅");
        sort.setTextSize(14f);
        sort.setTextColor(SecurityUiHelper.CLR_TEAL);
        sort.setBackground(null);
        sort.setAllCaps(false);
        sort.setOnClickListener(v -> showSortDialog());
        wrapper.addView(sort);

        Button sys = new Button(this);
        sys.setText("⚙");
        sys.setTextSize(14f);
        sys.setTextColor(mSystemFilter ? SecurityUiHelper.CLR_TEAL : SecurityUiHelper.CLR_SECONDARY);
        sys.setBackground(null);
        sys.setAllCaps(false);
        sys.setOnClickListener(v -> {
            mSystemFilter = !mSystemFilter;
            sys.setTextColor(mSystemFilter ? SecurityUiHelper.CLR_TEAL : SecurityUiHelper.CLR_SECONDARY);
            applyFilterAndSort();
        });
        wrapper.addView(sys);
        return wrapper;
    }

    private void addFilterChip(LinearLayout row, String label, int riskVal) {
        boolean active = (mRiskFilter == riskVal);
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(10f);
        btn.setAllCaps(false);
        int bgColor = active
                ? (riskVal == AppSecurityInfo.RISK_HIGH   ? SecurityUiHelper.CLR_RED
                  : riskVal == AppSecurityInfo.RISK_MEDIUM ? SecurityUiHelper.CLR_ORANGE
                  : riskVal == AppSecurityInfo.RISK_LOW   ? SecurityUiHelper.CLR_GREEN
                  : SecurityUiHelper.CLR_TEAL)
                : SecurityUiHelper.CLR_CARD;
        btn.setTextColor(active ? 0xFF0A1622 : SecurityUiHelper.CLR_SECONDARY);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(mUi.dp(12));
        btn.setBackground(bg);
        btn.setPadding(mUi.dp(10), mUi.dp(3), mUi.dp(10), mUi.dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = mUi.dp(5);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> { mRiskFilter = riskVal; applyFilterAndSort(); });
        row.addView(btn);
    }

    private void showSortDialog() {
        String[] options = {"Name (A–Z)", "Risk Score (High first)", "Install Date (Newest first)", "Last Used (Recent first)"};
        new AlertDialog.Builder(this)
            .setTitle("Sort Apps By")
            .setItems(options, (d, which) -> { mSortMode = which; applyFilterAndSort(); })
            .show();
    }

    // ── Filter + sort + render ────────────────────────────────────────────────

    private void applyFilterAndSort() {
        mFiltered = new ArrayList<>();
        for (AppSecurityInfo a : mAll) {
            if (mSystemFilter && a.isSystemApp) continue;
            if (mRiskFilter >= 0 && a.riskLevel != mRiskFilter) continue;
            if (!mFilter.isEmpty()
                    && (a.appName == null || !a.appName.toLowerCase().contains(mFilter))
                    && !a.packageName.toLowerCase().contains(mFilter)) continue;
            mFiltered.add(a);
        }
        switch (mSortMode) {
            case SORT_NAME:    Collections.sort(mFiltered,
                    (a, b) -> {
                        String na = a.appName != null ? a.appName : a.packageName;
                        String nb = b.appName != null ? b.appName : b.packageName;
                        return na.compareToIgnoreCase(nb);
                    }); break;
            case SORT_RISK:    Collections.sort(mFiltered,
                    (a, b) -> Integer.compare(b.riskScore, a.riskScore)); break;
            case SORT_INSTALL: Collections.sort(mFiltered,
                    (a, b) -> Long.compare(b.installTime, a.installTime)); break;
            case SORT_USED:    Collections.sort(mFiltered,
                    (a, b) -> Long.compare(b.lastUsed, a.lastUsed)); break;
        }
        rebuildList();
    }

    private void rebuildList() {
        mListContainer.removeAllViews();

        if (mFiltered.isEmpty()) {
            LinearLayout empty = mUi.card(16, 20);
            empty.setGravity(Gravity.CENTER);
            TextView tv = mUi.label(mAll.isEmpty()
                    ? "No scan data yet — run a Deep Scan from the Dashboard."
                    : "No apps match your search/filter.",
                    13f, SecurityUiHelper.CLR_SECONDARY, false);
            tv.setGravity(Gravity.CENTER);
            empty.addView(tv);
            mListContainer.addView(empty);
            return;
        }

        mListContainer.addView(mUi.label(
                mFiltered.size() + " apps  •  tap row to expand  •  tap ⊞ All to expand all",
                11f, SecurityUiHelper.CLR_SECONDARY, false));
        mListContainer.addView(mUi.spacer(6));

        for (AppSecurityInfo app : mFiltered) {
            mListContainer.addView(buildAppCard(app));
            mListContainer.addView(mUi.spacer(8));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Expandable app card
    // ═════════════════════════════════════════════════════════════════════════

    private View buildAppCard(AppSecurityInfo app) {
        boolean expanded = mExpanded.contains(app.packageName);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(SecurityUiHelper.CLR_CARD);
        cardBg.setCornerRadius(mUi.dp(14));
        if (app.isSuspectedSpyware)
            cardBg.setStroke(mUi.dp(2), SecurityUiHelper.CLR_RED);
        else if (app.riskLevel == AppSecurityInfo.RISK_HIGH)
            cardBg.setStroke(mUi.dp(1), 0x55E63946);
        else if (app.isBankingApp)
            cardBg.setStroke(mUi.dp(1), 0x5560A5FA);
        card.setBackground(cardBg);

        LinearLayout header = buildCardHeader(app, expanded);
        LinearLayout body   = buildCardBody(app);
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);

        card.addView(header);
        card.addView(body);

        header.setClickable(true);
        header.setFocusable(true);
        header.setOnClickListener(v -> {
            boolean nowExpanded = !mExpanded.contains(app.packageName);
            if (nowExpanded) mExpanded.add(app.packageName);
            else             mExpanded.remove(app.packageName);
            body.setVisibility(nowExpanded ? View.VISIBLE : View.GONE);
            TextView chevron = (TextView) header.getTag();
            if (chevron != null) chevron.setText(nowExpanded ? "▲" : "▼");
        });

        return card;
    }

    // ── Card header ───────────────────────────────────────────────────────────

    private LinearLayout buildCardHeader(AppSecurityInfo app, boolean expanded) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(mUi.dp(14), mUi.dp(12), mUi.dp(14), mUi.dp(12));

        // Icon
        if (app.icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(app.icon);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(mUi.dp(42), mUi.dp(42));
            ilp.rightMargin = mUi.dp(12);
            header.addView(iv, ilp);
        } else {
            TextView fallback = mUi.label("📦", 24f, SecurityUiHelper.CLR_SECONDARY, false);
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(mUi.dp(42), mUi.dp(42));
            flp.rightMargin = mUi.dp(12);
            fallback.setGravity(Gravity.CENTER);
            header.addView(fallback, flp);
        }

        // Name + package + perm strip
        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        nameCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tvName = mUi.label(
                app.appName != null ? app.appName : app.packageName,
                14f, SecurityUiHelper.CLR_TEXT, true);
        tvName.setMaxLines(1);
        nameRow.addView(tvName);
        if (app.isRunning) {
            nameRow.addView(mUi.hspacer(5));
            nameRow.addView(mUi.badge("● Live", 0x22009900, SecurityUiHelper.CLR_GREEN, 18));
        }
        if (app.isSuspectedSpyware) {
            nameRow.addView(mUi.hspacer(4));
            nameRow.addView(mUi.badge("⚠ SPYWARE", 0x33E63946, SecurityUiHelper.CLR_RED, 18));
        }
        nameCol.addView(nameRow);

        TextView tvPkg = mUi.label(app.packageName, 10f, SecurityUiHelper.CLR_SECONDARY, false);
        tvPkg.setMaxLines(1);
        nameCol.addView(tvPkg);

        nameCol.addView(buildPermIconStrip(app));
        header.addView(nameCol);

        // Right: risk + chevron
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(Gravity.CENTER | Gravity.END);
        rightCol.setPadding(mUi.dp(8), 0, 0, 0);

        int riskColor = app.riskColor();
        rightCol.addView(mUi.badge(app.riskLevelLabel().toUpperCase(),
                riskColor & 0x33FFFFFF, riskColor, 20));
        rightCol.addView(mUi.spacer(3));

        TextView tvScore = mUi.label(app.riskScore + "/100", 10f, riskColor, true);
        tvScore.setGravity(Gravity.END);
        rightCol.addView(tvScore);

        rightCol.addView(mUi.spacer(5));
        TextView chevron = mUi.label(expanded ? "▲" : "▼", 10f,
                SecurityUiHelper.CLR_SECONDARY, false);
        chevron.setGravity(Gravity.END);
        rightCol.addView(chevron);
        header.setTag(chevron);

        header.addView(rightCol);
        return header;
    }

    // ── Card body (expandable) ────────────────────────────────────────────────

    private LinearLayout buildCardBody(AppSecurityInfo app) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(mUi.dp(14), 0, mUi.dp(14), mUi.dp(14));

        body.addView(mUi.divider());
        body.addView(mUi.spacer(10));

        // 1. Overview
        body.addView(sectionLabel("📊  OVERVIEW"));
        body.addView(buildOverview(app));
        body.addView(mUi.spacer(12));

        // 2. Permissions Requested
        body.addView(sectionLabel("🔑  PERMISSIONS REQUESTED  (" + app.permissionCount() + " dangerous)"));
        body.addView(buildPermissionsRequested(app));
        body.addView(mUi.spacer(12));

        // 3. Granted Permissions (Shizuku)
        if (!app.grantedPermissions.isEmpty()) {
            body.addView(sectionLabel("✅  GRANTED RUNTIME PERMISSIONS  (via Shizuku — " + app.grantedPermissions.size() + " total)"));
            body.addView(buildGrantedPermissions(app));
            body.addView(mUi.spacer(12));
        }

        // 4. Risk Factors
        if (!app.riskFactors.isEmpty()) {
            body.addView(sectionLabel("⚠  RISK FACTORS  (" + app.riskFactors.size() + ")"));
            body.addView(buildRiskFactors(app));
            body.addView(mUi.spacer(12));
        }

        // 5. Elevated Privileges
        if (app.isDeviceAdmin || app.isAccessibilityService
                || app.isNotificationListener || app.isVpnService) {
            body.addView(sectionLabel("🔒  ELEVATED PRIVILEGES DETECTED"));
            body.addView(buildPrivilegeFlags(app));
            body.addView(mUi.spacer(12));
        }

        // 6. Actions
        body.addView(sectionLabel("⚡  ACTIONS"));
        body.addView(buildActions(app));
        return body;
    }

    // ── Overview section ──────────────────────────────────────────────────────

    private View buildOverview(AppSecurityInfo app) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SecurityUiHelper.CLR_CARD2);
        bg.setCornerRadius(mUi.dp(10));
        grid.setBackground(bg);
        grid.setPadding(mUi.dp(12), mUi.dp(10), mUi.dp(12), mUi.dp(10));

        infoRow(grid, "📱 Version",
                "v" + (app.versionName != null ? app.versionName : "?") + " (code " + app.versionCode + ")");
        infoRow(grid, "🏷 Type",          app.isSystemApp ? "System App" : "User-Installed App");
        if (app.installTime > 0)
            infoRow(grid, "📥 Installed",  mSdf.format(new Date(app.installTime)));
        if (app.lastUsed > 0)
            infoRow(grid, "🕐 Last Used",  mSdf.format(new Date(app.lastUsed)));
        else
            infoRow(grid, "🕐 Last Used",  "Unknown (grant Usage Stats for tracking)");
        if (app.backgroundRunCount > 0)
            infoRow(grid, "⚙ BG Runs",    app.backgroundRunCount + " times in last 24h");
        if (app.backgroundTimeMs > 0) {
            long mins = app.backgroundTimeMs / 60_000;
            infoRow(grid, "⏱ BG Time",   mins < 60 ? mins + " min" : (mins / 60) + "h " + (mins % 60) + "m");
        }
        infoRow(grid, "🌐 Network (7d)",  app.networkUsageFormatted());
        infoRow(grid, "📊 Risk Score",    app.riskScore + "/100 — " + app.riskLevelLabel() + " Risk");
        infoRow(grid, "🔑 Perms Requested", app.permissionCount() + " dangerous permissions");
        if (app.isBankingApp)
            infoRow(grid, "🏦 Banking App", app.isBankingRisk
                    ? "⚠ Cross-app banking risk detected" : "Recognized banking/finance app");
        return grid;
    }

    private void infoRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, mUi.dp(4), 0, mUi.dp(4));

        TextView tvLabel = mUi.label(label, 11f, SecurityUiHelper.CLR_SECONDARY, false);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(mUi.dp(130),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(tvLabel);

        TextView tvVal = mUi.label(value, 11f, SecurityUiHelper.CLR_TEXT, false);
        tvVal.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvVal.setMaxLines(2);
        row.addView(tvVal);

        parent.addView(row);
    }

    // ── Permissions requested ─────────────────────────────────────────────────

    private static final Object[][] PERM_DEFS = {
        // {field, emoji, name, description, isHighRisk}
        {"hasCamera",              "📷", "Camera",              "Take photos/video at any time",                            true},
        {"hasMicrophone",          "🎙", "Microphone",          "Record audio at any time",                                 true},
        {"hasLocation",            "📍", "Fine Location",       "Precise GPS location tracking",                            true},
        {"hasContacts",            "👥", "Contacts",            "Read your full contact list",                              true},
        {"hasStorage",             "💾", "Storage",             "Read/write all files on device",                           false},
        {"hasSms",                 "💬", "SMS",                 "Send/receive text messages, read SMS history",             true},
        {"hasPhone",               "📞", "Phone/Calls",         "Make calls, read call log",                                true},
        {"hasCalendar",            "📅", "Calendar",            "Read and modify calendar events",                          false},
        {"hasBodySensors",         "❤",  "Body Sensors",        "Access health/biometric sensor data",                      false},
        {"hasAccessibility",       "♿", "Accessibility",        "Read all screen content, simulate taps — VERY HIGH RISK", true},
        {"hasInternet",            "🌐", "Internet",            "Full unrestricted network access",                         false},
        {"hasOverlay",             "🔲", "Draw Overlay",        "Draw over other apps (overlay phishing risk)",             true},
        {"hasVpn",                 "🔒", "VPN",                 "Intercept and route ALL network traffic",                  true},
        {"hasNotificationAccess",  "🔔", "Notification Access", "Read every notification from every app",                   true},
        {"hasReadMediaImages",     "🖼", "Media/Images",        "Access all photos and media files",                        false},
        {"hasProcessOutgoingCalls","📲", "Outgoing Calls",      "Intercept and redirect outgoing calls",                    true},
        {"hasBootReceiver",        "🚀", "Boot Auto-Start",     "Launches automatically when device boots",                 false},
    };

    private View buildPermissionsRequested(AppSecurityInfo app) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SecurityUiHelper.CLR_CARD2);
        bg.setCornerRadius(mUi.dp(10));
        container.setBackground(bg);
        container.setPadding(mUi.dp(12), mUi.dp(8), mUi.dp(12), mUi.dp(8));

        boolean[] flags = {
            app.hasCamera, app.hasMicrophone, app.hasLocation, app.hasContacts,
            app.hasStorage, app.hasSms, app.hasPhone, app.hasCalendar,
            app.hasBodySensors, app.hasAccessibility, app.hasInternet,
            app.hasOverlay, app.hasVpn, app.hasNotificationAccess,
            app.hasReadMediaImages, app.hasProcessOutgoingCalls, app.hasBootReceiver
        };
        boolean[] recentUse = {
            app.cameraUsedRecently, app.micUsedRecently, app.locationUsedRecently,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false
        };

        boolean any = false;
        for (int i = 0; i < flags.length; i++) {
            if (!flags[i]) continue;
            any = true;

            String emoji   = (String) PERM_DEFS[i][1];
            String name    = (String) PERM_DEFS[i][2];
            String desc    = (String) PERM_DEFS[i][3];
            boolean hiRisk = (Boolean) PERM_DEFS[i][4];

            container.addView(buildPermRow(
                    emoji, name, desc, hiRisk, recentUse[i], app.grantedPermissions));
            if (i < flags.length - 1) container.addView(mUi.divider());
        }
        if (!any) {
            container.addView(mUi.label("✓ No dangerous permissions requested.",
                    12f, SecurityUiHelper.CLR_GREEN, false));
        }
        return container;
    }

    private View buildPermRow(String emoji, String name, String desc, boolean hiRisk,
                               boolean recentUse, List<String> grantedList) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, mUi.dp(7), 0, mUi.dp(7));

        // Emoji icon
        TextView te = mUi.label(emoji, 14f, SecurityUiHelper.CLR_TEXT, false);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                mUi.dp(28), LinearLayout.LayoutParams.WRAP_CONTENT);
        te.setGravity(Gravity.CENTER);
        row.addView(te, ep);

        // Name + description column
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        int nameColor = hiRisk ? SecurityUiHelper.CLR_ORANGE : SecurityUiHelper.CLR_TEXT;
        textCol.addView(mUi.label(name, 12f, nameColor, true));
        textCol.addView(mUi.label(desc, 10f, SecurityUiHelper.CLR_SECONDARY, false));
        row.addView(textCol);

        // Status badges column
        LinearLayout badgeCol = new LinearLayout(this);
        badgeCol.setOrientation(LinearLayout.VERTICAL);
        badgeCol.setGravity(Gravity.END);
        badgeCol.setPadding(mUi.dp(6), 0, 0, 0);

        // Granted status from Shizuku
        if (!grantedList.isEmpty()) {
            String nameUp = name.toUpperCase().replaceAll("\\s+", "_");
            boolean isGranted = false;
            for (String g : grantedList) {
                String gUp = g.toUpperCase();
                if (gUp.contains(nameUp.substring(0, Math.min(5, nameUp.length())))) {
                    isGranted = true; break;
                }
            }
            int gc = isGranted ? SecurityUiHelper.CLR_GREEN : 0xFF64748B;
            badgeCol.addView(mUi.badge(isGranted ? "Granted" : "Denied",
                    gc & 0x33FFFFFF, gc, 18));
        } else {
            badgeCol.addView(mUi.badge("Requested", 0x33FF9F1C,
                    SecurityUiHelper.CLR_ORANGE, 18));
        }

        if (recentUse) {
            badgeCol.addView(mUi.spacer(3));
            badgeCol.addView(mUi.badge("⚡ Used recently", 0x33E63946,
                    SecurityUiHelper.CLR_RED, 18));
        }

        row.addView(badgeCol);
        return row;
    }

    // ── Granted runtime permissions (Shizuku) ─────────────────────────────────

    private View buildGrantedPermissions(AppSecurityInfo app) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SecurityUiHelper.CLR_CARD2);
        bg.setCornerRadius(mUi.dp(10));
        container.setBackground(bg);
        container.setPadding(mUi.dp(12), mUi.dp(10), mUi.dp(12), mUi.dp(10));

        container.addView(mUi.label(
                "Source: Shizuku privileged AppOps dump",
                10f, SecurityUiHelper.CLR_TEAL, false));
        container.addView(mUi.spacer(8));

        for (String perm : app.grantedPermissions) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, mUi.dp(3), 0, mUi.dp(3));

            boolean dangerous = isDangerous(perm);
            int dotColor = dangerous ? SecurityUiHelper.CLR_ORANGE : SecurityUiHelper.CLR_GREEN;
            row.addView(mUi.colorDot(dotColor, 5));
            row.addView(mUi.hspacer(8));

            String shortPerm = perm.startsWith("android.permission.")
                    ? perm.substring("android.permission.".length()) : perm;
            TextView tv = mUi.label(shortPerm, 10f,
                    dangerous ? SecurityUiHelper.CLR_ORANGE : SecurityUiHelper.CLR_TEXT, false);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tv);

            if (dangerous)
                row.addView(mUi.badge("⚠", 0x33E63946, SecurityUiHelper.CLR_RED, 18));

            container.addView(row);
        }
        return container;
    }

    private static boolean isDangerous(String perm) {
        String up = perm.toUpperCase();
        return up.contains("CAMERA") || up.contains("RECORD_AUDIO") || up.contains("LOCATION")
            || up.contains("CONTACTS") || up.contains("READ_SMS") || up.contains("CALL_LOG")
            || up.contains("PROCESS_OUTGOING") || up.contains("ACCESSIBILITY")
            || up.contains("SYSTEM_ALERT_WINDOW") || up.contains("BIND_NOTIFICATION")
            || up.contains("READ_PHONE") || up.contains("RECEIVE_SMS");
    }

    // ── Risk factors ──────────────────────────────────────────────────────────

    private View buildRiskFactors(AppSecurityInfo app) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(app.riskLevel == AppSecurityInfo.RISK_HIGH ? 0xFF190A0A : SecurityUiHelper.CLR_CARD2);
        bg.setCornerRadius(mUi.dp(10));
        container.setBackground(bg);
        container.setPadding(mUi.dp(12), mUi.dp(10), mUi.dp(12), mUi.dp(10));

        for (String factor : app.riskFactors) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, mUi.dp(4), 0, mUi.dp(4));

            boolean critical = factor.toLowerCase().contains("camera")
                    || factor.toLowerCase().contains("spyware")
                    || factor.toLowerCase().contains("accessibility")
                    || factor.toLowerCase().contains("exfiltrate");
            int dotColor = critical ? SecurityUiHelper.CLR_RED : SecurityUiHelper.CLR_ORANGE;
            row.addView(mUi.colorDot(dotColor, 6));
            row.addView(mUi.hspacer(8));

            TextView tv = mUi.label(factor, 11f, SecurityUiHelper.CLR_TEXT, false);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tv);
            container.addView(row);
        }

        // Risk score gauge
        container.addView(mUi.spacer(8));
        container.addView(mUi.divider());
        container.addView(mUi.spacer(6));
        container.addView(buildScoreGauge(app));
        return container;
    }

    private View buildScoreGauge(AppSecurityInfo app) {
        LinearLayout gauge = new LinearLayout(this);
        gauge.setOrientation(LinearLayout.VERTICAL);

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.addView(mUi.label("Risk Score", 10f, SecurityUiHelper.CLR_SECONDARY, false));
        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        labelRow.addView(sp);
        labelRow.addView(mUi.label(app.riskScore + " / 100", 10f, app.riskColor(), true));
        gauge.addView(labelRow);
        gauge.addView(mUi.spacer(5));

        LinearLayout track = new LinearLayout(this);
        track.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, mUi.dp(7)));
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor(0xFF1E3A5F);
        trackBg.setCornerRadius(mUi.dp(4));
        track.setBackground(trackBg);

        float pct = Math.max(0.02f, Math.min(app.riskScore / 100f, 1f));
        View fill = new View(this);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, pct);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setColor(app.riskColor());
        fillBg.setCornerRadius(mUi.dp(4));
        fill.setBackground(fillBg);
        fill.setLayoutParams(flp);
        track.addView(fill);

        View rest = new View(this);
        rest.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f - pct));
        track.addView(rest);
        gauge.addView(track);
        return gauge;
    }

    // ── Elevated privilege flags ──────────────────────────────────────────────

    private View buildPrivilegeFlags(AppSecurityInfo app) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1A1000);
        bg.setCornerRadius(mUi.dp(10));
        bg.setStroke(mUi.dp(1), 0x55FF9F1C);
        container.setBackground(bg);
        container.setPadding(mUi.dp(12), mUi.dp(10), mUi.dp(12), mUi.dp(10));

        if (app.isDeviceAdmin)
            privRow(container, "🔐", "Device Administrator",
                    "Can enforce policies, lock screen, factory reset");
        if (app.isAccessibilityService)
            privRow(container, "♿", "Accessibility Service Active",
                    "Reading screen content and injecting input events");
        if (app.isNotificationListener)
            privRow(container, "🔔", "Notification Listener Active",
                    "Reads every notification from every app in real time");
        if (app.isVpnService)
            privRow(container, "🔒", "VPN Service Active",
                    "All network traffic routed through this app");
        return container;
    }

    private void privRow(LinearLayout parent, String emoji, String title, String desc) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, mUi.dp(5), 0, mUi.dp(5));
        row.addView(mUi.label(emoji + " ", 14f, SecurityUiHelper.CLR_ORANGE, false));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(mUi.label(title, 12f, SecurityUiHelper.CLR_ORANGE, true));
        col.addView(mUi.label(desc, 10f, SecurityUiHelper.CLR_SECONDARY, false));
        row.addView(col);
        parent.addView(row);
    }

    // ── Action buttons ────────────────────────────────────────────────────────

    private View buildActions(AppSecurityInfo app) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // View full details
        Button details = mUi.primaryButton("📋 Details", SecurityUiHelper.CLR_TEAL);
        details.setTextSize(11f);
        details.setPadding(mUi.dp(12), mUi.dp(7), mUi.dp(12), mUi.dp(7));
        details.setOnClickListener(v -> {
            Intent i = new Intent(this, AppDetailsActivity.class);
            i.putExtra(AppDetailsActivity.EXTRA_PKG, app.packageName);
            startActivity(i);
        });
        row.addView(details);
        row.addView(mUi.hspacer(8));

        // Uninstall (user apps only)
        if (!app.isSystemApp) {
            Button uninstall = mUi.primaryButton("🗑 Uninstall", SecurityUiHelper.CLR_RED);
            uninstall.setTextSize(11f);
            uninstall.setPadding(mUi.dp(12), mUi.dp(7), mUi.dp(12), mUi.dp(7));
            uninstall.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                    .setTitle("Uninstall " + app.appName + "?")
                    .setMessage("This will remove " + app.appName + " (" + app.packageName + ") from your device.")
                    .setPositiveButton("Uninstall", (d, w) -> startActivity(
                            new Intent(Intent.ACTION_DELETE,
                                    Uri.parse("package:" + app.packageName))))
                    .setNegativeButton("Cancel", null)
                    .show());
            row.addView(uninstall);
            row.addView(mUi.hspacer(8));
        }

        // Open system app info
        Button appInfo = new Button(this);
        appInfo.setText("⚙ App Info");
        appInfo.setTextSize(11f);
        appInfo.setAllCaps(false);
        appInfo.setTextColor(SecurityUiHelper.CLR_SECONDARY);
        appInfo.setBackground(null);
        appInfo.setPadding(0, 0, 0, 0);
        appInfo.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + app.packageName))));
        row.addView(appInfo);
        return row;
    }

    // ── Permission icon strip (compact header view) ───────────────────────────

    private LinearLayout buildPermIconStrip(AppSecurityInfo app) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, mUi.dp(3), 0, 0);

        if (app.hasCamera)        addStripIcon(row, "📷", app.cameraUsedRecently);
        if (app.hasMicrophone)    addStripIcon(row, "🎙", app.micUsedRecently);
        if (app.hasLocation)      addStripIcon(row, "📍", app.locationUsedRecently);
        if (app.hasContacts)      addStripIcon(row, "👥", false);
        if (app.hasSms)           addStripIcon(row, "💬", false);
        if (app.hasAccessibility) addStripIcon(row, "♿", false);
        if (app.hasOverlay)       addStripIcon(row, "🔲", false);
        if (app.hasVpn)           addStripIcon(row, "🔒", false);
        if (app.isDeviceAdmin)    addStripIcon(row, "🔐", false);
        return row;
    }

    private void addStripIcon(LinearLayout row, String emoji, boolean recent) {
        TextView tv = new TextView(this);
        tv.setText(emoji);
        tv.setTextSize(10f);
        if (recent) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x33E63946);
            bg.setCornerRadius(mUi.dp(3));
            tv.setBackground(bg);
            tv.setPadding(mUi.dp(2), 0, mUi.dp(2), 0);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = mUi.dp(2);
        row.addView(tv, lp);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private View sectionLabel(String text) {
        TextView tv = mUi.label(text, 10f, 0xFF94A3B8, true);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = mUi.dp(6);
        tv.setLayoutParams(lp);
        return tv;
    }
}
