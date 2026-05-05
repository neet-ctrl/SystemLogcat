package juloo.keyboard2;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Modern two-tab clipboard panel shown inside the IME keyboard.
 *
 * Tab 0 — Clipboard History : ⌨ Paste  📋 Copy  📌/📍 Pin  🗑 Delete
 * Tab 1 — Smart Clips       : ⌨ Paste (unlocked only)  📋/🔒 Copy  📌/📍 Pin
 *
 * Each tab has a search bar with an embedded mini-QWERTY keyboard for filtering
 * clips without leaving the panel.  The scroll area shrinks when the mini
 * keyboard is open to keep the total height within the IME window.
 */
public final class KeyboardClipboardPane extends LinearLayout
        implements ClipboardHistoryService.OnClipboardHistoryChange,
                   SmartClipsService.OnSmartClipsChangeListener {

    // ── Palette (mirrors FloatingWidgetService) ────────────────────────────────
    private static final int C_BG        = 0xFF0A0E1A;
    private static final int C_SURFACE   = 0xCC1E2040;
    private static final int C_SURFACE_B = 0xCC151832;
    private static final int C_PRIMARY   = 0xFF4F46E5;
    private static final int C_PRIMARY_L = 0xFF818CF8;
    private static final int C_TXT       = 0xFFE2E8F0;
    private static final int C_TXT_SEC   = 0xFF94A3B8;
    private static final int C_TXT_HINT  = 0xFF475569;
    private static final int C_GREEN     = 0xFF059669;
    private static final int C_INACTIVE  = 0xAA252947;
    private static final int C_BORDER    = 0x553A3F6E;

    // ── Fields ────────────────────────────────────────────────────────────────
    private final float dp;

    private LinearLayout  _cardsContainer;
    private Button        _tabHistory, _tabSmart;
    private ScrollView    _scrollView;
    private LinearLayout  _miniKbContainer;
    private TextView      _searchDisplay;

    private boolean _showSmart   = false;
    private boolean _searchOpen  = false;
    private final StringBuilder _searchQuery = new StringBuilder();

    private ClipboardHistoryService _histService;
    private SmartClipsService       _smartService;

    // Scroll area heights (dp) — shrink when mini-keyboard is visible
    private static final int SCROLL_H_NORMAL = 210;
    private static final int SCROLL_H_SEARCH = 120;

    // ── Constructors ──────────────────────────────────────────────────────────

    public KeyboardClipboardPane(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        dp = ctx.getResources().getDisplayMetrics().density;
        init(ctx);
    }

    public KeyboardClipboardPane(Context ctx) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        init(ctx);
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void init(Context ctx) {
        setOrientation(VERTICAL);
        setBackgroundColor(C_BG);

        // Tab bar
        addView(buildTabBar(ctx));

        // Thin primary-colour divider
        View divider = new View(ctx);
        divider.setBackgroundColor(C_PRIMARY);
        divider.setAlpha(0.30f);
        addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));

        // Search bar (always visible)
        addView(buildSearchBar(ctx));

        // Mini-QWERTY (initially hidden)
        _miniKbContainer = buildMiniKeyboard(ctx);
        _miniKbContainer.setVisibility(GONE);
        addView(_miniKbContainer);

        // Scrollable card area
        _scrollView = new ScrollView(ctx);
        _scrollView.setFillViewport(true);
        _scrollView.setOverScrollMode(OVER_SCROLL_NEVER);

        _cardsContainer = new LinearLayout(ctx);
        _cardsContainer.setOrientation(VERTICAL);
        _cardsContainer.setPadding(dp(8), dp(6), dp(8), dp(6));
        _scrollView.addView(_cardsContainer, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        addView(_scrollView, new LayoutParams(
                LayoutParams.MATCH_PARENT, dp(SCROLL_H_NORMAL)));

        // Services
        _histService  = ClipboardHistoryService.get_service(ctx);
        _smartService = SmartClipsService.getInstance(ctx);
        if (_histService != null) _histService.set_on_clipboard_history_change(this);
        if (_smartService != null) _smartService.addListener(this);

        refresh(ctx);
    }

    // ── Tab bar ───────────────────────────────────────────────────────────────

    private LinearLayout buildTabBar(Context ctx) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(7), dp(10), dp(7));
        bar.setBackgroundColor(0xFF0D1120);

        _tabHistory = makeTabBtn(ctx, "📋  History", true);
        _tabHistory.setOnClickListener(v -> switchTab(ctx, false));

        _tabSmart = makeTabBtn(ctx, "⚡  Smart Clips", false);
        _tabSmart.setOnClickListener(v -> switchTab(ctx, true));

        LayoutParams lp1 = new LayoutParams(0, dp(30), 1f);
        lp1.setMargins(0, 0, dp(6), 0);
        bar.addView(_tabHistory, lp1);
        bar.addView(_tabSmart,   new LayoutParams(0, dp(30), 1f));
        return bar;
    }

    private Button makeTabBtn(Context ctx, String label, boolean active) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setTextColor(active ? C_TXT : C_TXT_HINT);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(active ? C_PRIMARY : C_INACTIVE);
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
        return b;
    }

    private void switchTab(Context ctx, boolean smart) {
        _showSmart = smart;
        styleTab(_tabHistory, !smart);
        styleTab(_tabSmart,    smart);
        clearSearch(ctx);   // reset search query when switching tabs
    }

    private void styleTab(Button b, boolean active) {
        b.setTextColor(active ? C_TXT : C_TXT_HINT);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(active ? C_PRIMARY : C_INACTIVE);
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
    }

    // ── Search bar ────────────────────────────────────────────────────────────

    private LinearLayout buildSearchBar(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(4), dp(8), dp(4));
        row.setBackgroundColor(0xFF0D1120);

        // Pill container for 🔍 + query text
        LinearLayout pill = new LinearLayout(ctx);
        pill.setOrientation(HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(C_INACTIVE);
        pillBg.setCornerRadius(dp(20));
        pillBg.setStroke(1, C_BORDER);
        pill.setBackground(pillBg);

        TextView icon = new TextView(ctx);
        icon.setText("🔍");
        icon.setTextSize(11);
        LayoutParams iconLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        iconLp.setMargins(0, 0, dp(5), 0);
        pill.addView(icon, iconLp);

        _searchDisplay = new TextView(ctx);
        _searchDisplay.setHint("Search clips…");
        _searchDisplay.setHintTextColor(C_TXT_HINT);
        _searchDisplay.setTextColor(C_TXT);
        _searchDisplay.setTextSize(12);
        _searchDisplay.setSingleLine(true);
        _searchDisplay.setEllipsize(TextUtils.TruncateAt.END);
        pill.addView(_searchDisplay, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        LayoutParams pillLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        row.addView(pill, pillLp);

        // Tap the pill to toggle mini-keyboard
        pill.setOnClickListener(v -> toggleMiniKeyboard(ctx));

        // × clear button (shown only when query is non-empty)
        Button clear = new Button(ctx);
        clear.setText("×");
        clear.setTextSize(16);
        clear.setTypeface(Typeface.DEFAULT_BOLD);
        clear.setTextColor(C_TXT_HINT);
        clear.setPadding(0, 0, 0, 0);
        clear.setMinWidth(0);
        clear.setMinHeight(0);
        clear.setBackground(null);
        LayoutParams clLp = new LayoutParams(dp(32), dp(32));
        clLp.setMargins(dp(6), 0, 0, 0);
        clear.setLayoutParams(clLp);
        clear.setVisibility(INVISIBLE);
        clear.setOnClickListener(v -> clearSearch(ctx));
        clear.setTag("search_clear");
        row.addView(clear);

        return row;
    }

    private void toggleMiniKeyboard(Context ctx) {
        _searchOpen = !_searchOpen;
        _miniKbContainer.setVisibility(_searchOpen ? VISIBLE : GONE);
        LayoutParams svLp = (LayoutParams) _scrollView.getLayoutParams();
        svLp.height = dp(_searchOpen ? SCROLL_H_SEARCH : SCROLL_H_NORMAL);
        _scrollView.setLayoutParams(svLp);
        updateSearchDisplay();
        refresh(ctx);
    }

    private void clearSearch(Context ctx) {
        _searchQuery.setLength(0);
        _searchOpen = false;
        _miniKbContainer.setVisibility(GONE);
        LayoutParams svLp = (LayoutParams) _scrollView.getLayoutParams();
        svLp.height = dp(SCROLL_H_NORMAL);
        _scrollView.setLayoutParams(svLp);
        updateSearchDisplay();
        refresh(ctx);
    }

    private void appendSearch(Context ctx, String ch) {
        _searchQuery.append(ch);
        updateSearchDisplay();
        refresh(ctx);
    }

    private void backspaceSearch(Context ctx) {
        if (_searchQuery.length() > 0) {
            _searchQuery.deleteCharAt(_searchQuery.length() - 1);
            updateSearchDisplay();
            refresh(ctx);
        }
    }

    private void updateSearchDisplay() {
        String q = _searchQuery.toString();
        _searchDisplay.setText(q.isEmpty() ? null : q);

        // Show/hide the × clear button (found by tag in parent)
        if (_searchDisplay.getParent() instanceof LinearLayout) {
            LinearLayout pill = (LinearLayout) _searchDisplay.getParent();
            if (pill.getParent() instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) pill.getParent();
                View clrBtn = row.findViewWithTag("search_clear");
                if (clrBtn != null) clrBtn.setVisibility(q.isEmpty() ? INVISIBLE : VISIBLE);
            }
        }

        // Highlight pill border when active
        if (_searchDisplay.getParent() instanceof LinearLayout) {
            LinearLayout pill = (LinearLayout) _searchDisplay.getParent();
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setColor(C_INACTIVE);
            pillBg.setCornerRadius(dp(20));
            pillBg.setStroke(_searchOpen ? dp(1) : 1,
                    _searchOpen ? C_PRIMARY_L : C_BORDER);
            pill.setBackground(pillBg);
        }
    }

    // ── Mini-QWERTY keyboard ──────────────────────────────────────────────────

    private static final String[] KEYS_ROW1 = {"Q","W","E","R","T","Y","U","I","O","P"};
    private static final String[] KEYS_ROW2 = {"A","S","D","F","G","H","J","K","L"};
    private static final String[] KEYS_ROW3 = {"Z","X","C","V","B","N","M"};

    private LinearLayout buildMiniKeyboard(Context ctx) {
        LinearLayout kb = new LinearLayout(ctx);
        kb.setOrientation(VERTICAL);
        kb.setPadding(dp(4), dp(4), dp(4), dp(4));
        kb.setBackgroundColor(0xFF0D1120);

        // Row 1: Q…P
        kb.addView(buildKeyRow(ctx, KEYS_ROW1, false));
        // Row 2: A…L (centred by adding side margin)
        LinearLayout row2 = buildKeyRow(ctx, KEYS_ROW2, false);
        LayoutParams r2lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        r2lp.setMargins(dp(14), dp(3), dp(14), 0);
        row2.setLayoutParams(r2lp);
        kb.addView(row2);
        // Row 3: Z…M + ⌫
        kb.addView(buildKeyRow(ctx, KEYS_ROW3, true));

        return kb;
    }

    private LinearLayout buildKeyRow(Context ctx, String[] letters, boolean withBackspace) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LayoutParams rowLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(3), 0, 0);
        row.setLayoutParams(rowLp);

        for (String letter : letters) {
            Button b = makeMiniKey(ctx, letter, 1f);
            b.setOnClickListener(v -> appendSearch(getContext(), letter.toLowerCase()));
            row.addView(b);
        }

        if (withBackspace) {
            Button back = makeMiniKey(ctx, "⌫", 1.5f);
            back.setTextColor(0xFFFF8A80);   // soft red
            back.setOnClickListener(v -> backspaceSearch(getContext()));
            row.addView(back);
        }
        return row;
    }

    private Button makeMiniKey(Context ctx, String label, float weight) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(C_TXT);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        LayoutParams lp = new LayoutParams(0, dp(26), weight);
        lp.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E2040);
        bg.setCornerRadius(dp(6));
        bg.setStroke(1, C_BORDER);
        b.setBackground(bg);
        return b;
    }

    // ── Refresh / filtering ───────────────────────────────────────────────────

    private void refresh(Context ctx) {
        _cardsContainer.removeAllViews();
        if (_showSmart) buildSmartCards(ctx);
        else            buildHistoryCards(ctx);
    }

    /** Returns true if [text] matches the current search query (case-insensitive). */
    private boolean matchesSearch(String... fields) {
        if (_searchQuery.length() == 0) return true;
        String q = _searchQuery.toString().toLowerCase();
        for (String f : fields) {
            if (f != null && f.toLowerCase().contains(q)) return true;
        }
        return false;
    }

    // ── History cards ─────────────────────────────────────────────────────────

    private void buildHistoryCards(Context ctx) {
        if (_histService == null) {
            showEmpty(ctx, "Clipboard history isn't supported on this device.");
            return;
        }
        List<String> raw = _histService.clear_expired_and_get_history();
        if (raw.isEmpty()) {
            showEmpty(ctx, "No clipboard history yet.\nCopy some text to see it here.");
            return;
        }

        List<String> pinned = new ArrayList<>(), rest = new ArrayList<>();
        for (String s : raw) {
            if (!matchesSearch(s)) continue;
            if (ClipboardHistoryService.isPinned(s)) pinned.add(s);
            else rest.add(s);
        }

        List<String> ordered = new ArrayList<>();
        ordered.addAll(pinned);
        ordered.addAll(rest);

        if (ordered.isEmpty()) {
            showEmpty(ctx, "No clips match \"" + _searchQuery + "\".");
            return;
        }

        for (int i = 0; i < ordered.size(); i++) {
            String text = ordered.get(i);
            _cardsContainer.addView(
                    buildHistoryCard(ctx, text, ClipboardHistoryService.isPinned(text), i));
        }
    }

    private View buildHistoryCard(Context ctx, String text, boolean pinned, int idx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(8), dp(6), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(idx % 2 == 0 ? C_SURFACE : C_SURFACE_B);
        bg.setCornerRadius(dp(10));
        if (pinned) bg.setStroke(1, C_PRIMARY_L);
        card.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));

        LayoutParams cardLp = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(5));
        card.setLayoutParams(cardLp);

        // Pinned accent stripe
        if (pinned) {
            View stripe = new View(ctx);
            stripe.setBackgroundColor(C_PRIMARY_L);
            LayoutParams slp = new LayoutParams(dp(3), LayoutParams.MATCH_PARENT);
            slp.setMargins(0, 0, dp(8), 0);
            stripe.setLayoutParams(slp);
            card.addView(stripe);
        }

        // Content text
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(C_TXT);
        tv.setTextSize(12);
        tv.setMaxLines(3);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setLineSpacing(0, 1.2f);
        card.addView(tv, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        // ⌨ Paste
        Button paste = makeIconBtn(ctx, "⌨", 0x55818CF8);
        paste.setOnClickListener(v -> ClipboardHistoryService.paste(text));
        card.addView(paste);

        // 📋 Copy
        Button copy = makeIconBtn(ctx, "📋", C_GREEN);
        copy.setOnClickListener(v ->
                ClipboardHistoryService.copyToClipboard(ctx.getApplicationContext(), text));
        card.addView(copy);

        // 📌 / 📍 Pin
        Button pin = makeIconBtn(ctx, pinned ? "📌" : "📍",
                pinned ? 0x554F46E5 : 0x22818CF8);
        pin.setOnClickListener(v -> {
            ClipboardHistoryService.togglePin(text);
            refresh(ctx);
        });
        card.addView(pin);

        // 🗑 Delete
        Button del = makeIconBtn(ctx, "🗑", 0x44FF6B6B);
        del.setOnClickListener(v -> {
            if (_histService != null) {
                _histService.remove_history_entry(text);
                refresh(ctx);
            }
        });
        card.addView(del);

        return card;
    }

    // ── Smart clip cards ──────────────────────────────────────────────────────

    private void buildSmartCards(Context ctx) {
        if (_smartService == null) {
            showEmpty(ctx, "Smart Clips service is not available.");
            return;
        }
        List<SmartClipsService.SmartClip> clips = _smartService.getClipsForWidget();

        List<SmartClipsService.SmartClip> pinned = new ArrayList<>(), rest = new ArrayList<>();
        for (SmartClipsService.SmartClip c : clips) {
            if (!matchesSearch(c.content, c.description, c.keyword, "#" + c.serial))
                continue;
            if (PinStore.isSmartPinned(ctx, c.serial)) pinned.add(c);
            else rest.add(c);
        }

        List<SmartClipsService.SmartClip> ordered = new ArrayList<>();
        ordered.addAll(pinned);
        ordered.addAll(rest);

        if (ordered.isEmpty()) {
            String msg = clips.isEmpty()
                    ? "No smart clips yet.\nOpen the SmartClips screen to add some."
                    : "No smart clips match \"" + _searchQuery + "\".";
            showEmpty(ctx, msg);
            return;
        }

        for (int i = 0; i < ordered.size(); i++) {
            _cardsContainer.addView(buildSmartCard(ctx, ordered.get(i), i));
        }
    }

    private View buildSmartCard(Context ctx, SmartClipsService.SmartClip clip, int idx) {
        boolean isPinned = PinStore.isSmartPinned(ctx, clip.serial);

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(VERTICAL);
        card.setPadding(dp(10), dp(8), dp(6), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(idx % 2 == 0 ? C_SURFACE : C_SURFACE_B);
        bg.setCornerRadius(dp(10));
        if (isPinned) bg.setStroke(1, C_PRIMARY_L);
        card.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));

        LayoutParams cardLp = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(5));
        card.setLayoutParams(cardLp);

        // ── Top row ──────────────────────────────────────────────────────────
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // #N chip
        TextView serial = new TextView(ctx);
        serial.setText("#" + clip.serial);
        serial.setTextSize(9);
        serial.setTextColor(C_PRIMARY_L);
        serial.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(C_INACTIVE);
        sBg.setCornerRadius(dp(10));
        sBg.setStroke(1, C_BORDER);
        serial.setBackground(sBg);
        serial.setPadding(dp(5), dp(2), dp(5), dp(2));
        LayoutParams slp = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        slp.setMargins(0, 0, dp(7), 0);
        row.addView(serial, slp);

        // Content / description / masked
        String display = clip.locked
                ? "⬛  ⬛  ⬛   locked"
                : (clip.description.isEmpty() ? clip.content : clip.description);
        TextView tv = new TextView(ctx);
        tv.setText(display);
        tv.setTextColor(clip.locked ? C_TXT_HINT : C_TXT);
        tv.setTextSize(12);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setLineSpacing(0, 1.2f);
        LayoutParams tvLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        tvLp.setMargins(0, 0, dp(4), 0);
        row.addView(tv, tvLp);

        // ⌨ Paste — unlocked only
        if (!clip.locked) {
            Button paste = makeIconBtn(ctx, "⌨", 0x55818CF8);
            paste.setOnClickListener(v -> ClipboardHistoryService.paste(clip.content));
            row.addView(paste);
        }

        // 📋 / 🔒 Copy
        Button copy = makeIconBtn(ctx, clip.locked ? "🔒" : "📋",
                clip.locked ? 0x33FF6B6B : C_GREEN);
        copy.setOnClickListener(v -> {
            if (clip.locked) {
                toast(ctx, "Clip is locked — open SmartClips to unlock.");
            } else {
                ClipboardHistoryService.suppressNextClip();
                ClipboardHistoryService.copyToClipboard(ctx.getApplicationContext(), clip.content);
            }
        });
        row.addView(copy);

        // 📌 / 📍 Pin
        Button pin = makeIconBtn(ctx, isPinned ? "📌" : "📍",
                isPinned ? 0x554F46E5 : 0x22818CF8);
        pin.setOnClickListener(v -> {
            PinStore.toggleSmartPin(ctx, clip.serial);
            refresh(ctx);
        });
        row.addView(pin);

        card.addView(row);

        // ── Keyword pill ─────────────────────────────────────────────────────
        if (!clip.keyword.isEmpty()) {
            TextView kw = new TextView(ctx);
            kw.setText("🔑 " + clip.keyword);
            kw.setTextSize(9);
            kw.setTextColor(C_TXT_SEC);
            GradientDrawable kwBg = new GradientDrawable();
            kwBg.setColor(C_INACTIVE);
            kwBg.setCornerRadius(dp(8));
            kw.setBackground(kwBg);
            kw.setPadding(dp(6), dp(2), dp(6), dp(2));
            LayoutParams kwLp = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            kwLp.setMargins(0, dp(4), 0, 0);
            card.addView(kw, kwLp);
        }

        return card;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private void showEmpty(Context ctx, String msg) {
        TextView tv = new TextView(ctx);
        tv.setText(msg);
        tv.setTextColor(C_TXT_HINT);
        tv.setTextSize(12);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(28), dp(16), dp(28));
        _cardsContainer.addView(tv, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private Button makeIconBtn(Context ctx, String icon, int color) {
        Button b = new Button(ctx);
        b.setText(icon);
        b.setTextSize(12);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        int sz = dp(29);
        LayoutParams lp = new LayoutParams(sz, sz);
        lp.setMargins(dp(4), 0, 0, 0);
        b.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        b.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(dp(1));
        return b;
    }

    private void toast(Context ctx, String msg) {
        Toast.makeText(ctx.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) { return (int)(v * dp); }

    // ── Live update callbacks ─────────────────────────────────────────────────

    @Override
    public void on_clipboard_history_change() {
        post(() -> refresh(getContext()));
    }

    @Override
    public void onSmartClipsChanged() {
        post(() -> refresh(getContext()));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (_histService != null) _histService.set_on_clipboard_history_change(null);
        if (_smartService != null) _smartService.removeListener(this);
    }
}
