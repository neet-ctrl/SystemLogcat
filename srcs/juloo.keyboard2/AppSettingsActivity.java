package juloo.keyboard2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public class AppSettingsActivity extends Activity {

    private ThemeManager.ThemeColors C;
    private String mCreatedWithSig;
    private float D;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        mCreatedWithSig = ThemeManager.signature(this);
        C = ThemeManager.colors(this);
        D = getResources().getDisplayMetrics().density;
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!ThemeManager.signature(this).equals(mCreatedWithSig)) recreate();
    }

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C.background);

        // ── Header ──────────────────────────────────────────────
        root.addView(buildHeader());

        // ── Appearance Section ──────────────────────────────────
        root.addView(sectionLabel("🎨  Appearance"));
        root.addView(buildThemeSelector());
        root.addView(buildMatrixSection());

        // ── Smart Clips Section ─────────────────────────────────
        root.addView(sectionLabel("🔐  Smart Clips"));
        root.addView(buildAutoLockRow());
        root.addView(buildToggleRow(
                "Show Serial No. in Widget",
                "Display clip number badge in home widget",
                ThemeManager.KEY_SHOW_SERIAL, true));
        root.addView(buildClipLimitRow());

        // ── Clipboard Section ───────────────────────────────────
        root.addView(sectionLabel("📋  Clipboard"));
        root.addView(buildClipboardLimitRow());

        // ── Behaviour Section ───────────────────────────────────
        root.addView(sectionLabel("⚙  Behaviour"));
        root.addView(buildToggleRow(
                "Haptic Feedback",
                "Vibrate on key press (requires keyboard restart)",
                ThemeManager.KEY_HAPTIC, true));

        // ── About Section ───────────────────────────────────────
        root.addView(sectionLabel("ℹ  About"));
        root.addView(buildAboutCard());

        scroll.addView(root);
        setContentView(scroll);
        ThemeManager.attachMatrixOverlay(this);
    }

    // ── Header ───────────────────────────────────────────────────────────────

    private View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setBackgroundColor(C.headerBg);
        h.setPadding(dp(20), dp(20), dp(16), dp(20));
        h.setGravity(Gravity.CENTER_VERTICAL);

        Button back = new Button(this);
        back.setText("←");
        back.setTextColor(C.headerText);
        back.setTextSize(18);
        back.setPadding(0, 0, dp(8), 0);
        back.setBackground(null);
        back.setOnClickListener(v -> finish());
        h.addView(back);

        TextView title = new TextView(this);
        title.setText("App Settings");
        title.setTextSize(18);
        title.setTextColor(C.headerText);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        h.addView(title, tp);

        if (ThemeManager.isMatrixMode(this)) {
            TextView badge = new TextView(this);
            badge.setText("[MATRIX]");
            badge.setTextColor(C.primary);
            badge.setTextSize(10);
            badge.setTypeface(Typeface.MONOSPACE);
            h.addView(badge);
        }

        return h;
    }

    // ── Theme selector ────────────────────────────────────────────────────────

    private View buildThemeSelector() {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);

        TextView label = rowLabel("Theme");
        card.addView(label);

        TextView sub = rowSub("Controls the overall color scheme of Smart Clips & App Settings");
        card.addView(sub);

        String[] opts  = {"system", "light", "dark"};
        String[] labels = {"System", "Light", "Dark"};
        String current = ThemeManager.getTheme(this);

        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, dp(12), 0, 0);
        seg.setLayoutParams(sp);

        for (int i = 0; i < opts.length; i++) {
            final String val = opts[i];
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setTextSize(13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMargins(dp(6), 0, 0, 0);
            b.setLayoutParams(lp);
            applySegBtn(b, val.equals(current));
            b.setOnClickListener(v -> {
                ThemeManager.prefs(this).edit().putString(ThemeManager.KEY_THEME, val).apply();
                recreate();
            });
            seg.addView(b);
        }
        card.addView(seg);
        return card;
    }

    // ── Matrix section ────────────────────────────────────────────────────────

    private View buildMatrixSection() {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        textCol.addView(rowLabel("◼ Matrix Mode"));
        textCol.addView(rowSub("Digital rain overlay · green-on-black aesthetic"));
        topRow.addView(textCol);

        Switch matrixSwitch = new Switch(this);
        matrixSwitch.setChecked(ThemeManager.isMatrixMode(this));
        matrixSwitch.setOnCheckedChangeListener((v, on) -> {
            ThemeManager.prefs(this).edit().putBoolean(ThemeManager.KEY_MATRIX, on).apply();
            recreate();
        });
        topRow.addView(matrixSwitch);
        card.addView(topRow);

        // Speed row (always shown, more relevant when matrix on)
        View divider = makeDivider();
        card.addView(divider);

        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.VERTICAL);
        speedRow.addView(rowLabel("Rain Speed"));
        speedRow.addView(rowSub("How fast the characters fall"));

        String[] speedOpts = {"slow", "normal", "fast"};
        String[] speedLabels = {"Slow", "Normal", "Fast"};
        String curSpeed = ThemeManager.getMatrixSpeed(this);

        LinearLayout speedSeg = new LinearLayout(this);
        speedSeg.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ssLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ssLp.setMargins(0, dp(10), 0, 0);
        speedSeg.setLayoutParams(ssLp);
        for (int i = 0; i < speedOpts.length; i++) {
            final String val = speedOpts[i];
            Button b = new Button(this);
            b.setText(speedLabels[i]);
            b.setTextSize(12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMargins(dp(5), 0, 0, 0);
            b.setLayoutParams(lp);
            applySegBtn(b, val.equals(curSpeed));
            b.setOnClickListener(v -> {
                ThemeManager.prefs(this).edit().putString(ThemeManager.KEY_MATRIX_SPEED, val).apply();
                recreate();
            });
            speedSeg.addView(b);
        }
        speedRow.addView(speedSeg);
        card.addView(speedRow);

        // Density row
        View divider2 = makeDivider();
        card.addView(divider2);

        LinearLayout densityRow = new LinearLayout(this);
        densityRow.setOrientation(LinearLayout.VERTICAL);
        densityRow.addView(rowLabel("Rain Density"));
        densityRow.addView(rowSub("Number of active character columns"));

        String[] densOpts = {"low", "medium", "high"};
        String[] densLabels = {"Low", "Medium", "High"};
        String curDens = ThemeManager.getMatrixDensity(this);

        LinearLayout densSeg = new LinearLayout(this);
        densSeg.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams dsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dsLp.setMargins(0, dp(10), 0, 0);
        densSeg.setLayoutParams(dsLp);
        for (int i = 0; i < densOpts.length; i++) {
            final String val = densOpts[i];
            Button b = new Button(this);
            b.setText(densLabels[i]);
            b.setTextSize(12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMargins(dp(5), 0, 0, 0);
            b.setLayoutParams(lp);
            applySegBtn(b, val.equals(curDens));
            b.setOnClickListener(v -> {
                ThemeManager.prefs(this).edit().putString(ThemeManager.KEY_MATRIX_DENSITY, val).apply();
                recreate();
            });
            densSeg.addView(b);
        }
        densityRow.addView(densSeg);
        card.addView(densityRow);

        return card;
    }

    // ── Auto-lock row ─────────────────────────────────────────────────────────

    private View buildAutoLockRow() {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(rowLabel("Auto-lock Timer"));
        card.addView(rowSub("Duration Smart Clips stays unlocked after PIN entry"));

        int[] mins   = {5, 10, 30, -1};
        String[] lbls = {"5 min", "10 min", "30 min", "Never"};
        int cur = ThemeManager.getAutoLockMins(this);

        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, dp(12), 0, 0);
        seg.setLayoutParams(sp);
        for (int i = 0; i < mins.length; i++) {
            final int val = mins[i];
            Button b = new Button(this);
            b.setText(lbls[i]);
            b.setTextSize(11);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMargins(dp(5), 0, 0, 0);
            b.setLayoutParams(lp);
            applySegBtn(b, val == cur);
            b.setOnClickListener(v -> {
                ThemeManager.prefs(this).edit().putInt(ThemeManager.KEY_AUTO_LOCK, val).apply();
                recreate();
            });
            seg.addView(b);
        }
        card.addView(seg);
        return card;
    }

    // ── Clip widget limit row ─────────────────────────────────────────────────

    private View buildClipLimitRow() {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(rowLabel("Clips Shown in Widget"));
        card.addView(rowSub("Maximum Smart Clips displayed on home screen widget"));

        int[] vals   = {10, 20, 50};
        String[] lbls = {"10", "20", "50"};
        int cur = ThemeManager.getClipWidgetLimit(this);

        LinearLayout seg = buildSegRow(vals, lbls, cur, ThemeManager.KEY_CLIP_LIMIT);
        card.addView(seg);
        return card;
    }

    // ── Clipboard history limit ───────────────────────────────────────────────

    private View buildClipboardLimitRow() {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(rowLabel("History Limit"));
        card.addView(rowSub("Maximum clipboard entries stored"));

        int[] vals   = {50, 100, 200, 500};
        String[] lbls = {"50", "100", "200", "500"};
        int cur = ThemeManager.getClipboardLimit(this);

        LinearLayout seg = buildSegRow(vals, lbls, cur, ThemeManager.KEY_CB_LIMIT);
        card.addView(seg);
        return card;
    }

    // ── Generic toggle row ────────────────────────────────────────────────────

    private View buildToggleRow(String label, String sub, String prefKey, boolean defVal) {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        textCol.addView(rowLabel(label));
        textCol.addView(rowSub(sub));
        card.addView(textCol);

        Switch sw = new Switch(this);
        sw.setChecked(ThemeManager.prefs(this).getBoolean(prefKey, defVal));
        sw.setOnCheckedChangeListener((v, on) ->
                ThemeManager.prefs(this).edit().putBoolean(prefKey, on).apply());
        card.addView(sw);

        return card;
    }

    // ── About card ────────────────────────────────────────────────────────────

    private View buildAboutCard() {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);

        TextView appName = new TextView(this);
        appName.setText("UnBelievable Keyboard");
        appName.setTextColor(C.primary);
        appName.setTextSize(15);
        appName.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(appName);

        TextView desc = new TextView(this);
        desc.setText("Open-source Android IME with Smart Clips, formula expansion,\nMatrix mode, clipboard history, and developer tools.");
        desc.setTextColor(C.textSecondary);
        desc.setTextSize(12);
        desc.setPadding(0, dp(6), 0, dp(12));
        card.addView(desc);

        View div = makeDivider();
        card.addView(div);

        Button repoBtn = new Button(this);
        repoBtn.setText("⬡  View Source on GitHub");
        repoBtn.setTextColor(C.primary);
        repoBtn.setTextSize(13);
        repoBtn.setBackground(null);
        repoBtn.setPadding(0, dp(12), 0, 0);
        repoBtn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/Shakti-ctrl/UnBelievable-Keyboard")));
            } catch (Exception ignored) {}
        });
        card.addView(repoBtn);

        return card;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LinearLayout makeCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C.surface);
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), 0, dp(16), dp(10));
        c.setLayoutParams(lp);
        if (Build.VERSION.SDK_INT >= 21) c.setElevation(dp(2));
        return c;
    }

    private View sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(C.primary);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setAllCaps(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(20), dp(18), dp(20), dp(6));
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView rowLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(C.textPrimary);
        tv.setTextSize(14);
        tv.setTypeface(ThemeManager.isMatrixMode(this) ? Typeface.MONOSPACE : Typeface.DEFAULT_BOLD);
        return tv;
    }

    private TextView rowSub(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(C.textSecondary);
        tv.setTextSize(11);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(2), 0, 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private View makeDivider() {
        View v = new View(this);
        v.setBackgroundColor(C.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(0, dp(12), 0, dp(12));
        v.setLayoutParams(lp);
        return v;
    }

    private void applySegBtn(Button b, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? C.primary : C.surfaceVariant);
        bg.setCornerRadius(dp(8));
        b.setBackground(bg);
        b.setTextColor(selected ? C.headerText : C.textSecondary);
        b.setPadding(dp(4), dp(8), dp(4), dp(8));
        b.setMinWidth(0);
        b.setMinHeight(0);
    }

    private LinearLayout buildSegRow(int[] vals, String[] lbls, int cur, String key) {
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, dp(12), 0, 0);
        seg.setLayoutParams(sp);
        for (int i = 0; i < vals.length; i++) {
            final int val = vals[i];
            Button b = new Button(this);
            b.setText(lbls[i]);
            b.setTextSize(12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMargins(dp(5), 0, 0, 0);
            b.setLayoutParams(lp);
            applySegBtn(b, val == cur);
            b.setOnClickListener(v -> {
                ThemeManager.prefs(this).edit().putInt(key, val).apply();
                recreate();
            });
            seg.addView(b);
        }
        return seg;
    }

    private int dp(int v) { return (int)(v * D); }
}
