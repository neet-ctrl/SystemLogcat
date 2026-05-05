package juloo.keyboard2;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Keyboard clipboard pane — visually identical to FloatingWidgetService.
 *
 * Layout (top → bottom):
 *   ┌─ Header bar: [📋 Clipboard title]  [⚙ Settings]  [⬅ Back] ─┐
 *   │  Tab pills : [📋 History]  [⚡ Smart Clips]                  │
 *   │  Divider                                                     │
 *   │  ListView  (glass cards — same adapters as floating widget)  │
 *   └──────────────────────────────────────────────────────────────┘
 */
public final class KeyboardClipboardPane extends LinearLayout
        implements ClipboardHistoryService.OnClipboardHistoryChange,
                   SmartClipsService.OnSmartClipsChangeListener {

    // ── Palette (exact copy from FloatingWidgetService) ──────────────────────
    private static final int COL_BG        = 0xFF0A0E1A;
    private static final int COL_SURFACE   = 0xCC1E2040;
    private static final int COL_SURFACE_B = 0xCC151832;
    private static final int COL_PRIMARY   = 0xFF4F46E5;
    private static final int COL_PRIMARY_L = 0xFF818CF8;
    private static final int COL_TXT       = 0xFFE2E8F0;
    private static final int COL_TXT_SEC   = 0xFF94A3B8;
    private static final int COL_TXT_HINT  = 0xFF475569;
    private static final int COL_GREEN     = 0xFF059669;
    private static final int COL_RED       = 0xFFFF6B6B;
    private static final int COL_INACTIVE  = 0xAA252947;
    private static final int COL_BORDER    = 0x553A3F6E;

    // ── Fields ────────────────────────────────────────────────────────────────
    private final float dp;

    private TextView _tvTitle;
    private Button   _btnHistory;
    private Button   _btnSmart;
    private ListView _listView;
    private View     _pinPanel;   // inline PIN entry shown when Smart Clips is locked

    private boolean _showSmart = false;

    private ClipboardHistoryService _histService;
    private SmartClipsService       _smartService;

    /** Called when the user taps "⬅ Back" — keyboard service hooks this. */
    private Runnable _dismissListener;

    // ── Constructors ──────────────────────────────────────────────────────────

    public KeyboardClipboardPane(Context ctx, android.util.AttributeSet attrs) {
        super(ctx, attrs);
        dp = ctx.getResources().getDisplayMetrics().density;
        init(ctx);
    }

    public KeyboardClipboardPane(Context ctx) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        init(ctx);
    }

    public void setOnDismissListener(Runnable r) {
        _dismissListener = r;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void init(Context ctx) {
        setOrientation(VERTICAL);
        setBackgroundColor(COL_BG);

        addView(buildHeader(ctx));
        addView(buildTabBar(ctx));

        // Thin primary divider
        View divider = new View(ctx);
        divider.setBackgroundColor(COL_PRIMARY);
        divider.setAlpha(0.30f);
        addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));

        // Inline PIN panel (hidden by default)
        _pinPanel = buildPinPanel(ctx);
        _pinPanel.setVisibility(GONE);
        addView(_pinPanel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // ListView — fixed height so the pane stays keyboard-sized (not full-screen)
        _listView = new ListView(ctx);
        _listView.setBackgroundColor(0x00000000);
        _listView.setDivider(null);
        _listView.setDividerHeight(dp(6));
        _listView.setPadding(dp(8), dp(6), dp(8), dp(6));
        _listView.setClipToPadding(false);
        int listH = (int) ctx.getResources().getDimension(R.dimen.clipboard_view_height);
        addView(_listView, new LayoutParams(LayoutParams.MATCH_PARENT, listH));

        // Services
        _histService  = ClipboardHistoryService.get_service(ctx);
        _smartService = SmartClipsService.getInstance(ctx);
        if (_histService != null) _histService.set_on_clipboard_history_change(this);
        if (_smartService != null) _smartService.addListener(this);

        updateList(ctx);
    }

    // ── Header bar ────────────────────────────────────────────────────────────

    private LinearLayout buildHeader(Context ctx) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(8), dp(6));
        bar.setBackgroundColor(0xFF0D1120);

        // Title
        _tvTitle = new TextView(ctx);
        _tvTitle.setText("📋  Clipboard");
        _tvTitle.setTextColor(COL_TXT);
        _tvTitle.setTextSize(14);
        _tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(_tvTitle, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        // Settings button
        Button settingsBtn = makeHeaderBtn(ctx, "⚙", COL_INACTIVE);
        settingsBtn.setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(
                    ctx, AppSettingsActivity.class);
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        });
        bar.addView(settingsBtn);

        // Back to keyboard button
        Button backBtn = makeHeaderBtn(ctx, "⬅", COL_PRIMARY);
        backBtn.setOnClickListener(v -> {
            if (_dismissListener != null) _dismissListener.run();
        });
        LayoutParams backLp = new LayoutParams(dp(36), dp(30));
        backLp.setMargins(dp(6), 0, 0, 0);
        backBtn.setLayoutParams(backLp);
        bar.addView(backBtn);

        return bar;
    }

    private Button makeHeaderBtn(Context ctx, String label, int color) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(COL_TXT);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        LayoutParams lp = new LayoutParams(dp(36), dp(30));
        lp.setMargins(dp(4), 0, 0, 0);
        b.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(8));
        b.setBackground(bg);
        return b;
    }

    // ── Tab bar ───────────────────────────────────────────────────────────────

    private LinearLayout buildTabBar(Context ctx) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(7), dp(10), dp(7));
        bar.setBackgroundColor(0xFF0D1120);

        _btnHistory = makeTabBtn(ctx, "📋  History");
        _btnSmart   = makeTabBtn(ctx, "⚡  Smart Clips");

        _btnHistory.setOnClickListener(v -> {
            if (_showSmart) {
                _showSmart = false;
                _pinPanel.setVisibility(GONE);
                _listView.setVisibility(VISIBLE);
                refreshTabUI();
                updateList(ctx);
            }
        });

        _btnSmart.setOnClickListener(v -> {
            if (!_showSmart) {
                _showSmart = true;
                refreshTabUI();
                if (_smartService != null
                        && _smartService.isLockEnabled()
                        && !_smartService.isUnlocked()
                        && _smartService.isPinSetup()) {
                    showInlinePinPanel(ctx);
                } else {
                    _pinPanel.setVisibility(GONE);
                    _listView.setVisibility(VISIBLE);
                    updateList(ctx);
                }
            } else {
                _showSmart = false;
                _pinPanel.setVisibility(GONE);
                _listView.setVisibility(VISIBLE);
                refreshTabUI();
                updateList(ctx);
            }
        });

        LayoutParams lp1 = new LayoutParams(0, dp(30), 1f);
        lp1.setMargins(0, 0, dp(6), 0);
        bar.addView(_btnHistory, lp1);
        bar.addView(_btnSmart,   new LayoutParams(0, dp(30), 1f));

        refreshTabUI();
        return bar;
    }

    private Button makeTabBtn(Context ctx, String label) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COL_INACTIVE);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), COL_BORDER);
        b.setBackground(bg);
        b.setTextColor(COL_TXT_SEC);
        return b;
    }

    private void refreshTabUI() {
        if (_tvTitle != null)
            _tvTitle.setText(_showSmart ? "🔐  Smart Clips" : "📋  Clipboard");

        // Active tab: filled primary pill
        GradientDrawable activeBg = new GradientDrawable();
        activeBg.setColor(COL_PRIMARY);
        activeBg.setCornerRadius(dp(20));

        // Inactive tab: dark outlined pill
        GradientDrawable inactiveBg = new GradientDrawable();
        inactiveBg.setColor(COL_INACTIVE);
        inactiveBg.setCornerRadius(dp(20));
        inactiveBg.setStroke(dp(1), COL_BORDER);

        if (_btnHistory != null) {
            _btnHistory.setBackground(_showSmart ? inactiveBg : activeBg);
            _btnHistory.setTextColor(_showSmart ? COL_TXT_SEC : 0xFFFFFFFF);
        }
        if (_btnSmart != null) {
            GradientDrawable smBg = new GradientDrawable();
            smBg.setColor(_showSmart ? COL_PRIMARY : COL_INACTIVE);
            smBg.setCornerRadius(dp(20));
            if (!_showSmart) smBg.setStroke(dp(1), COL_BORDER);
            _btnSmart.setBackground(smBg);
            _btnSmart.setTextColor(_showSmart ? 0xFFFFFFFF : COL_TXT_SEC);
        }
    }

    // ── Inline PIN panel ──────────────────────────────────────────────────────

    private View buildPinPanel(Context ctx) {
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(20), dp(24), dp(20), dp(24));
        panel.setBackgroundColor(COL_BG);
        return panel;
    }

    private void showInlinePinPanel(Context ctx) {
        _listView.setVisibility(GONE);
        _pinPanel.setVisibility(VISIBLE);

        LinearLayout panel = (LinearLayout) _pinPanel;
        panel.removeAllViews();

        TextView label = new TextView(ctx);
        label.setText("🔐  Enter PIN to unlock Smart Clips");
        label.setTextColor(COL_TXT);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        panel.addView(label, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // PIN digit display
        final StringBuilder pinInput = new StringBuilder();
        TextView pinDisplay = new TextView(ctx);
        pinDisplay.setText("••••");
        pinDisplay.setTextColor(COL_TXT_HINT);
        pinDisplay.setTextSize(22);
        pinDisplay.setGravity(Gravity.CENTER);
        pinDisplay.setTypeface(Typeface.DEFAULT_BOLD);
        LayoutParams dispLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        dispLp.setMargins(0, dp(14), 0, dp(14));
        panel.addView(pinDisplay, dispLp);

        Runnable updateDisplay = () -> {
            if (pinInput.length() == 0) {
                pinDisplay.setText("••••");
                pinDisplay.setTextColor(COL_TXT_HINT);
            } else {
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < pinInput.length(); i++) dots.append("●");
                pinDisplay.setText(dots.toString());
                pinDisplay.setTextColor(COL_PRIMARY_L);
            }
        };

        // Number rows: 1-9, then 0 + ⌫ + Unlock
        int[][] rows = {{1,2,3},{4,5,6},{7,8,9}};
        for (int[] row : rows) {
            LinearLayout numRow = new LinearLayout(ctx);
            numRow.setOrientation(HORIZONTAL);
            numRow.setGravity(Gravity.CENTER);
            LayoutParams rowLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(6));
            numRow.setLayoutParams(rowLp);
            for (int n : row) {
                final int digit = n; // captured copy — effectively final for lambda
                Button nb = makePinDigitBtn(ctx, String.valueOf(digit));
                nb.setOnClickListener(v -> {
                    if (pinInput.length() < 8) {
                        pinInput.append(digit);
                        updateDisplay.run();
                    }
                });
                numRow.addView(nb);
            }
            panel.addView(numRow);
        }

        // Bottom row: ⌫  0  Unlock
        LinearLayout bottomRow = new LinearLayout(ctx);
        bottomRow.setOrientation(HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER);

        Button backspaceBtn = makePinDigitBtn(ctx, "⌫");
        backspaceBtn.setTextColor(0xFFFF8A80);
        backspaceBtn.setOnClickListener(v -> {
            if (pinInput.length() > 0) {
                pinInput.deleteCharAt(pinInput.length() - 1);
                updateDisplay.run();
            }
        });
        bottomRow.addView(backspaceBtn);

        Button zeroBtn = makePinDigitBtn(ctx, "0");
        zeroBtn.setOnClickListener(v -> {
            if (pinInput.length() < 8) {
                pinInput.append('0');
                updateDisplay.run();
            }
        });
        bottomRow.addView(zeroBtn);

        Button unlockBtn = makePinDigitBtn(ctx, "✓ Unlock");
        unlockBtn.setTextSize(10);
        GradientDrawable unlockBg = new GradientDrawable();
        unlockBg.setColor(COL_PRIMARY);
        unlockBg.setCornerRadius(dp(10));
        unlockBtn.setBackground(unlockBg);
        unlockBtn.setTextColor(0xFFFFFFFF);
        unlockBtn.setOnClickListener(v -> {
            if (_smartService.verifyPin(pinInput.toString())) {
                _smartService.unlockForDuration();
                _pinPanel.setVisibility(GONE);
                _listView.setVisibility(VISIBLE);
                updateList(ctx);
            } else {
                pinDisplay.setText("Wrong PIN");
                pinDisplay.setTextColor(COL_RED);
                pinInput.setLength(0);
            }
        });
        bottomRow.addView(unlockBtn);

        panel.addView(bottomRow);
    }

    private Button makePinDigitBtn(Context ctx, String label) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(COL_TXT);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        int sz = dp(46);
        LayoutParams lp = new LayoutParams(sz, sz);
        lp.setMargins(dp(5), 0, dp(5), 0);
        b.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COL_SURFACE);
        bg.setCornerRadius(dp(10));
        bg.setStroke(1, COL_BORDER);
        b.setBackground(bg);
        return b;
    }

    // ── List update (mirrors FloatingWidgetService.updateList exactly) ─────────

    private void updateList(Context ctx) {
        if (_pinPanel.getVisibility() == VISIBLE) return; // PIN panel showing — don't overwrite
        post(() -> {
            if (_showSmart) {
                List<SmartClipsService.SmartClip> clips =
                        (_smartService != null) ? _smartService.getClipsForWidget()
                                                : new ArrayList<>();
                Set<Integer> pins = PinStore.getSmartPins(ctx);
                clips.sort((a, b) -> {
                    boolean pa = pins.contains(a.serial), pb = pins.contains(b.serial);
                    if (pa != pb) return pa ? -1 : 1;
                    return Integer.compare(a.serial, b.serial);
                });
                _listView.setAdapter(new GlassSmartClipAdapter(ctx, clips, () -> updateList(ctx)));
                _listView.setOnItemClickListener((p, v, pos, id) -> {
                    SmartClipsService.SmartClip clip = clips.get(pos);
                    if (clip.locked) {
                        Toast.makeText(ctx, "Clip locked — open app to view",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        boolean pasted = ClipboardHistoryService.pasteOrCopy(ctx, clip.content);
                        Toast.makeText(ctx,
                                pasted ? "Pasted!" : "Copied! (open a text field to paste directly)",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                ClipboardHistoryService svc = ClipboardHistoryService.get_service(ctx);
                List<ClipboardHistoryService.HistoryEntry> entries =
                        svc != null ? svc.get_history_entries() : new ArrayList<>();
                entries.sort((a, b) -> {
                    if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
                    return 0;
                });
                List<String> clips = new ArrayList<>();
                for (ClipboardHistoryService.HistoryEntry e : entries) clips.add(e.content);
                if (clips.size() > 50) clips = clips.subList(0, 50);
                final List<String> finalClips = clips;
                _listView.setAdapter(new GlassClipboardAdapter(ctx, finalClips, () -> updateList(ctx)));
                _listView.setOnItemClickListener((p, v, pos, id) -> {
                    boolean pasted = ClipboardHistoryService.pasteOrCopy(ctx, finalClips.get(pos));
                    Toast.makeText(ctx,
                            pasted ? "Pasted!" : "Copied! (open a text field to paste directly)",
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ── Live update callbacks ─────────────────────────────────────────────────

    @Override
    public void on_clipboard_history_change() {
        post(() -> updateList(getContext()));
    }

    @Override
    public void onSmartClipsChanged() {
        post(() -> updateList(getContext()));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (_histService != null) _histService.set_on_clipboard_history_change(null);
        if (_smartService != null) _smartService.removeListener(this);
    }

    // ── dp helper ─────────────────────────────────────────────────────────────

    private int dp(int v) { return (int)(v * dp); }

    // ══════════════════════════════════════════════════════════════════════════
    // Glass Clipboard Adapter — exact copy of FloatingWidgetService inner class
    // ══════════════════════════════════════════════════════════════════════════

    static class GlassClipboardAdapter extends ArrayAdapter<String> {
        private final Context  ctx;
        private final float    dp;
        private final Runnable refresh;

        GlassClipboardAdapter(Context ctx, List<String> items, Runnable refresh) {
            super(ctx, 0, items);
            this.ctx     = ctx;
            this.dp      = ctx.getResources().getDisplayMetrics().density;
            this.refresh = refresh;
        }

        @Override
        public View getView(int pos, View convertView, android.view.ViewGroup parent) {
            String text = getItem(pos);

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(10), dp(8), dp(10));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(pos % 2 == 0 ? COL_SURFACE : COL_SURFACE_B);
            bg.setCornerRadius(dp(12));
            boolean pinned = ClipboardHistoryService.isPinned(text);
            if (pinned) bg.setStroke(1, COL_PRIMARY_L);
            card.setBackground(bg);
            if (Build.VERSION.SDK_INT >= 21) card.setElevation(2 * dp);

            // Pinned accent stripe
            if (pinned) {
                View stripe = new View(ctx);
                stripe.setBackgroundColor(COL_PRIMARY_L);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(3),
                        LinearLayout.LayoutParams.MATCH_PARENT);
                slp.setMargins(0, 0, dp(8), 0);
                stripe.setLayoutParams(slp);
                card.addView(stripe);
            }

            // Content text
            TextView tv = new TextView(ctx);
            tv.setText(text);
            tv.setTextColor(COL_TXT);
            tv.setTextSize(12);
            tv.setMaxLines(2);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setLineSpacing(0, 1.2f);
            card.addView(tv, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            // ⌨ Paste
            Button paste = makePasteBtn(ctx, dp);
            paste.setOnClickListener(v -> {
                boolean pasted = ClipboardHistoryService.pasteOrCopy(ctx, text);
                Toast.makeText(ctx,
                        pasted ? "Pasted!" : "Copied! (focus a text field first)",
                        Toast.LENGTH_SHORT).show();
            });
            card.addView(paste);

            // 📋 Copy
            Button copy = makeCopyBtn(ctx, dp);
            copy.setOnClickListener(v ->
                    ClipboardHistoryService.copyToClipboard(ctx, text));
            card.addView(copy);

            // 📌/📍 Pin
            Button pin = makePinBtn(ctx, dp, pinned);
            pin.setOnClickListener(v -> {
                ClipboardHistoryService.togglePin(text);
                if (refresh != null) refresh.run();
            });
            card.addView(pin);

            return card;
        }

        private int dp(int v) { return (int)(v * dp); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Glass Smart Clip Adapter — exact copy of FloatingWidgetService inner class
    // ══════════════════════════════════════════════════════════════════════════

    static class GlassSmartClipAdapter extends ArrayAdapter<SmartClipsService.SmartClip> {
        private final Context  ctx;
        private final float    dp;
        private final Runnable refresh;

        GlassSmartClipAdapter(Context ctx, List<SmartClipsService.SmartClip> clips,
                              Runnable refresh) {
            super(ctx, 0, clips);
            this.ctx     = ctx;
            this.dp      = ctx.getResources().getDisplayMetrics().density;
            this.refresh = refresh;
        }

        @Override
        public View getView(int pos, View convertView, android.view.ViewGroup parent) {
            SmartClipsService.SmartClip clip = getItem(pos);

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(10), dp(10));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(pos % 2 == 0 ? COL_SURFACE : COL_SURFACE_B);
            bg.setCornerRadius(dp(12));
            card.setBackground(bg);
            if (Build.VERSION.SDK_INT >= 21) card.setElevation(2 * dp);

            // Top row: serial chip + content + buttons
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            // #N serial chip
            TextView serial = new TextView(ctx);
            serial.setText("#" + clip.serial);
            serial.setTextSize(9);
            serial.setTextColor(COL_PRIMARY_L);
            serial.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable sBg = new GradientDrawable();
            sBg.setColor(COL_INACTIVE);
            sBg.setCornerRadius(dp(12));
            sBg.setStroke((int)(0.8f * dp), COL_BORDER);
            serial.setBackground(sBg);
            serial.setPadding(dp(6), dp(2), dp(6), dp(2));
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            sLp.setMargins(0, 0, dp(8), 0);
            row.addView(serial, sLp);

            // Content text
            String display = clip.locked ? "⬛  ⬛  ⬛   locked"
                    : (clip.description.isEmpty() ? clip.content : clip.description);
            TextView tv = new TextView(ctx);
            tv.setText(display);
            tv.setTextColor(clip.locked ? COL_TXT_HINT : COL_TXT);
            tv.setTextSize(12);
            tv.setMaxLines(2);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvLp.setMargins(0, 0, 0, 0);
            row.addView(tv, tvLp);

            // ⌨ Paste — unlocked only
            if (!clip.locked) {
                Button paste = makePasteBtn(ctx, dp);
                paste.setOnClickListener(v -> {
                    boolean pasted = ClipboardHistoryService.pasteOrCopy(ctx, clip.content);
                    Toast.makeText(ctx,
                            pasted ? "Pasted!" : "Copied! (focus a text field first)",
                            Toast.LENGTH_SHORT).show();
                });
                row.addView(paste);
            }

            // 📋 Copy
            Button copy = makeCopyBtn(ctx, dp);
            copy.setOnClickListener(v -> {
                if (clip.locked) {
                    Toast.makeText(ctx, "Clip locked — open app to view",
                            Toast.LENGTH_SHORT).show();
                } else {
                    ClipboardHistoryService.suppressNextClip();
                    ClipboardHistoryService.copyToClipboard(ctx, clip.content);
                    Toast.makeText(ctx, "Copied!", Toast.LENGTH_SHORT).show();
                }
            });
            row.addView(copy);

            // 📌/📍 Pin
            boolean pinned = PinStore.isSmartPinned(ctx, clip.serial);
            Button pinBtn = makePinBtn(ctx, dp, pinned);
            pinBtn.setOnClickListener(v -> {
                if (clip.locked) {
                    Toast.makeText(ctx, "Clip locked — open app to view",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                PinStore.toggleSmartPin(ctx, clip.serial);
                if (refresh != null) refresh.run();
            });
            row.addView(pinBtn);

            card.addView(row);

            // Keyword pill (optional)
            if (!clip.keyword.isEmpty()) {
                TextView kw = new TextView(ctx);
                kw.setText("{" + clip.keyword + "}");
                kw.setTextSize(9);
                kw.setTextColor(COL_PRIMARY_L);
                LinearLayout.LayoutParams kwLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                kwLp.setMargins(0, dp(4), 0, 0);
                kw.setLayoutParams(kwLp);
                card.addView(kw);
            }

            return card;
        }

        private int dp(int v) { return (int)(v * dp); }
    }

    // ── Shared button factories (exact copy from FloatingWidgetService) ────────

    private static Button makeCopyBtn(Context ctx, float dp) {
        Button b = new Button(ctx);
        b.setText("📋");
        b.setTextSize(14);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0); b.setMinHeight(0);
        int sz = (int)(34 * dp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
        lp.setMargins((int)(8 * dp), 0, 0, 0);
        b.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(COL_GREEN);
        b.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(2 * dp);
        return b;
    }

    private static Button makePasteBtn(Context ctx, float dp) {
        Button b = new Button(ctx);
        b.setText("⌨");
        b.setTextSize(13);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0); b.setMinHeight(0);
        int sz = (int)(34 * dp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
        lp.setMargins((int)(5 * dp), 0, 0, 0);
        b.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x44818CF8);
        b.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(2 * dp);
        return b;
    }

    private static Button makePinBtn(Context ctx, float dp, boolean pinned) {
        Button b = new Button(ctx);
        b.setText(pinned ? "📌" : "📍");
        b.setTextSize(13);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0); b.setMinHeight(0);
        int sz = (int)(34 * dp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
        lp.setMargins((int)(5 * dp), 0, 0, 0);
        b.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(pinned ? 0x554F46E5 : 0x22818CF8);
        b.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(2 * dp);
        return b;
    }
}
