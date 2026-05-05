package juloo.keyboard2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Full-screen interactive tutorial shown on first launch and accessible
 * any time via the 📖 button on the launcher home screen.
 *
 * Navigation: Back / Next buttons + swipe-progress dots.
 * First install: auto-shown, with a visible Skip option.
 */
public class TutorialActivity extends Activity {

    private static final String PREFS_NAME = "app_ui_prefs";
    private static final String KEY_SHOWN  = "tutorial_shown_v1";

    /** Call from LauncherActivity.onCreate — shows tutorial if first launch. */
    public static void showIfFirstLaunch(Activity host) {
        SharedPreferences p = host.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!p.getBoolean(KEY_SHOWN, false)) {
            host.startActivity(new android.content.Intent(host, TutorialActivity.class));
        }
    }

    /** Mark tutorial as seen (persists across installs of the same data). */
    public static void markSeen(Context ctx) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
           .edit().putBoolean(KEY_SHOWN, true).apply();
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private ThemeManager.ThemeColors C;
    private float D; // dp multiplier
    private int _page = 0;

    private FrameLayout _pageContainer;
    private LinearLayout _dotsRow;
    private Button  _btnBack;
    private Button  _btnNext;
    private TextView _btnSkip;

    // ── Tutorial steps ────────────────────────────────────────────────────────

    private static final Step[] STEPS = {
        new Step("👋", "Welcome to UnBelievable Keyboard!",
            "This quick tour shows you everything the app can do — from basic typing tricks to powerful Smart Clips and more.\n\n" +
            "Take your time. You can swipe through at your own pace, or skip to the end and come back later anytime.",
            new Tip[]{
                new Tip("📖", "Tap the book icon on the home screen to re-open this tutorial anytime"),
                new Tip("⏩", "Hit Skip at the top-right to jump straight in and explore on your own"),
            }),

        new Step("⌨️", "Step 1 — Enable the Keyboard",
            "Before you can use the keyboard, you need to enable it in Android Settings. Here's exactly how:",
            new Tip[]{
                new Tip("1️⃣", "Tap  Enable Keyboard  on the home screen (or go to Settings → System → Language & Input → On-screen keyboard)"),
                new Tip("2️⃣", "Find  UnBelievable Keyboard  in the list and turn it ON"),
                new Tip("3️⃣", "Tap  Switch Keyboard  on the home screen and pick  UnBelievable Keyboard  as your active keyboard"),
                new Tip("✅", "The keyboard is now active everywhere on your phone — in any app, any text box!"),
            }),

        new Step("👆", "Step 2 — Swipe to Type Extra Symbols",
            "Every key on the keyboard hides extra characters. Instead of switching layouts, just swipe!",
            new Tip[]{
                new Tip("↗", "Swipe UP-RIGHT on  Q  →  gets you  1  (number)"),
                new Tip("↙", "Swipe DOWN-LEFT on  Q  →  gets you  Esc"),
                new Tip("↑", "Swipe UP on  Space  →  cycles forward through your layouts"),
                new Tip("↓", "Swipe DOWN on  Space  →  cycles backward"),
                new Tip("💡", "The small symbols shown at the corners and edges of each key are exactly what you get when you swipe there"),
            }),

        new Step("🧭", "Step 3 — All 8 Swipe Directions",
            "Each key supports up to 8 swipe directions — think of a compass rose. This gives every key 9 possible characters!",
            new Tip[]{
                new Tip("↖ NW", "Swipe to the top-left corner"),
                new Tip("↑ N ", "Swipe straight up"),
                new Tip("↗ NE", "Swipe to the top-right corner"),
                new Tip("← W ", "Swipe straight left"),
                new Tip("→ E ", "Swipe straight right"),
                new Tip("↙ SW", "Swipe to the bottom-left corner"),
                new Tip("↓ S ", "Swipe straight down"),
                new Tip("↘ SE", "Swipe to the bottom-right corner"),
                new Tip("🔘 Center", "Tap the key normally for the main character"),
            }),

        new Step("🔘", "Step 4 — Hold & Other Gestures",
            "Beyond swiping, the keyboard understands a few more gestures that are super useful.",
            new Tip[]{
                new Tip("👇 Hold", "Long-press any key to get a pop-up with all its available symbols"),
                new Tip("⭕ Circle", "Draw a circle gesture on  G  to trigger Compose mode (special character combinations)"),
                new Tip("⇧ Shift", "Tap Shift once = capitalise next letter. Double-tap = Caps Lock"),
                new Tip("⌫ Backspace", "Swipe left on Backspace to delete whole words at once"),
                new Tip("Ctrl + *", "Press Ctrl then the * key to open the built-in clipboard viewer"),
            }),

        new Step("📋", "Step 5 — Clipboard History",
            "Every time you copy text anywhere on your phone, the keyboard quietly remembers it. Never lose a copied item again!",
            new Tip[]{
                new Tip("📌", "Pin important items to keep them at the top of the list"),
                new Tip("⌨", "Tap the clipboard icon in the suggestion bar to open clipboard inside the keyboard"),
                new Tip("🔗", "Tap the widget icon next to it to open the floating clipboard overlay — use it inside any app!"),
                new Tip("⏰", "Clipboard history expires automatically. Go to App Settings to choose how long items are kept"),
                new Tip("🗑", "Swipe a history item or tap Remove to delete it"),
            }),

        new Step("🔐", "Step 6 — Smart Clips",
            "Smart Clips are your personal secret storage for things you type often — passwords, bank details, addresses, codes, tokens. They stay encrypted on your device.",
            new Tip[]{
                new Tip("➕", "Tap  + ADD CLIP  at the bottom of the Smart Clips screen to create one"),
                new Tip("📝", "Give it a title (description) so you remember what it is, and paste or type the content"),
                new Tip("🙈", "Mark a clip as  Hidden  to keep it out of the widget but still accessible in the app"),
                new Tip("🔒", "Lock a clip with PIN to require your PIN every time someone tries to view or copy it"),
                new Tip("📌", "Pin clips to the top so your most-used ones are always first"),
            }),

        new Step("⌨️", "Step 7 — Assign Clips to Keyboard Keys",
            "Here's where it gets magical! You can bind any Smart Clip to a swipe direction on any key. Then just swipe to paste it while typing — no copy-paste needed!",
            new Tip[]{
                new Tip("1️⃣", "Open Smart Clips and find the clip you want to assign"),
                new Tip("2️⃣", "Tap the  ⌨  (keyboard) button on that clip's action row"),
                new Tip("3️⃣", "A full keyboard preview appears. Tap any  +  dot around any key"),
                new Tip("4️⃣", "The clip is now bound to that key's swipe direction"),
                new Tip("✨", "While typing in ANY app, swipe that direction on that key — the clip's content is pasted instantly!"),
                new Tip("💡", "You have 8 slots per key × all keyboard keys = hundreds of possible shortcuts"),
            }),

        new Step("🚀", "Step 8 — Instant Paste While Typing",
            "Once you've assigned clips to keys, here's how to use them in real life.",
            new Tip[]{
                new Tip("📱", "Open any app — messaging, browser, email, anything"),
                new Tip("⌨️", "Start typing normally with the keyboard open"),
                new Tip("↗", "When you need to paste your Smart Clip, swipe the direction you assigned it to"),
                new Tip("⚡", "The clip content appears instantly at the cursor — no menus, no copy-paste!"),
                new Tip("🎯", "Perfect for: passwords, addresses, signatures, URLs, codes, greetings"),
                new Tip("🔵", "Assigned keys show a coloured dot — blue = another clip, green = current clip"),
            }),

        new Step("🪟", "Step 9 — Floating Widget",
            "The floating widget lives on top of all your other apps as a small overlay you can move around.",
            new Tip[]{
                new Tip("🔗", "Tap the widget icon in the keyboard's suggestion bar to open it"),
                new Tip("📋", "Switch between your Clipboard History and Smart Clips using the tabs"),
                new Tip("👆", "Tap any entry to paste it directly into the text box you have open"),
                new Tip("🖱", "Drag the widget to any corner of your screen to keep it out of the way"),
                new Tip("✖", "Tap X to close the widget when you're done"),
            }),

        new Step("⚡", "Step 10 — Typing Master",
            "Want to type faster and more accurately? Typing Master is a built-in speed trainer that gives you real-time feedback.",
            new Tip[]{
                new Tip("📊", "Your WPM (words per minute) and accuracy are shown live as you type"),
                new Tip("🎯", "Practise with common words, sentences, or code snippets"),
                new Tip("📈", "Watch your speed improve over multiple sessions"),
                new Tip("⌨️", "Open it from the home screen — great for warming up before a big typing session"),
            }),

        new Step("🛡", "Step 11 — System Console (Advanced)",
            "The System Console lets you monitor live device logs — useful for developers and power users.",
            new Tip[]{
                new Tip("⚠️", "This feature requires  Shizuku  to be installed and running"),
                new Tip("📡", "Once connected, Shizuku grants shell-level log access without root"),
                new Tip("🔍", "Filter logs by tag, level, or keyword in real time"),
                new Tip("💾", "Share or export log sessions for debugging"),
                new Tip("🟢 Green dot", "Shizuku connected — ready to use"),
                new Tip("🔴 Red dot", "Shizuku not running — start it from its own app first"),
            }),

        new Step("🎉", "You're All Set!",
            "You now know everything about UnBelievable Keyboard. Go try it out — and remember, you can always come back to this tutorial by tapping 📖 on the home screen.",
            new Tip[]{
                new Tip("⌨️", "Enable the keyboard in Settings if you haven't yet"),
                new Tip("🔐", "Create your first Smart Clip — start with something you type often"),
                new Tip("↗", "Assign it to a key and feel the magic of instant swipe-paste"),
                new Tip("📋", "Browse your clipboard history — it's probably already full of useful stuff!"),
                new Tip("❓", "Questions? The ℹ icon in Smart Clips shows icon meanings. This 📖 reopens the tutorial."),
            }),
    };

    // ── Activity lifecycle ────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        markSeen(this);

        C = ThemeManager.colors(this);
        D = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(C.background);

        // Main content area
        _pageContainer = new FrameLayout(this);
        root.addView(_pageContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Bottom nav bar (dots + buttons)
        LinearLayout navBar = buildNavBar();
        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(navBar, navLp);

        // Skip button (top-right)
        _btnSkip = new TextView(this);
        _btnSkip.setText("Skip ›");
        _btnSkip.setTextSize(13);
        _btnSkip.setTextColor(C.textHint);
        _btnSkip.setGravity(Gravity.CENTER);
        _btnSkip.setPadding(dp(16), dp(16), dp(16), dp(8));
        _btnSkip.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams skipLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        root.addView(_btnSkip, skipLp);

        setContentView(root);
        ThemeManager.attachMatrixOverlay(this);

        showPage(_page, false);
    }

    // ── Navigation bar (dots + Back/Next) ────────────────────────────────────

    private LinearLayout buildNavBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(C.surface);
        bar.setPadding(dp(20), dp(12), dp(20), dp(20));

        // Progress dots row
        _dotsRow = new LinearLayout(this);
        _dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        _dotsRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < STEPS.length; i++) {
            View dot = new View(this);
            dot.setBackgroundColor(i == 0 ? C.primary : C.divider);
            int dotW = i == 0 ? dp(20) : dp(7);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dotW, dp(7));
            dlp.setMargins(dp(3), 0, dp(3), 0);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setCornerRadius(dp(4));
            dotBg.setColor(i == 0 ? C.primary : C.divider);
            dot.setBackground(dotBg);
            _dotsRow.addView(dot, dlp);
        }
        bar.addView(_dotsRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Button row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRowLp.setMargins(0, dp(14), 0, 0);
        btnRow.setLayoutParams(btnRowLp);

        _btnBack = makePillButton("← Back", false);
        _btnBack.setOnClickListener(v -> navigate(-1));
        _btnBack.setVisibility(View.INVISIBLE);
        btnRow.addView(_btnBack, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        btnRow.addView(spacer(dp(12)));

        _btnNext = makePillButton("Next →", true);
        _btnNext.setOnClickListener(v -> navigate(+1));
        btnRow.addView(_btnNext, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        bar.addView(btnRow);
        return bar;
    }

    // ── Page rendering ────────────────────────────────────────────────────────

    private void showPage(int index, boolean animate) {
        Step step = STEPS[index];

        // Build the new page view
        ScrollView page = buildPageView(step, index);
        page.setAlpha(animate ? 0f : 1f);
        _pageContainer.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        if (animate && _pageContainer.getChildCount() > 1) {
            // Fade out old, fade in new
            View old = _pageContainer.getChildAt(0);
            page.animate().alpha(1f).setDuration(220).start();
            old.animate().alpha(0f).setDuration(180)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator a) {
                        _pageContainer.removeView(old);
                    }
                }).start();
        }

        // Update dots
        for (int i = 0; i < _dotsRow.getChildCount(); i++) {
            View dot = _dotsRow.getChildAt(i);
            boolean active = (i == index);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(4));
            bg.setColor(active ? C.primary : C.divider);
            dot.setBackground(bg);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
            lp.width = active ? dp(20) : dp(7);
            dot.setLayoutParams(lp);
        }

        // Update buttons
        boolean first = (index == 0);
        boolean last  = (index == STEPS.length - 1);
        _btnBack.setVisibility(first ? View.INVISIBLE : View.VISIBLE);
        _btnNext.setText(last ? "✓ Done" : "Next →");
        _btnSkip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);

        // Update button colour for Done state
        updatePillButton(_btnNext, last);
    }

    private ScrollView buildPageView(Step step, int index) {
        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(60), dp(24), dp(150));

        // ── Big emoji icon ────────────────────────────────────────────────────
        TextView icon = new TextView(this);
        icon.setText(step.emoji);
        icon.setTextSize(64);
        icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconLp.setMargins(0, 0, 0, dp(16));
        content.addView(icon, iconLp);

        // ── Step indicator pill ───────────────────────────────────────────────
        if (index > 0 && index < STEPS.length - 1) {
            TextView stepPill = new TextView(this);
            stepPill.setText("Step " + index + " of " + (STEPS.length - 2));
            stepPill.setTextSize(11);
            stepPill.setTextColor(C.primary);
            stepPill.setGravity(Gravity.CENTER);
            stepPill.setPadding(dp(14), dp(4), dp(14), dp(4));
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setColor(0x20818CF8);
            pillBg.setStroke(1, C.primary);
            pillBg.setCornerRadius(dp(20));
            stepPill.setBackground(pillBg);
            LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            pillLp.gravity = Gravity.CENTER_HORIZONTAL;
            pillLp.setMargins(0, 0, 0, dp(16));
            content.addView(stepPill, pillLp);
        }

        // ── Title ─────────────────────────────────────────────────────────────
        TextView title = new TextView(this);
        title.setText(step.title);
        title.setTextSize(22);
        title.setTextColor(C.textPrimary);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, 0, 0, dp(14));
        content.addView(title, titleLp);

        // ── Description ───────────────────────────────────────────────────────
        TextView desc = new TextView(this);
        desc.setText(step.description);
        desc.setTextSize(14);
        desc.setTextColor(C.textSecondary);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(0, 1.5f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.setMargins(0, 0, 0, dp(24));
        content.addView(desc, descLp);

        // ── Tip cards ─────────────────────────────────────────────────────────
        for (int i = 0; i < step.tips.length; i++) {
            Tip tip = step.tips[i];
            content.addView(buildTipCard(tip, i));
        }

        sv.addView(content);
        return sv;
    }

    private View buildTipCard(Tip tip, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        // Alternating card tint
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(index % 2 == 0 ? C.surface : C.surfaceVariant);
        bg.setCornerRadius(dp(12));
        bg.setStroke(1, C.divider);
        card.setBackground(bg);

        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(1));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardLp);

        // Icon badge
        TextView iconView = new TextView(this);
        iconView.setText(tip.icon);
        iconView.setTextSize(14);
        iconView.setGravity(Gravity.CENTER);
        int badgeSz = dp(36);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0x22818CF8);
        badgeBg.setCornerRadius(dp(10));
        iconView.setBackground(badgeBg);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(badgeSz, badgeSz));
        card.addView(iconView);

        // Text
        TextView text = new TextView(this);
        text.setText(tip.text);
        text.setTextSize(13);
        text.setTextColor(C.textPrimary);
        text.setLineSpacing(0, 1.4f);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMargins(dp(12), 0, 0, 0);
        text.setLayoutParams(textLp);
        card.addView(text);

        return card;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void navigate(int delta) {
        int next = _page + delta;
        if (next < 0) { finish(); return; }
        if (next >= STEPS.length) { finish(); return; }
        _page = next;
        showPage(_page, true);
    }

    @Override
    public void onBackPressed() {
        if (_page > 0) navigate(-1);
        else super.onBackPressed();
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private Button makePillButton(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(8), dp(12), dp(8), dp(12));
        b.setAllCaps(false);
        b.setMinHeight(0);
        updatePillButton(b, primary);
        return b;
    }

    private void updatePillButton(Button b, boolean primary) {
        boolean isDone = "✓ Done".equals(b.getText().toString());
        GradientDrawable bg = new GradientDrawable();
        int color = primary
                ? (isDone ? C.green : C.primary)
                : C.surfaceVariant;
        bg.setColor(color);
        bg.setCornerRadius(dp(24));
        if (!primary) bg.setStroke(1, C.divider);
        b.setBackground(bg);
        b.setTextColor(primary ? 0xFFFFFFFF : C.textSecondary);
    }

    private View spacer(int widthPx) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(widthPx, 1));
        return v;
    }

    private int dp(int v) { return (int)(v * D); }

    // ── Data classes ──────────────────────────────────────────────────────────

    static class Step {
        final String emoji, title, description;
        final Tip[] tips;
        Step(String e, String t, String d, Tip[] tips) {
            emoji = e; title = t; description = d; this.tips = tips;
        }
    }

    static class Tip {
        final String icon, text;
        Tip(String i, String t) { icon = i; text = t; }
    }
}
