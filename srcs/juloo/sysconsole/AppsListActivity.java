package juloo.sysconsole;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppsListActivity extends Activity {

    private static final int FILTER_ALL    = 0;
    private static final int FILTER_HIGH   = 1;
    private static final int FILTER_MEDIUM = 2;
    private static final int FILTER_LOW    = 3;

    private final Handler mMain = new Handler(Looper.getMainLooper());

    private SecurityUiHelper mUi;

    private LinearLayout mListContainer;
    private TextView     mStatusText;
    private ProgressBar  mProgress;
    private View         mLoadingBar;
    private TextView[]   mFilterBtns   = new TextView[4];
    private TextView     mSysToggle;

    private List<AppSecurityInfo> mAll      = new ArrayList<>();
    private List<AppSecurityInfo> mFiltered = new ArrayList<>();

    private int     mFilter        = FILTER_ALL;
    private boolean mShowSystem    = true;
    private String  mSearch        = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUi = new SecurityUiHelper(this);
        buildUi();
        startLoading();
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A1622);

        root.addView(buildHeader());
        root.addView(buildSearchRow());
        root.addView(buildFilterRow());
        root.addView(buildLoadingBar());

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0A1622);
        mListContainer = new LinearLayout(this);
        mListContainer.setOrientation(LinearLayout.VERTICAL);
        mListContainer.setPadding(dp(12), dp(8), dp(12), dp(40));
        sv.addView(mListContainer);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF0D1F33);
        int sbh = getStatusBarHeight();
        bar.setPadding(dp(14), sbh + dp(10), dp(14), dp(12));

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(20f);
        back.setTextColor(0xFF00BCD4);
        back.setPadding(0, 0, dp(12), 0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("Installed Apps");
        title.setTextSize(17f);
        title.setTextColor(0xFFE8F4FD);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title);

        mStatusText = new TextView(this);
        mStatusText.setText("Loading…");
        mStatusText.setTextSize(11f);
        mStatusText.setTextColor(0xFF8899AA);
        col.addView(mStatusText);

        bar.addView(col);
        return bar;
    }

    private View buildSearchRow() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setBackgroundColor(0xFF0D1F33);
        wrap.setPadding(dp(12), 0, dp(12), dp(10));

        EditText et = new EditText(this);
        et.setHint("Search app name or package…");
        et.setHintTextColor(0xFF445566);
        et.setTextColor(0xFFE8F4FD);
        et.setTextSize(13f);
        et.setSingleLine(true);
        et.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0xFF132030);
        etBg.setCornerRadius(dp(10));
        etBg.setStroke(dp(1), 0xFF1E3A50);
        et.setBackground(etBg);
        et.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int a) {
                mSearch = s.toString().toLowerCase().trim();
                applyFilter();
            }
            public void afterTextChanged(Editable e) {}
        });
        wrap.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private View buildFilterRow() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setBackgroundColor(0xFF0D1F33);
        wrap.setPadding(dp(12), 0, dp(12), dp(12));

        String[] labels = {"All", "High", "Medium", "Low"};
        int[]    values = {FILTER_ALL, FILTER_HIGH, FILTER_MEDIUM, FILTER_LOW};

        for (int i = 0; i < labels.length; i++) {
            final int fi = values[i];
            TextView btn = makeChip(labels[i], i == 0);
            btn.setOnClickListener(v -> setFilter(fi));
            mFilterBtns[i] = btn;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            wrap.addView(btn, lp);
        }

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        wrap.addView(spacer);

        mSysToggle = makeChip("System", true);
        mSysToggle.setOnClickListener(v -> {
            mShowSystem = !mShowSystem;
            updateSysToggle();
            applyFilter();
        });
        updateSysToggle();
        wrap.addView(mSysToggle);
        return wrap;
    }

    private View buildLoadingBar() {
        FrameLayout frame = new FrameLayout(this);
        frame.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        mProgress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        mProgress.setIndeterminate(true);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(3));
        mProgress.setLayoutParams(flp);
        frame.addView(mProgress);
        mLoadingBar = frame;
        return mLoadingBar;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void startLoading() {
        new Thread(this::loadApps).start();
    }

    private void loadApps() {
        mMain.post(() -> {
            mProgress.setVisibility(View.VISIBLE);
            mStatusText.setText("Fetching installed packages…");
        });

        PackageManager pm = getPackageManager();
        List<PackageInfo> pkgs;
        try {
            pkgs = pm.getInstalledPackages(
                    PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES);
        } catch (Exception e) {
            mMain.post(() -> showError("PackageManager failed: " + e.getMessage()));
            return;
        }

        mMain.post(() -> mStatusText.setText("Reading " + pkgs.size() + " apps via Shizuku…"));

        Set<String> runningPkgs  = new HashSet<>();
        Set<String> shizukuPkgs  = new HashSet<>();

        if (ShizukuCommandHelper.isAvailable()) {
            List<String> psList = ShizukuCommandHelper.getAllProcesses();
            for (String line : psList) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length > 0) {
                    String last = parts[parts.length - 1];
                    if (last.contains(".")) runningPkgs.add(last);
                }
            }
            List<String> pmList = ShizukuCommandHelper.listAllPackages();
            for (String line : pmList) {
                String pkg = line.trim();
                if (pkg.startsWith("package:")) {
                    String rest = pkg.substring(8);
                    if (rest.contains("=")) rest = rest.substring(rest.lastIndexOf('=') + 1);
                    shizukuPkgs.add(rest.trim());
                }
            }
        }

        List<AppSecurityInfo> result = new ArrayList<>();
        int total = pkgs.size();
        int idx   = 0;

        for (PackageInfo pi : pkgs) {
            idx++;
            if (idx % 30 == 0) {
                final String msg = "Analyzing " + idx + " / " + total + "…";
                mMain.post(() -> mStatusText.setText(msg));
            }

            AppSecurityInfo info = new AppSecurityInfo();
            info.packageName = pi.packageName;
            info.versionName = pi.versionName != null ? pi.versionName : "?";
            info.versionCode = pi.versionCode;
            info.installTime = pi.firstInstallTime;
            info.isSystemApp = (pi.applicationInfo != null)
                    && ((pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);

            try {
                info.appName = (String) pm.getApplicationLabel(pi.applicationInfo);
                info.icon    = pm.getApplicationIcon(pi.packageName);
            } catch (Exception e) {
                info.appName = pi.packageName;
                info.icon    = null;
            }

            analyzePermissions(info, pi);

            info.isRunning = runningPkgs.contains(pi.packageName);

            calculateRisk(info);

            result.add(info);
        }

        if (!shizukuPkgs.isEmpty()) {
            Set<String> knownPkgs = new HashSet<>();
            for (AppSecurityInfo a : result) knownPkgs.add(a.packageName);
            for (String pkg : shizukuPkgs) {
                if (!knownPkgs.contains(pkg)) {
                    AppSecurityInfo extra = new AppSecurityInfo();
                    extra.packageName = pkg;
                    extra.appName     = pkg;
                    extra.riskLevel   = AppSecurityInfo.RISK_LOW;
                    extra.riskScore   = 0;
                    result.add(extra);
                }
            }
        }

        Collections.sort(result, (a, b) -> {
            if (a.riskScore != b.riskScore) return b.riskScore - a.riskScore;
            String na = a.appName != null ? a.appName : a.packageName;
            String nb = b.appName != null ? b.appName : b.packageName;
            return na.compareToIgnoreCase(nb);
        });

        mAll = result;

        mMain.post(() -> {
            mProgress.setVisibility(View.GONE);
            applyFilter();
        });
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
                case android.Manifest.permission.READ_EXTERNAL_STORAGE:
                case android.Manifest.permission.WRITE_EXTERNAL_STORAGE:
                    info.hasStorage = true; break;
                case android.Manifest.permission.INTERNET:
                    info.hasInternet = true; break;
                case android.Manifest.permission.SYSTEM_ALERT_WINDOW:
                    info.hasOverlay = true; break;
                case android.Manifest.permission.RECEIVE_BOOT_COMPLETED:
                    info.hasBootReceiver = true; break;
                case "android.permission.BIND_ACCESSIBILITY_SERVICE":
                    info.hasAccessibility = true; break;
                case "android.permission.BIND_VPN_SERVICE":
                    info.hasVpn = true; break;
                case "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE":
                    info.hasNotificationAccess = true; break;
                case android.Manifest.permission.PROCESS_OUTGOING_CALLS:
                    info.hasProcessOutgoingCalls = true; break;
            }
        }
        if (pi.services != null) {
            for (android.content.pm.ServiceInfo si : pi.services) {
                if (si.permission == null) continue;
                if (si.permission.contains("ACCESSIBILITY")) info.hasAccessibility = true;
                if (si.permission.contains("VPN"))           info.hasVpn           = true;
                if (si.permission.contains("NOTIFICATION_LISTENER")) info.hasNotificationAccess = true;
            }
        }
    }

    private void calculateRisk(AppSecurityInfo info) {
        int score = 0;
        if (info.isSystemApp) {
            info.riskScore = 0;
            info.riskLevel = AppSecurityInfo.RISK_LOW;
            return;
        }
        if (info.hasCamera && info.hasInternet)      score += 25;
        if (info.hasMicrophone && info.hasInternet)  score += 25;
        if (info.hasLocation && info.hasInternet)    score += 20;
        if (info.hasAccessibility)                   score += 30;
        if (info.isDeviceAdmin)                      score += 40;
        if (info.hasVpn)                             score += 25;
        if (info.hasNotificationAccess)              score += 20;
        if (info.hasOverlay && !info.isSystemApp)    score += 15;
        if (info.hasSms && !info.isSystemApp)        score += 20;
        if (info.hasPhone)                           score += 12;
        if (info.hasContacts)                        score += 8;
        if (info.hasProcessOutgoingCalls)            score += 10;
        if (info.hasBootReceiver)                    score += 5;
        info.riskScore = Math.min(score, 100);
        if (info.riskScore >= 50)      info.riskLevel = AppSecurityInfo.RISK_HIGH;
        else if (info.riskScore >= 20) info.riskLevel = AppSecurityInfo.RISK_MEDIUM;
        else                           info.riskLevel = AppSecurityInfo.RISK_LOW;
    }

    // ── Filter logic ──────────────────────────────────────────────────────────

    private void setFilter(int filter) {
        mFilter = filter;
        for (int i = 0; i < mFilterBtns.length; i++) {
            boolean active = (i == filter);
            updateChip(mFilterBtns[i], chipLabel(i), active, chipColor(i));
        }
        applyFilter();
    }

    private String chipLabel(int i) {
        return new String[]{"All", "High", "Medium", "Low"}[i];
    }

    private int chipColor(int i) {
        switch (i) {
            case FILTER_HIGH:   return 0xFFE63946;
            case FILTER_MEDIUM: return 0xFFFF9F1C;
            case FILTER_LOW:    return 0xFF2ECC71;
            default:            return 0xFF00BCD4;
        }
    }

    private void updateSysToggle() {
        updateChip(mSysToggle, mShowSystem ? "System ✓" : "System", mShowSystem, 0xFF8899AA);
    }

    private void applyFilter() {
        mFiltered = new ArrayList<>();
        for (AppSecurityInfo app : mAll) {
            if (!mShowSystem && app.isSystemApp) continue;
            if (mFilter == FILTER_HIGH   && app.riskLevel != AppSecurityInfo.RISK_HIGH)   continue;
            if (mFilter == FILTER_MEDIUM && app.riskLevel != AppSecurityInfo.RISK_MEDIUM) continue;
            if (mFilter == FILTER_LOW    && app.riskLevel != AppSecurityInfo.RISK_LOW)    continue;
            if (!mSearch.isEmpty()) {
                String name = (app.appName != null ? app.appName : "").toLowerCase();
                String pkg  = app.packageName.toLowerCase();
                if (!name.contains(mSearch) && !pkg.contains(mSearch)) continue;
            }
            mFiltered.add(app);
        }
        renderList();
    }

    // ── Render list ───────────────────────────────────────────────────────────

    private void renderList() {
        mListContainer.removeAllViews();

        if (mAll.isEmpty()) {
            mListContainer.addView(centeredMsg("Loading…"));
            return;
        }

        mStatusText.setText(mFiltered.size() + " of " + mAll.size() + " apps");

        if (mFiltered.isEmpty()) {
            mListContainer.addView(centeredMsg("No apps match this filter."));
            return;
        }

        for (AppSecurityInfo app : mFiltered) {
            mListContainer.addView(buildAppRow(app));
            View div = new View(this);
            div.setBackgroundColor(0x14FFFFFF);
            div.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            mListContainer.addView(div);
        }
    }

    // ── App row card ──────────────────────────────────────────────────────────

    private View buildAppRow(AppSecurityInfo app) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(12), dp(4), dp(12));
        row.setBackgroundColor(Color.TRANSPARENT);

        GradientDrawable sel = new GradientDrawable();
        sel.setColor(0x18FFFFFF);
        sel.setCornerRadius(dp(8));

        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, AppDetailsActivity.class);
            intent.putExtra(AppDetailsActivity.EXTRA_PKG, app.packageName);
            startActivity(intent);
        });

        if (app.icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(app.icon);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
            ilp.rightMargin = dp(12);
            row.addView(iv, ilp);
        } else {
            View placeholder = new View(this);
            GradientDrawable ph = new GradientDrawable();
            ph.setShape(GradientDrawable.OVAL);
            ph.setColor(0xFF1E3A50);
            placeholder.setBackground(ph);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(44), dp(44));
            plp.rightMargin = dp(12);
            row.addView(placeholder, plp);
        }

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(this);
        String name = (app.appName != null && !app.appName.isEmpty())
                ? app.appName : app.packageName;
        tvName.setText(name);
        tvName.setTextSize(14f);
        tvName.setTextColor(0xFFE8F4FD);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setMaxLines(1);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        center.addView(tvName);

        TextView tvPkg = new TextView(this);
        tvPkg.setText(app.packageName);
        tvPkg.setTextSize(10f);
        tvPkg.setTextColor(0xFF55778A);
        tvPkg.setMaxLines(1);
        tvPkg.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams pkgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pkgLp.topMargin = dp(2);
        center.addView(tvPkg, pkgLp);

        LinearLayout dotsRow = new LinearLayout(this);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dotsLp.topMargin = dp(5);
        dotsRow.setLayoutParams(dotsLp);

        if (app.hasCamera)            addPermTag(dotsRow, "CAM",  0xFFE63946);
        if (app.hasMicrophone)        addPermTag(dotsRow, "MIC",  0xFFFF9F1C);
        if (app.hasLocation)          addPermTag(dotsRow, "LOC",  0xFF60A5FA);
        if (app.hasSms)               addPermTag(dotsRow, "SMS",  0xFF4CAF50);
        if (app.hasAccessibility)     addPermTag(dotsRow, "A11Y", 0xFFAB47BC);
        if (app.hasOverlay)           addPermTag(dotsRow, "OVL",  0xFFFF5252);
        if (app.hasVpn)               addPermTag(dotsRow, "VPN",  0xFF00BCD4);
        if (app.hasNotificationAccess)addPermTag(dotsRow, "NOTIF",0xFFFFEB3B);
        if (app.isRunning) {
            TextView live = new TextView(this);
            live.setText("● LIVE");
            live.setTextSize(8f);
            live.setTextColor(0xFF2ECC71);
            live.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams liveLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            liveLp.leftMargin = dp(4);
            live.setLayoutParams(liveLp);
            dotsRow.addView(live);
        }

        center.addView(dotsRow);
        row.addView(center);

        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(Gravity.CENTER | Gravity.END);

        int    riskColor = riskColor(app);
        String riskLabel = app.riskLevel == AppSecurityInfo.RISK_HIGH   ? "HIGH"
                         : app.riskLevel == AppSecurityInfo.RISK_MEDIUM ? "MED"
                         : "LOW";

        TextView tvScore = new TextView(this);
        tvScore.setText(String.valueOf(app.riskScore));
        tvScore.setTextSize(18f);
        tvScore.setTextColor(riskColor);
        tvScore.setTypeface(Typeface.DEFAULT_BOLD);
        tvScore.setGravity(Gravity.CENTER);
        rightCol.addView(tvScore);

        TextView tvRisk = new TextView(this);
        tvRisk.setText(riskLabel);
        tvRisk.setTextSize(9f);
        tvRisk.setTextColor(riskColor);
        tvRisk.setTypeface(Typeface.DEFAULT_BOLD);
        tvRisk.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rlLp.topMargin = dp(1);
        tvRisk.setLayoutParams(rlLp);
        GradientDrawable rlBg = new GradientDrawable();
        rlBg.setColor(riskColor & 0x22FFFFFF);
        rlBg.setCornerRadius(dp(4));
        tvRisk.setBackground(rlBg);
        tvRisk.setPadding(dp(6), dp(1), dp(6), dp(1));
        rightCol.addView(tvRisk);

        if (app.isSystemApp) {
            TextView sysBadge = new TextView(this);
            sysBadge.setText("SYS");
            sysBadge.setTextSize(8.5f);
            sysBadge.setTextColor(0xFF8899AA);
            sysBadge.setTypeface(Typeface.DEFAULT_BOLD);
            sysBadge.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            sbLp.topMargin = dp(3);
            sysBadge.setLayoutParams(sbLp);
            GradientDrawable sysBg = new GradientDrawable();
            sysBg.setColor(0x228899AA);
            sysBg.setCornerRadius(dp(4));
            sysBadge.setBackground(sysBg);
            sysBadge.setPadding(dp(5), dp(1), dp(5), dp(1));
            rightCol.addView(sysBadge);
        }

        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                dp(54), LinearLayout.LayoutParams.WRAP_CONTENT);
        rightLp.leftMargin = dp(8);
        row.addView(rightCol, rightLp);

        return row;
    }

    private void addPermTag(LinearLayout row, String label, int color) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(8f);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(4), dp(1), dp(4), dp(1));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color & 0x28FFFFFF);
        bg.setCornerRadius(dp(4));
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(3);
        tv.setLayoutParams(lp);
        row.addView(tv);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int riskColor(AppSecurityInfo app) {
        switch (app.riskLevel) {
            case AppSecurityInfo.RISK_HIGH:   return 0xFFE63946;
            case AppSecurityInfo.RISK_MEDIUM: return 0xFFFF9F1C;
            default:                          return 0xFF2ECC71;
        }
    }

    private TextView makeChip(String label, boolean active) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(12), dp(5), dp(12), dp(5));
        updateChip(tv, label, active, 0xFF00BCD4);
        return tv;
    }

    private void updateChip(TextView tv, String label, boolean active, int color) {
        tv.setText(label);
        GradientDrawable bg = new GradientDrawable();
        if (active) {
            bg.setColor(color & 0x33FFFFFF);
            bg.setStroke(dp(1), color);
            tv.setTextColor(color);
        } else {
            bg.setColor(0xFF132030);
            bg.setStroke(dp(1), 0xFF1E3A50);
            tv.setTextColor(0xFF8899AA);
        }
        bg.setCornerRadius(dp(20));
        tv.setBackground(bg);
    }

    private View centeredMsg(String msg) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(60), dp(24), dp(60));
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextSize(14f);
        tv.setTextColor(0xFF445566);
        tv.setGravity(Gravity.CENTER);
        box.addView(tv);
        return box;
    }

    private void showError(String msg) {
        mProgress.setVisibility(View.GONE);
        mStatusText.setText("Error");
        mListContainer.removeAllViews();
        mListContainer.addView(centeredMsg("Error loading apps:\n" + msg));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }
}
