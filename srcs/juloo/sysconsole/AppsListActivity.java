package juloo.sysconsole;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import java.util.ArrayList;
import java.util.List;

public class AppsListActivity extends Activity {

    private SecurityUiHelper    mUi;
    private SecurityScanManager mMgr;
    private LinearLayout        mListContainer;
    private List<AppSecurityInfo> mAll = new ArrayList<>();
    private String              mFilter = "";
    private int                 mRiskFilter = -1;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        mMgr = SecurityHub.getManager(this);
        mUi  = new SecurityUiHelper(this);
        mAll = mMgr.getCachedApps();
        buildUi();
    }

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
        mListContainer.setPadding(mUi.dp(16), mUi.dp(8), mUi.dp(16), mUi.dp(32));
        sv.addView(mListContainer);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        refreshList();
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

        TextView title = mUi.label("All Apps", 17f, SecurityUiHelper.CLR_TEXT, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(title, lp);

        int total = mAll.size();
        TextView count = mUi.label(total + " apps", 12f,
                SecurityUiHelper.CLR_SECONDARY, false);
        bar.addView(count);
        return bar;
    }

    private View buildSearchBar() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setBackgroundColor(SecurityUiHelper.CLR_NAV_BG);
        wrapper.setPadding(mUi.dp(16), 0, mUi.dp(16), mUi.dp(10));

        EditText et = new EditText(this);
        et.setHint("Search apps…");
        et.setHintTextColor(0xFF475569);
        et.setTextColor(SecurityUiHelper.CLR_TEXT);
        et.setTextSize(13f);
        et.setBackground(null);
        et.setPadding(mUi.dp(14), mUi.dp(10), mUi.dp(14), mUi.dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SecurityUiHelper.CLR_CARD);
        bg.setCornerRadius(mUi.dp(10));
        et.setBackground(bg);

        et.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int s, int co, int a) {}
            public void onTextChanged(CharSequence c, int s, int b, int a) {
                mFilter = c.toString().toLowerCase().trim();
                refreshList();
            }
            public void afterTextChanged(Editable e) {}
        });

        wrapper.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return wrapper;
    }

    private View buildFilterBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(SecurityUiHelper.CLR_NAV_BG);
        bar.setPadding(mUi.dp(16), 0, mUi.dp(16), mUi.dp(12));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        addFilterChip(bar, "All",    -1,                            0xFF334155);
        addFilterChip(bar, "High",   AppSecurityInfo.RISK_HIGH,   0xFFE63946);
        addFilterChip(bar, "Medium", AppSecurityInfo.RISK_MEDIUM, 0xFFFF9F1C);
        addFilterChip(bar, "Low",    AppSecurityInfo.RISK_LOW,    0xFF00A896);

        return bar;
    }

    private void addFilterChip(LinearLayout bar, String label, int riskLevel, int color) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(11f);
        btn.setTextColor(0xFFFFFFFF);
        btn.setAllCaps(false);
        int ph = mUi.dp(12), pv = mUi.dp(5);
        btn.setPadding(ph, pv, ph, pv);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(mUi.dp(20));
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = mUi.dp(8);

        btn.setOnClickListener(v -> {
            mRiskFilter = riskLevel;
            refreshList();
        });
        bar.addView(btn, lp);
    }

    private void refreshList() {
        mListContainer.removeAllViews();
        List<AppSecurityInfo> filtered = new ArrayList<>();
        for (AppSecurityInfo a : mAll) {
            if (!mFilter.isEmpty()
                    && !a.appName.toLowerCase().contains(mFilter)
                    && !a.packageName.toLowerCase().contains(mFilter)) continue;
            if (mRiskFilter >= 0 && a.riskLevel != mRiskFilter) continue;
            filtered.add(a);
        }

        if (filtered.isEmpty()) {
            mListContainer.addView(mUi.label(
                    "No apps match your filter.", 13f, SecurityUiHelper.CLR_SECONDARY, false));
            return;
        }

        for (AppSecurityInfo app : filtered) {
            mListContainer.addView(buildAppCard(app));
            mListContainer.addView(mUi.spacer(8));
        }
    }

    private View buildAppCard(AppSecurityInfo app) {
        LinearLayout card = mUi.card(12, 12);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            Intent i = new Intent(this, AppDetailsActivity.class);
            i.putExtra(AppDetailsActivity.EXTRA_PKG, app.packageName);
            startActivity(i);
        });

        LinearLayout row = mUi.row(true);

        if (app.icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(app.icon);
            int sz = mUi.dp(40);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, sz);
            ilp.rightMargin = mUi.dp(12);
            row.addView(iv, ilp);
        } else {
            TextView em = mUi.label("📦", 24f, SecurityUiHelper.CLR_SECONDARY, false);
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                    mUi.dp(40), mUi.dp(40));
            elp.rightMargin = mUi.dp(12);
            row.addView(em, elp);
        }

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tpLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(tpLp);

        textCol.addView(mUi.label(app.appName, 14f, SecurityUiHelper.CLR_TEXT, true));

        TextView tvPkg = mUi.label(app.packageName, 10f, SecurityUiHelper.CLR_SECONDARY, false);
        tvPkg.setPadding(0, mUi.dp(1), 0, 0);
        textCol.addView(tvPkg);

        textCol.addView(mUi.spacer(5));

        LinearLayout permRow = buildPermIconRow(app);
        textCol.addView(permRow);

        row.addView(textCol);

        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rcLp.leftMargin = mUi.dp(8);
        rightCol.setLayoutParams(rcLp);

        rightCol.addView(mUi.riskBadge(app));
        rightCol.addView(mUi.spacer(4));

        TextView tvScore = mUi.label(app.riskScore + "/100", 11f, app.riskColor(), true);
        tvScore.setGravity(Gravity.END);
        rightCol.addView(tvScore);

        if (app.isRunning) {
            rightCol.addView(mUi.spacer(4));
            rightCol.addView(mUi.badge("Running",
                    SecurityUiHelper.CLR_TEAL & 0x33FFFFFF | 0xFF000000,
                    SecurityUiHelper.CLR_TEAL, 20));
        }

        row.addView(rightCol);
        card.addView(row);
        return card;
    }

    private LinearLayout buildPermIconRow(AppSecurityInfo app) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        if (app.hasCamera)     addPermIcon(row, "📷", app.cameraUsedRecently);
        if (app.hasMicrophone) addPermIcon(row, "🎙", app.micUsedRecently);
        if (app.hasLocation)   addPermIcon(row, "📍", app.locationUsedRecently);
        if (app.hasContacts)   addPermIcon(row, "👥", false);
        if (app.hasSms)        addPermIcon(row, "💬", false);
        if (app.hasAccessibility) addPermIcon(row, "♿", false);
        if (app.hasOverlay)    addPermIcon(row, "🔲", false);

        return row;
    }

    private void addPermIcon(LinearLayout row, String emoji, boolean recentUse) {
        TextView tv = new TextView(this);
        tv.setText(emoji);
        tv.setTextSize(11f);

        if (recentUse) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x33E63946);
            bg.setCornerRadius(mUi.dp(4));
            tv.setBackground(bg);
            tv.setPadding(mUi.dp(2), mUi.dp(1), mUi.dp(2), mUi.dp(1));
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = mUi.dp(3);
        row.addView(tv, lp);
    }
}
