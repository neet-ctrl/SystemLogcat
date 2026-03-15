package juloo.sysconsole;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * App Security Grid — 2-column tile view of all installed apps.
 * Tapping any tile opens AppDetailsActivity for the full detail screen.
 */
public class AppsListActivity extends Activity {

    private static final int SORT_NAME    = 0;
    private static final int SORT_RISK    = 1;
    private static final int SORT_INSTALL = 2;
    private static final int SORT_USED    = 3;

    private static final int BATCH_SIZE = 20;

    private SecurityUiHelper      mUi;
    private SecurityScanManager   mMgr;
    private LinearLayout          mListContainer;
    private List<AppSecurityInfo> mAll      = new ArrayList<>();
    private List<AppSecurityInfo> mFiltered = new ArrayList<>();
    private String                mFilter       = "";
    private int                   mRiskFilter   = -1;
    private int                   mSortMode     = SORT_RISK;
    private boolean               mSystemFilter = false;
    private int                   mRenderOffset    = 0;
    private int                   mRenderGeneration = 0;
    private final Handler         mHandler = new Handler(Looper.getMainLooper());

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
        mListContainer = new LinearLayout(this);
        mListContainer.setOrientation(LinearLayout.VERTICAL);
        mListContainer.setPadding(mUi.dp(10), mUi.dp(10), mUi.dp(10), mUi.dp(36));
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
        titleCol.addView(mUi.label("App Security Audit", 17f, SecurityUiHelper.CLR_TEXT, true));
        titleCol.addView(mUi.label(mAll.size() + " apps  ·  tap any tile to view details",
                11f, SecurityUiHelper.CLR_SECONDARY, false));
        bar.addView(titleCol);

        Button sort = new Button(this);
        sort.setText("⇅");
        sort.setTextSize(15f);
        sort.setTextColor(SecurityUiHelper.CLR_TEAL);
        sort.setBackground(null);
        sort.setAllCaps(false);
        sort.setOnClickListener(v -> showSortDialog());
        bar.addView(sort);

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
                mHandler.postDelayed(() -> applyFilterAndSort(), 200);
            }
            public void afterTextChanged(Editable e) {}
        });
        wrapper.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return wrapper;
    }

    // ── Filter / sort bar ─────────────────────────────────────────────────────

    private View buildFilterBar() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setBackgroundColor(SecurityUiHelper.CLR_NAV_BG);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);
        wrapper.setPadding(mUi.dp(14), 0, mUi.dp(14), mUi.dp(12));

        addFilterChip(wrapper, "All",       -1);
        addFilterChip(wrapper, "🔴 High",   AppSecurityInfo.RISK_HIGH);
        addFilterChip(wrapper, "🟠 Medium", AppSecurityInfo.RISK_MEDIUM);
        addFilterChip(wrapper, "🟢 Low",    AppSecurityInfo.RISK_LOW);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        wrapper.addView(spacer);

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
                  : riskVal == AppSecurityInfo.RISK_LOW    ? SecurityUiHelper.CLR_GREEN
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
        String[] options = {
            "Name (A–Z)",
            "Risk Score (High first)",
            "Install Date (Newest first)",
            "Last Used (Recent first)"
        };
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
            case SORT_NAME:
                Collections.sort(mFiltered, (a, b) -> {
                    String na = a.appName != null ? a.appName : a.packageName;
                    String nb = b.appName != null ? b.appName : b.packageName;
                    return na.compareToIgnoreCase(nb);
                }); break;
            case SORT_RISK:
                Collections.sort(mFiltered,
                    (a, b) -> Integer.compare(b.riskScore, a.riskScore)); break;
            case SORT_INSTALL:
                Collections.sort(mFiltered,
                    (a, b) -> Long.compare(b.installTime, a.installTime)); break;
            case SORT_USED:
                Collections.sort(mFiltered,
                    (a, b) -> Long.compare(b.lastUsed, a.lastUsed)); break;
        }
        rebuildList();
    }

    private void rebuildList() {
        mListContainer.removeAllViews();
        mRenderOffset = 0;
        final int gen = ++mRenderGeneration;

        if (mFiltered.isEmpty()) {
            LinearLayout empty = mUi.card(16, 40);
            empty.setGravity(Gravity.CENTER);
            TextView tv = mUi.label(
                    mAll.isEmpty()
                        ? "No scan data yet.\nRun a Deep Scan from the Dashboard first."
                        : "No apps match your search or filter.",
                    13f, SecurityUiHelper.CLR_SECONDARY, false);
            tv.setGravity(Gravity.CENTER);
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            empty.addView(tv);
            mListContainer.addView(empty);
            return;
        }

        TextView countHint = mUi.label(
                mFiltered.size() + " apps  ·  sorted by "
                + new String[]{"name","risk","install date","last used"}[mSortMode],
                11f, SecurityUiHelper.CLR_SECONDARY, false);
        countHint.setPadding(mUi.dp(4), mUi.dp(2), 0, mUi.dp(8));
        mListContainer.addView(countHint);

        renderNextBatch(gen);
    }

    private void renderNextBatch(int gen) {
        if (gen != mRenderGeneration) return;
        int batchEnd = Math.min(mRenderOffset + BATCH_SIZE, mFiltered.size());
        int i = mRenderOffset;
        while (i < batchEnd) {
            AppSecurityInfo a1 = mFiltered.get(i++);
            AppSecurityInfo a2 = (i < mFiltered.size()) ? mFiltered.get(i++) : null;
            try {
                mListContainer.addView(buildAppRow(a1, a2));
                mListContainer.addView(mUi.spacer(8));
            } catch (Exception ignored) {}
        }
        mRenderOffset = i;
        if (mRenderOffset < mFiltered.size()) {
            mHandler.post(() -> renderNextBatch(gen));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2-column tile row
    // ═════════════════════════════════════════════════════════════════════════

    private View buildAppRow(AppSecurityInfo a1, AppSecurityInfo a2) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = mUi.dp(5);
        row.addView(buildAppTile(a1), lp1);

        if (a2 != null) {
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(buildAppTile(a2), lp2);
        } else {
            View spacer = new View(this);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
            row.addView(spacer);
        }
        return row;
    }

    // ── App tile card ─────────────────────────────────────────────────────────

    private View buildAppTile(AppSecurityInfo app) {
        int riskColor = riskColor(app);
        int bgColor   = riskBg(app);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(mUi.dp(11), mUi.dp(12), mUi.dp(11), mUi.dp(11));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(mUi.dp(14));
        if (app.isSuspectedSpyware) {
            bg.setStroke(mUi.dp(2), 0xCCE63946);
        } else if (app.riskLevel == AppSecurityInfo.RISK_HIGH) {
            bg.setStroke(mUi.dp(1), 0x99E63946);
        } else if (app.riskLevel == AppSecurityInfo.RISK_MEDIUM) {
            bg.setStroke(mUi.dp(1), 0x88FF9F1C);
        } else if (app.isBankingApp) {
            bg.setStroke(mUi.dp(1), 0x5560A5FA);
        }
        card.setBackground(bg);

        // ── Row 1: icon + name/package ────────────────────────────────────
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.TOP);

        if (app.icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(app.icon);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    mUi.dp(40), mUi.dp(40));
            ilp.rightMargin = mUi.dp(9);
            topRow.addView(iv, ilp);
        }

        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        nameCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String name = (app.appName != null && !app.appName.isEmpty())
                ? app.appName : app.packageName;
        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextSize(13f);
        tvName.setTextColor(SecurityUiHelper.CLR_TEXT);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setMaxLines(1);
        tvName.setEllipsize(TextUtils.TruncateAt.END);
        nameCol.addView(tvName);

        TextView tvPkg = new TextView(this);
        tvPkg.setText(app.packageName);
        tvPkg.setTextSize(9f);
        tvPkg.setTextColor(SecurityUiHelper.CLR_SECONDARY);
        tvPkg.setMaxLines(1);
        tvPkg.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams pkgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pkgLp.topMargin = mUi.dp(1);
        nameCol.addView(tvPkg, pkgLp);

        // Tag badges row (Banking / System / Admin / Spyware)
        LinearLayout tagsRow = new LinearLayout(this);
        tagsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tagsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tagsLp.topMargin = mUi.dp(4);
        tagsRow.setLayoutParams(tagsLp);

        if (app.isSuspectedSpyware) {
            tagsRow.addView(miniTag("SPYWARE", 0xFFE63946));
        } else if (app.isBankingApp) {
            tagsRow.addView(miniTag("Banking", 0xFF60A5FA));
        } else if (app.isSystemApp) {
            tagsRow.addView(miniTag("System", SecurityUiHelper.CLR_SECONDARY));
        }
        if (app.isDeviceAdmin) {
            tagsRow.addView(miniTagGap(miniTag("Admin", 0xFFE63946)));
        }
        nameCol.addView(tagsRow);
        topRow.addView(nameCol);
        card.addView(topRow);

        // ── Divider ───────────────────────────────────────────────────────
        card.addView(mUi.spacer(10));
        View divider = new View(this);
        divider.setBackgroundColor(0x18FFFFFF);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, mUi.dp(1)));
        card.addView(divider);
        card.addView(mUi.spacer(8));

        // ── Row 2: risk score + permission dots + running indicator ───────
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);

        // Risk score pill
        TextView tvScore = new TextView(this);
        tvScore.setText(String.valueOf(app.riskScore));
        tvScore.setTextSize(11f);
        tvScore.setTextColor(riskColor);
        tvScore.setTypeface(Typeface.DEFAULT_BOLD);
        tvScore.setPadding(mUi.dp(7), mUi.dp(2), mUi.dp(7), mUi.dp(2));
        GradientDrawable scoreBg = new GradientDrawable();
        scoreBg.setColor(riskColor & 0x33FFFFFF);
        scoreBg.setCornerRadius(mUi.dp(8));
        tvScore.setBackground(scoreBg);
        bottomRow.addView(tvScore);

        // Risk label
        TextView tvRisk = new TextView(this);
        String rlabel = app.isSuspectedSpyware ? "Spyware"
                : app.riskLevel == AppSecurityInfo.RISK_HIGH   ? "High"
                : app.riskLevel == AppSecurityInfo.RISK_MEDIUM ? "Medium"
                : app.riskLevel == AppSecurityInfo.RISK_LOW    ? "Low"
                : "Clean";
        tvRisk.setText(rlabel);
        tvRisk.setTextSize(9f);
        tvRisk.setTextColor(riskColor);
        LinearLayout.LayoutParams rlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rlLp.leftMargin = mUi.dp(4);
        tvRisk.setLayoutParams(rlLp);
        bottomRow.addView(tvRisk);

        // Flex spacer
        View flex = new View(this);
        flex.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        bottomRow.addView(flex);

        // Permission dots (camera=red, mic=orange, location=blue, sms=amber)
        addPermDot(bottomRow, app.hasCamera,        0xFFE63946);
        addPermDot(bottomRow, app.hasMicrophone,     0xFFFF9F1C);
        addPermDot(bottomRow, app.hasLocation,       0xFF60A5FA);
        addPermDot(bottomRow, app.hasSms,            0xFF4CAF50);
        addPermDot(bottomRow, app.hasAccessibility,  0xFFAB47BC);
        addPermDot(bottomRow, app.hasOverlay,        0xFFFF5252);

        // Running indicator
        if (app.isRunning) {
            TextView live = new TextView(this);
            live.setText("⬤");
            live.setTextSize(7f);
            live.setTextColor(0xFF4CAF50);
            LinearLayout.LayoutParams liveLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            liveLp.leftMargin = mUi.dp(4);
            live.setLayoutParams(liveLp);
            bottomRow.addView(live);
        }

        card.addView(bottomRow);

        // ── Tap to open detail screen ─────────────────────────────────────
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, AppDetailsActivity.class);
            intent.putExtra(AppDetailsActivity.EXTRA_PKG, app.packageName);
            startActivity(intent);
        });

        return card;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int riskColor(AppSecurityInfo app) {
        if (app.isSuspectedSpyware)                          return 0xFFE63946;
        if (app.riskLevel == AppSecurityInfo.RISK_HIGH)      return 0xFFFF6B6B;
        if (app.riskLevel == AppSecurityInfo.RISK_MEDIUM)    return SecurityUiHelper.CLR_ORANGE;
        if (app.riskLevel == AppSecurityInfo.RISK_LOW)       return SecurityUiHelper.CLR_GREEN;
        return SecurityUiHelper.CLR_SECONDARY;
    }

    private int riskBg(AppSecurityInfo app) {
        if (app.isSuspectedSpyware)                          return 0xFF200A0A;
        if (app.riskLevel == AppSecurityInfo.RISK_HIGH)      return 0xFF1C0E0E;
        if (app.riskLevel == AppSecurityInfo.RISK_MEDIUM)    return 0xFF1C1508;
        if (app.riskLevel == AppSecurityInfo.RISK_LOW)       return 0xFF0D1C0D;
        return SecurityUiHelper.CLR_CARD;
    }

    private void addPermDot(LinearLayout row, boolean show, int color) {
        if (!show) return;
        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        dot.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                mUi.dp(6), mUi.dp(6));
        lp.leftMargin = mUi.dp(2);
        dot.setLayoutParams(lp);
        row.addView(dot);
    }

    private TextView miniTag(String label, int color) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(8.5f);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(mUi.dp(5), mUi.dp(1), mUi.dp(5), mUi.dp(1));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color & 0x28FFFFFF);
        bg.setCornerRadius(mUi.dp(6));
        tv.setBackground(bg);
        return tv;
    }

    private View miniTagGap(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = mUi.dp(4);
        v.setLayoutParams(lp);
        return v;
    }
}
