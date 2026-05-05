package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

public class SmartClipsActivity extends Activity
        implements SmartClipsService.OnSmartClipsChangeListener {

    // ── State ─────────────────────────────────────────────────────────────────
    private SmartClipsService _service;
    private ListView  _listView;
    private GridView  _gridView;
    private EditText  _searchBar;
    private ClipAdapter      _adapter;
    private GridClipAdapter  _gridAdapter;
    private boolean   _isGridView = false;
    private List<SmartClipsService.SmartClip> _allClips      = new ArrayList<>();
    private List<SmartClipsService.SmartClip> _filteredClips = new ArrayList<>();
    private Handler  _handler = new Handler();
    private Runnable _unlockCountdown;
    private TextView _unlockTimerView;

    // ── Theme ─────────────────────────────────────────────────────────────────
    private ThemeManager.ThemeColors C;
    private float  D;
    private String _createdWithSig;
    private boolean _isMatrix;

    // ── Icon constants ────────────────────────────────────────────────────────
    private static final String ICO_COPY   = "📋";
    private static final String ICO_EDIT   = "✏";
    private static final String ICO_HIDE   = "🙈";
    private static final String ICO_SHOW   = "👁";
    private static final String ICO_LOCK   = "🔒";
    private static final String ICO_UNLOCK = "🔓";
    private static final String ICO_ADD    = "+";
    private static final String ICO_INFO   = "ℹ";
    private static final String ICO_ASSIGN = "⌨";

    // ══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        _createdWithSig = ThemeManager.signature(this);
        C       = ThemeManager.colors(this);
        D       = getResources().getDisplayMetrics().density;
        _isMatrix = ThemeManager.isMatrixMode(this);
        _service  = SmartClipsService.getInstance(this);

        if (_service.isLockEnabled() && !_service.isUnlocked()) {
            if (!_service.isPinSetup()) showFirstTimePinSetup();
            else                        showPinDialog(true);
            return;
        }
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!ThemeManager.signature(this).equals(_createdWithSig)) recreate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        _service.removeListener(this);
        if (_unlockCountdown != null) _handler.removeCallbacks(_unlockCountdown);
    }

    @Override
    public void onSmartClipsChanged() {
        runOnUiThread(this::refreshClips);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Root UI
    // ══════════════════════════════════════════════════════════════════════════

    private void buildUI() {
        C = ThemeManager.colors(this);
        _isMatrix = ThemeManager.isMatrixMode(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C.background);

        // ── Header ────────────────────────────────────────────────
        root.addView(buildHeader());

        // ── Search bar ────────────────────────────────────────────
        root.addView(buildSearchBar());

        // ── List / Grid ───────────────────────────────────────────
        _listView = new ListView(this);
        _listView.setDivider(null);
        _listView.setDividerHeight(dp(10));
        _listView.setPadding(dp(14), dp(8), dp(14), dp(8));
        _listView.setClipToPadding(false);
        _listView.setBackgroundColor(0);

        _gridView = new GridView(this);
        _gridView.setNumColumns(2);
        _gridView.setHorizontalSpacing(dp(10));
        _gridView.setVerticalSpacing(dp(10));
        _gridView.setPadding(dp(14), dp(8), dp(14), dp(8));
        _gridView.setClipToPadding(false);
        _gridView.setVisibility(View.GONE);
        _gridView.setBackgroundColor(0);

        LinearLayout.LayoutParams fillLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(_listView, fillLp);
        root.addView(_gridView, fillLp);

        // ── Auto-unlock timer ─────────────────────────────────────
        if (_service.isLockEnabled() && _service.isUnlocked()) {
            _unlockTimerView = new TextView(this);
            _unlockTimerView.setGravity(Gravity.CENTER);
            _unlockTimerView.setPadding(0, dp(4), 0, dp(4));
            _unlockTimerView.setTextSize(10);
            _unlockTimerView.setTextColor(C.green);
            if (_isMatrix) _unlockTimerView.setTypeface(Typeface.MONOSPACE);
            root.addView(_unlockTimerView);
            startUnlockCountdown();
        }

        // ── Bottom action bar ─────────────────────────────────────
        root.addView(buildBottomBar());

        setContentView(root);
        ThemeManager.attachMatrixOverlay(this);
        _service.addListener(this);
        refreshClips();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Header
    // ══════════════════════════════════════════════════════════════════════════

    private LinearLayout buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setBackgroundColor(C.headerBg);
        h.setPadding(dp(12), dp(10), dp(10), dp(10));

        // Back
        Button backBtn = plainIconBtn("←", 18);
        backBtn.setTextColor(C.headerText);
        backBtn.setOnClickListener(v -> finish());
        h.addView(backBtn);

        // Title + subtitle
        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(_isMatrix ? "[SMART_CLIPS]" : "Smart Clips");
        title.setTextSize(16);
        title.setTextColor(C.headerText);
        title.setTypeface(_isMatrix ? Typeface.MONOSPACE : Typeface.DEFAULT_BOLD);
        titleCol.addView(title);

        int count = _service.getClips().size();
        TextView sub = new TextView(this);
        sub.setText(count + " clip" + (count != 1 ? "s" : ""));
        sub.setTextSize(10);
        sub.setTextColor(_isMatrix ? C.primary : 0xAAFFFFFF);
        if (_isMatrix) sub.setTypeface(Typeface.MONOSPACE);
        titleCol.addView(sub);
        h.addView(titleCol);

        // ── ℹ Info button ─────────────────────────────────────────
        Button infoBtn = makeRoundHeaderBtn(ICO_INFO);
        infoBtn.setContentDescription("Icon Legend");
        infoBtn.setOnClickListener(v -> showIconLegend());
        h.addView(infoBtn);

        // ── ⊞/≡ View toggle ──────────────────────────────────────
        Button toggleBtn = makeRoundHeaderBtn(_isGridView ? "≡" : "⊞");
        toggleBtn.setOnClickListener(v -> {
            _isGridView = !_isGridView;
            toggleBtn.setText(_isGridView ? "≡" : "⊞");
            _listView.setVisibility(_isGridView ? View.GONE  : View.VISIBLE);
            _gridView.setVisibility(_isGridView ? View.VISIBLE : View.GONE);
            refreshAdapters();
        });
        addHeaderBtnMargin(toggleBtn);
        h.addView(toggleBtn);

        // ── 🔒/🔓 Lock toggle ─────────────────────────────────────
        if (_service.isPinSetup()) {
            Button lockToggle = makeRoundHeaderBtn(
                    _service.isLockEnabled() ? ICO_LOCK : ICO_UNLOCK);
            lockToggle.setOnClickListener(v -> {
                boolean on = !_service.isLockEnabled();
                _service.setLockEnabled(on);
                lockToggle.setText(on ? ICO_LOCK : ICO_UNLOCK);
            });
            addHeaderBtnMargin(lockToggle);
            h.addView(lockToggle);

            // ✱ Change PIN
            Button pinBtn = makeRoundHeaderBtn("✱");
            pinBtn.setOnClickListener(v -> showChangePinDialog());
            addHeaderBtnMargin(pinBtn);
            h.addView(pinBtn);
        } else {
            Button setupPin = makeRoundHeaderBtn("✱");
            setupPin.setOnClickListener(v -> showFirstTimePinSetup());
            addHeaderBtnMargin(setupPin);
            h.addView(setupPin);
        }

        return h;
    }

    /** Apply left-margin to a header button without overriding its fixed size. */
    private void addHeaderBtnMargin(View btn) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) btn.getLayoutParams();
        if (lp == null) lp = new LinearLayout.LayoutParams(dp(34), dp(34));
        lp.setMargins(dp(6), 0, 0, 0);
        btn.setLayoutParams(lp);
    }

    private void showIconLegend() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(8));

        String[][] legend = {
            {ICO_COPY,   "Copy",   "Copy this clip to your clipboard"},
            {ICO_EDIT,   "Edit",   "Edit content, description or keyword"},
            {ICO_HIDE,   "Hide",   "Hide from home-screen widget (still in app)"},
            {ICO_SHOW,   "Show",   "Reveal a currently-hidden clip"},
            {ICO_LOCK,   "Lock",   "Require PIN to view / copy this clip"},
            {ICO_UNLOCK, "Unlock", "Remove PIN lock from this clip"},
            {"⊞",        "Grid view", "Switch to 2-column grid layout"},
            {"≡",        "List view", "Switch to single-column list layout"},
            {"✱",        "PIN",    "Set or change the Smart Clips PIN"},
            {ICO_INFO,   "Info",   "This icon legend! 👋"},
            {ICO_ADD,    "Add",    "Add a new Smart Clip"},
        };

        for (String[] row : legend) {
            LinearLayout rowView = new LinearLayout(this);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, 0, 0, dp(12));
            rowView.setLayoutParams(rlp);

            // Icon circle
            TextView icon = new TextView(this);
            icon.setText(row[0]);
            icon.setTextSize(18);
            icon.setGravity(Gravity.CENTER);
            GradientDrawable ibg = new GradientDrawable();
            ibg.setColor(C.surfaceVariant);
            ibg.setCornerRadius(dp(20));
            icon.setBackground(ibg);
            int sz = dp(40);
            icon.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
            rowView.addView(icon);

            // Label + description
            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tcp.setMargins(dp(14), 0, 0, 0);
            textCol.setLayoutParams(tcp);

            TextView label = new TextView(this);
            label.setText(row[1]);
            label.setTextSize(14);
            label.setTextColor(C.textPrimary);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            textCol.addView(label);

            TextView desc = new TextView(this);
            desc.setText(row[2]);
            desc.setTextSize(11);
            desc.setTextColor(C.textSecondary);
            textCol.addView(desc);

            rowView.addView(textCol);
            content.addView(rowView);
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(content);

        new AlertDialog.Builder(this)
                .setTitle("Icon Guide")
                .setView(sv)
                .setPositiveButton("Got it", null)
                .show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Search Bar
    // ══════════════════════════════════════════════════════════════════════════

    private View buildSearchBar() {
        FrameLayout wrap = new FrameLayout(this);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.setMargins(dp(14), dp(10), dp(14), dp(4));
        wrap.setLayoutParams(wlp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C.surface);
        bg.setCornerRadius(dp(28));
        if (_isMatrix) bg.setStroke((int)(1.5f * D), C.primary);
        wrap.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) wrap.setElevation(dp(2));

        _searchBar = new EditText(this);
        _searchBar.setHint("🔍  Search clips…");
        _searchBar.setHintTextColor(C.textHint);
        _searchBar.setTextColor(C.textPrimary);
        _searchBar.setTextSize(14);
        _searchBar.setBackground(null);
        _searchBar.setPadding(dp(20), dp(13), dp(20), dp(13));
        if (_isMatrix) _searchBar.setTypeface(Typeface.MONOSPACE);
        _searchBar.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                filterClips(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        wrap.addView(_searchBar);
        return wrap;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Bottom action bar
    // ══════════════════════════════════════════════════════════════════════════

    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(C.surface);
        bar.setPadding(dp(20), dp(12), dp(20), dp(12));

        // FAB-style Add button
        Button addBtn = new Button(this);
        addBtn.setText(ICO_ADD + "  Add Clip");
        addBtn.setTextColor(0xFFFFFFFF);
        addBtn.setTextSize(14);
        addBtn.setTypeface(_isMatrix ? Typeface.MONOSPACE : Typeface.DEFAULT_BOLD);
        addBtn.setPadding(dp(36), dp(14), dp(36), dp(14));
        addBtn.setMinWidth(0); addBtn.setMinHeight(0);
        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(C.primary);
        addBg.setCornerRadius(dp(28));
        addBtn.setBackground(addBg);
        if (Build.VERSION.SDK_INT >= 21) addBtn.setElevation(dp(4));
        addBtn.setOnClickListener(v -> showAddDialog());
        bar.addView(addBtn);

        return bar;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Data / filtering
    // ══════════════════════════════════════════════════════════════════════════

    private void refreshClips() {
        _allClips = _service.getClips();
        filterClips(_searchBar != null ? _searchBar.getText().toString() : "");
    }

    private void filterClips(String query) {
        _filteredClips.clear();
        String q = query.toLowerCase().trim();
        for (SmartClipsService.SmartClip c : _allClips) {
            if (q.isEmpty()
                    || c.content.toLowerCase().contains(q)
                    || c.description.toLowerCase().contains(q)
                    || c.keyword.toLowerCase().contains(q)
                    || String.valueOf(c.serial).contains(q)) {
                _filteredClips.add(c);
            }
        }
        // Pinned smart clips float to top — serial numbers and content unchanged
        java.util.Set<Integer> pins = PinStore.getSmartPins(this);
        _filteredClips.sort((a, b) -> {
            boolean pa = pins.contains(a.serial), pb = pins.contains(b.serial);
            if (pa != pb) return pa ? -1 : 1;
            return Integer.compare(a.serial, b.serial);
        });
        refreshAdapters();
    }

    private void refreshAdapters() {
        if (_adapter == null) {
            _adapter = new ClipAdapter();
            _listView.setAdapter(_adapter);
        } else _adapter.notifyDataSetChanged();

        if (_gridAdapter == null) {
            _gridAdapter = new GridClipAdapter();
            _gridView.setAdapter(_gridAdapter);
        } else _gridAdapter.notifyDataSetChanged();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Clip card view (list)
    // ══════════════════════════════════════════════════════════════════════════

    private View buildClipCard(SmartClipsService.SmartClip clip, int position) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(12));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(position % 2 == 0 ? C.surface : C.surfaceVariant);
        cardBg.setCornerRadius(dp(16));
        card.setBackground(cardBg);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));

        // ── Row: serial chip + content ────────────────────────────
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView serial = new TextView(this);
        serial.setText("#" + clip.serial);
        serial.setTextSize(10);
        serial.setTextColor(C.primary);
        serial.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(C.primary);
        sBg.setAlpha(30);
        sBg.setCornerRadius(dp(20));
        serial.setBackground(sBg);
        serial.setPadding(dp(8), dp(3), dp(8), dp(3));
        // Tap serial chip → edit serial number
        serial.setClickable(true);
        serial.setOnClickListener(v -> showEditSerialDialog(clip));
        topRow.addView(serial);

        // Keyword pill
        if (!clip.keyword.isEmpty()) {
            TextView kw = new TextView(this);
            kw.setText("{" + clip.keyword + "}");
            kw.setTextSize(10);
            kw.setTextColor(C.secondary);
            GradientDrawable kwBg = new GradientDrawable();
            kwBg.setColor(C.secondary);
            kwBg.setAlpha(25);
            kwBg.setCornerRadius(dp(20));
            kw.setBackground(kwBg);
            kw.setPadding(dp(7), dp(3), dp(7), dp(3));
            LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            klp.setMargins(dp(6), 0, 0, 0);
            kw.setLayoutParams(klp);
            topRow.addView(kw);
        }

        // Hidden pill
        if (clip.hidden) {
            TextView hp = makeStatusPill("hidden", C.orange);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            hlp.setMargins(dp(6), 0, 0, 0);
            hp.setLayoutParams(hlp);
            topRow.addView(hp);
        }

        // Spacer
        topRow.addView(spacer());

        // Timestamp (top right)
        TextView ts = new TextView(this);
        ts.setText(clip.timestamp);
        ts.setTextSize(9);
        ts.setTextColor(C.textHint);
        topRow.addView(ts);

        card.addView(topRow);

        // ── Content ───────────────────────────────────────────────
        TextView contentView = new TextView(this);
        if (clip.locked) {
            contentView.setText("⬛  ⬛  ⬛  ⬛  ⬛  (locked)");
            contentView.setTextColor(C.textHint);
        } else {
            contentView.setText(clip.content);
            contentView.setTextColor(C.textPrimary);
        }
        contentView.setTextSize(15);
        contentView.setMaxLines(2);
        contentView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (_isMatrix) contentView.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, dp(8), 0, 0);
        contentView.setLayoutParams(clp);
        card.addView(contentView);

        // ── Description ───────────────────────────────────────────
        if (!clip.description.isEmpty()) {
            TextView desc = new TextView(this);
            desc.setText(clip.description);
            desc.setTextSize(12);
            desc.setTextColor(C.textSecondary);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dlp.setMargins(0, dp(3), 0, 0);
            desc.setLayoutParams(dlp);
            card.addView(desc);
        }

        // ── Thin divider ──────────────────────────────────────────
        View divider = new View(this);
        divider.setBackgroundColor(C.divider);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(0.8f * D));
        divLp.setMargins(0, dp(10), 0, dp(8));
        divider.setLayoutParams(divLp);
        card.addView(divider);

        // ── Icon action row ───────────────────────────────────────
        card.addView(buildActionRow(clip));

        return card;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Icon-only action row
    // ══════════════════════════════════════════════════════════════════════════

    private LinearLayout buildActionRow(SmartClipsService.SmartClip clip) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // 📋 Copy
        Button copy = makeActionIcon(ICO_COPY, C.green);
        copy.setContentDescription("Copy clip");
        copy.setOnClickListener(v -> {
            if (clip.locked) { showLockedClipDialog(clip); return; }
            copyToClipboard(clip.content);
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
        });
        row.addView(copy);

        // ✏ Edit
        Button edit = makeActionIcon(ICO_EDIT, C.blue);
        edit.setContentDescription("Edit clip");
        edit.setOnClickListener(v -> {
            if (clip.locked) { showLockedClipDialog(clip); return; }
            showEditDialog(clip);
        });
        row.addView(withMargin(edit, dp(8)));

        // 🙈/👁 Hide / Show
        Button hide = makeActionIcon(clip.hidden ? ICO_SHOW : ICO_HIDE, C.orange);
        hide.setContentDescription(clip.hidden ? "Show clip in widget" : "Hide clip from widget");
        hide.setOnClickListener(v -> _service.updateClip(clip.withHidden(!clip.hidden)));
        row.addView(withMargin(hide, dp(8)));

        // 🔒/🔓 Lock / Unlock
        Button lock = makeActionIcon(clip.locked ? ICO_UNLOCK : ICO_LOCK, C.purple);
        lock.setContentDescription(clip.locked ? "Unlock clip" : "Lock clip");
        lock.setOnClickListener(v -> {
            if (clip.locked) showUnlockClipDialog(clip);
            else showLockConfirmDialog(clip);
        });
        row.addView(withMargin(lock, dp(8)));

        // 📌/📍 Pin — display-only; serial/content never touched
        boolean pinned = PinStore.isSmartPinned(this, clip.serial);
        Button pinBtn = makeActionIcon(pinned ? "📌" : "📍", pinned ? C.primary : C.textHint);
        pinBtn.setContentDescription(pinned ? "Unpin" : "Pin to top");
        pinBtn.setOnClickListener(v -> {
            PinStore.toggleSmartPin(this, clip.serial);
            filterClips(_searchBar != null ? _searchBar.getText().toString() : "");
        });
        row.addView(withMargin(pinBtn, dp(8)));

        // Spacer
        row.addView(spacer());

        // ⌨ Assign to keyboard corner
        Button assignBtn = makeActionIcon(ICO_ASSIGN, C.primary);
        assignBtn.setContentDescription("Assign to keyboard key corner");
        assignBtn.setOnClickListener(v -> showKeyboardAssignDialog(clip));
        row.addView(withMargin(assignBtn, dp(8)));

        // Spacer
        row.addView(spacer());

        // View content button (text, so user knows what it does)
        if (!clip.locked) {
            Button viewBtn = makePillTextBtn("View", C.primary);
            viewBtn.setOnClickListener(v -> showClipContent(clip));
            row.addView(viewBtn);
        }

        return row;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Keyboard corner assignment — QWERTY picker dialog
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Full keyboard rows for the assign-key dialog.
     * Key names match key0.getString() used in SmartClipKeyBinder.
     *   Row 0 — number row
     *   Row 1 — Q row
     *   Row 2 — A row (home)
     *   Row 3 — Z row
     *   Row 4 — bottom row (Ctrl / Fn / Space / Enter)
     */
    private static final String[][] FULL_KEYBOARD_ROWS = {
        {"1","2","3","4","5","6","7","8","9","0"},
        {"q","w","e","r","t","y","u","i","o","p"},
        {"a","s","d","f","g","h","j","k","l"},
        {"shift","z","x","c","v","b","n","m","backspace"},
        {"ctrl","fn","space","enter"}
    };

    /**
     * All 8 swipe-direction slot names, indexed 1-8.
     * Slot layout on each key:
     *   [1=NW][7=N ][2=NE]
     *   [5=W ][key ][6=E ]
     *   [3=SW][8=S ][4=SE]
     */
    private static final String[] SLOT_NAMES = {
        "", "↖NW", "↗NE", "↙SW", "↘SE", "←W", "→E", "↑N", "↓S"
    };

    /** Human-readable label shown inside each key cell. */
    private static String keyDisplayLabel(String k) {
        switch (k) {
            case "shift":     return "⇧";
            case "backspace": return "⌫";
            case "ctrl":      return "Ctrl";
            case "fn":        return "Fn";
            case "space":     return "Space";
            case "enter":     return "↵";
            default:          return k.toUpperCase();
        }
    }

    /** Relative weight used when laying out key cells horizontally. */
    private static float keyWeight(String k) {
        switch (k) {
            case "shift": case "backspace": case "ctrl": case "enter": return 1.5f;
            case "fn":    return 1.1f;
            case "space": return 3.5f;
            default:      return 1.0f;
        }
    }

    /** FrameLayout gravity for each slot index (1-8). */
    private static int slotGravity(int slot) {
        switch (slot) {
            case 1: return Gravity.TOP    | Gravity.START;
            case 2: return Gravity.TOP    | Gravity.END;
            case 3: return Gravity.BOTTOM | Gravity.START;
            case 4: return Gravity.BOTTOM | Gravity.END;
            case 5: return Gravity.CENTER_VERTICAL | Gravity.START;
            case 6: return Gravity.CENTER_VERTICAL | Gravity.END;
            case 7: return Gravity.TOP    | Gravity.CENTER_HORIZONTAL;
            case 8: return Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            default: return Gravity.CENTER;
        }
    }

    private void showKeyboardAssignDialog(SmartClipsService.SmartClip clip) {
        final android.app.AlertDialog[] dlgRef = new android.app.AlertDialog[1];

        // ── Scroll container ──────────────────────────────────────────────────
        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(12), dp(10), dp(8));
        sv.addView(root);

        // Subtitle
        TextView sub = new TextView(this);
        sub.setText("Tap any of the 8 slots around a key to assign this clip.\n"
                + "Swipe in that direction while typing to paste instantly.");
        sub.setTextSize(11);
        sub.setTextColor(C.textSecondary);
        root.addView(sub);

        // Thin divider
        View topDiv = new View(this);
        topDiv.setBackgroundColor(C.divider);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divLp.setMargins(0, dp(10), 0, dp(8));
        topDiv.setLayoutParams(divLp);
        root.addView(topDiv);

        // ── Full keyboard grid ────────────────────────────────────────────────
        for (String[] keyRow : FULL_KEYBOARD_ROWS) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, dp(2), 0, 0);
            row.setLayoutParams(rowLp);
            for (String key : keyRow) {
                row.addView(buildKeyAssignCell(clip, key, dlgRef));
            }
            root.addView(row);
        }

        // Legend
        TextView legend = new TextView(this);
        legend.setText("8 swipe slots per key:  ↖NW  ↑N  ↗NE  ←W  →E  ↙SW  ↓S  ↘SE\n"
                + "Green = this clip  ·  Blue = other clip  ·  + = empty (tap to assign)");
        legend.setTextSize(9);
        legend.setTextColor(C.textHint);
        LinearLayout.LayoutParams legLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        legLp.setMargins(0, dp(12), 0, dp(2));
        legend.setLayoutParams(legLp);
        root.addView(legend);

        // ── Dialog ────────────────────────────────────────────────────────────
        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setTitle("⌨  Assign Clip #" + clip.serial
                        + (clip.description.isEmpty() ? "" : " — " + clip.description))
                .setView(sv)
                .setNegativeButton("Done", null)
                .create();
        dlgRef[0] = dlg;
        dlg.show();
    }

    /**
     * One key cell in the full keyboard assignment grid.
     *
     * Layout: FrameLayout with the key label centred and 8 small slot dots
     * arranged at the 8 swipe positions (N, NE, E, SE, S, SW, W, NW).
     *
     *   [NW][N ][NE]
     *   [W ][key][E ]
     *   [SW][S ][SE]
     *
     * Empty slots show "+" in dim grey; assigned slots show "#N" coloured
     * (green = this clip, blue/primary = a different clip).
     */
    private android.widget.FrameLayout buildKeyAssignCell(
            final SmartClipsService.SmartClip clip,
            final String keyName,
            final android.app.AlertDialog[] dlgRef) {

        int cellH   = dp(44);
        int dotSize = dp(10);
        int dotMar  = dp(1);

        // ── Outer frame ───────────────────────────────────────────────────────
        android.widget.FrameLayout cell = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams cellLp =
                new LinearLayout.LayoutParams(0, cellH, keyWeight(keyName));
        cellLp.setMargins(dp(1), 0, dp(1), 0);
        cell.setLayoutParams(cellLp);

        // Check if ANY slot on this key is assigned, to give it a subtle highlight
        boolean hasAnyAssignment = false;
        for (int s = 1; s <= 8; s++) {
            if (SmartClipKeyBinder.getSerial(this, keyName, s) >= 0) {
                hasAnyAssignment = true;
                break;
            }
        }

        GradientDrawable cellBg = new GradientDrawable();
        cellBg.setColor(hasAnyAssignment ? 0xFF1A1F35 : C.surface);
        cellBg.setCornerRadius(dp(5));
        cellBg.setStroke(1, hasAnyAssignment ? C.primary : C.divider);
        cell.setBackground(cellBg);

        // ── Centre key label ──────────────────────────────────────────────────
        TextView label = new TextView(this);
        label.setText(keyDisplayLabel(keyName));
        label.setTextSize(9);
        label.setTextColor(C.textPrimary);
        label.setGravity(Gravity.CENTER);
        // Horizontal padding so W/E dots don't overlap the label
        label.setPadding(dotSize + dotMar * 2, 0, dotSize + dotMar * 2, 0);
        cell.addView(label, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        // ── 8 swipe-direction slot dots ───────────────────────────────────────
        for (int slot = 1; slot <= 8; slot++) {
            final int s = slot;
            int existing = SmartClipKeyBinder.getSerial(this, keyName, slot);
            boolean empty = existing < 0;

            TextView dot = new TextView(this);
            dot.setGravity(Gravity.CENTER);
            dot.setTextSize(5);
            dot.setPadding(0, 0, 0, 0);

            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);

            if (empty) {
                dot.setText("+");
                dot.setTextColor(C.textHint);
                dotBg.setColor(C.surfaceVariant);
                dotBg.setStroke(1, C.divider);
                dot.setOnClickListener(v -> {
                    SmartClipKeyBinder.assign(SmartClipsActivity.this, keyName, s, clip.serial);
                    toast("Clip #" + clip.serial + " → '" + keyName + "' " + SLOT_NAMES[s]);
                    if (dlgRef[0] != null) {
                        dlgRef[0].dismiss();
                        showKeyboardAssignDialog(clip);
                    }
                });
            } else {
                final int existingFinal = existing;
                dot.setText("#" + existing);
                dot.setTextColor(0xFFFFFFFF);
                int dotColor = (existing == clip.serial) ? C.green : C.primary;
                dotBg.setColor(dotColor);
                dot.setOnClickListener(v ->
                    new android.app.AlertDialog.Builder(SmartClipsActivity.this)
                        .setTitle("Remove Assignment")
                        .setMessage("Remove clip #" + existingFinal
                                + " from '" + keyName + "' " + SLOT_NAMES[s] + "?")
                        .setPositiveButton("Remove", (d2, w2) -> {
                            SmartClipKeyBinder.remove(SmartClipsActivity.this, keyName, s);
                            toast("Removed from '" + keyName + "' " + SLOT_NAMES[s]);
                            if (dlgRef[0] != null) {
                                dlgRef[0].dismiss();
                                showKeyboardAssignDialog(clip);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show());
            }
            dot.setBackground(dotBg);
            android.widget.FrameLayout.LayoutParams dotLp =
                    new android.widget.FrameLayout.LayoutParams(dotSize, dotSize, slotGravity(s));
            dotLp.setMargins(dotMar, dotMar, dotMar, dotMar);
            cell.addView(dot, dotLp);
        }
        return cell;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Adapters
    // ══════════════════════════════════════════════════════════════════════════

    class ClipAdapter extends BaseAdapter {
        @Override public int    getCount()                      { return _filteredClips.size(); }
        @Override public Object getItem(int p)                  { return _filteredClips.get(p); }
        @Override public long   getItemId(int p)                { return _filteredClips.get(p).serial; }
        @Override public View   getView(int p, View v, ViewGroup g) {
            return buildClipCard(_filteredClips.get(p), p);
        }
    }

    class GridClipAdapter extends BaseAdapter {
        @Override public int    getCount()      { return _filteredClips.size(); }
        @Override public Object getItem(int p)  { return _filteredClips.get(p); }
        @Override public long   getItemId(int p){ return _filteredClips.get(p).serial; }

        @Override
        public View getView(int p, View v, ViewGroup parent) {
            SmartClipsService.SmartClip clip = _filteredClips.get(p);

            LinearLayout card = new LinearLayout(SmartClipsActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(12));
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(p % 2 == 0 ? C.surface : C.surfaceVariant);
            cardBg.setCornerRadius(dp(16));
            card.setBackground(cardBg);
            if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));

            // Serial + hidden pill
            LinearLayout topRow = new LinearLayout(SmartClipsActivity.this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView serial = new TextView(SmartClipsActivity.this);
            serial.setText("#" + clip.serial);
            serial.setTextSize(10);
            serial.setTextColor(C.primary);
            serial.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable sBg = new GradientDrawable();
            sBg.setColor(C.primary); sBg.setAlpha(30); sBg.setCornerRadius(dp(20));
            serial.setBackground(sBg);
            serial.setPadding(dp(7), dp(2), dp(7), dp(2));
            serial.setClickable(true);
            serial.setOnClickListener(btn -> showEditSerialDialog(clip));
            topRow.addView(serial);
            topRow.addView(spacer());
            if (clip.hidden) {
                TextView hp = makeStatusPill("hidden", C.orange);
                topRow.addView(hp);
            }
            card.addView(topRow);

            // Content
            TextView contentV = new TextView(SmartClipsActivity.this);
            if (clip.locked) {
                contentV.setText("⬛⬛⬛ locked");
                contentV.setTextColor(C.textHint);
            } else {
                contentV.setText(clip.content);
                contentV.setTextColor(C.textPrimary);
            }
            contentV.setTextSize(13);
            contentV.setMaxLines(3);
            contentV.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (_isMatrix) contentV.setTypeface(Typeface.MONOSPACE);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            clp.setMargins(0, dp(8), 0, 0);
            contentV.setLayoutParams(clp);
            card.addView(contentV);

            if (!clip.description.isEmpty()) {
                TextView descV = new TextView(SmartClipsActivity.this);
                descV.setText(clip.description);
                descV.setTextSize(10);
                descV.setTextColor(C.textSecondary);
                descV.setPadding(0, dp(4), 0, 0);
                descV.setMaxLines(1);
                descV.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(descV);
            }

            // Icon buttons
            View divider = new View(SmartClipsActivity.this);
            divider.setBackgroundColor(C.divider);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int)(0.8f * D));
            dlp.setMargins(0, dp(10), 0, dp(6));
            divider.setLayoutParams(dlp);
            card.addView(divider);

            LinearLayout btnRow = new LinearLayout(SmartClipsActivity.this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);

            Button copy = makeActionIcon(ICO_COPY, C.green);
            copy.setOnClickListener(vv -> {
                if (clip.locked) { showLockedClipDialog(clip); return; }
                copyToClipboard(clip.content);
                Toast.makeText(SmartClipsActivity.this, "Copied!", Toast.LENGTH_SHORT).show();
            });
            btnRow.addView(copy);

            Button edit = makeActionIcon(ICO_EDIT, C.blue);
            edit.setOnClickListener(vv -> {
                if (clip.locked) { showLockedClipDialog(clip); return; }
                showEditDialog(clip);
            });
            btnRow.addView(withMargin(edit, dp(6)));

            Button hide = makeActionIcon(clip.hidden ? ICO_SHOW : ICO_HIDE, C.orange);
            hide.setOnClickListener(vv -> _service.updateClip(clip.withHidden(!clip.hidden)));
            btnRow.addView(withMargin(hide, dp(6)));

            Button lock = makeActionIcon(clip.locked ? ICO_UNLOCK : ICO_LOCK, C.purple);
            lock.setOnClickListener(vv -> {
                if (clip.locked) showUnlockClipDialog(clip);
                else showLockConfirmDialog(clip);
            });
            btnRow.addView(withMargin(lock, dp(6)));

            boolean gPinned = PinStore.isSmartPinned(SmartClipsActivity.this, clip.serial);
            Button gPin = makeActionIcon(gPinned ? "📌" : "📍", gPinned ? C.primary : C.textHint);
            gPin.setOnClickListener(vv -> {
                PinStore.toggleSmartPin(SmartClipsActivity.this, clip.serial);
                filterClips(_searchBar != null ? _searchBar.getText().toString() : "");
            });
            btnRow.addView(withMargin(gPin, dp(6)));

            card.addView(btnRow);
            return card;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Auto-unlock countdown
    // ══════════════════════════════════════════════════════════════════════════

    private void startUnlockCountdown() {
        _unlockCountdown = new Runnable() {
            @Override public void run() {
                if (_unlockTimerView == null) return;
                long rem = _service.getUnlockRemainingMs();
                if (rem <= 0) { _unlockTimerView.setText("🔒 Auto-locked"); return; }
                long mins = rem / 60000;
                long secs = (rem % 60000) / 1000;
                _unlockTimerView.setText(String.format(
                        _isMatrix ? "[LOCK IN %d:%02d]" : "🔓  Auto-lock in %d:%02d", mins, secs));
                _handler.postDelayed(this, 1000);
            }
        };
        _handler.post(_unlockCountdown);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Dialogs — PIN
    // ══════════════════════════════════════════════════════════════════════════

    private void showFirstTimePinSetup() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(12));

        TextView info = new TextView(this);
        info.setText("Set a PIN to protect your Smart Clips.\nMinimum 4 digits.");
        info.setTextSize(13);
        info.setPadding(0, 0, 0, dp(14));
        layout.addView(info);

        EditText pin1 = new EditText(this);
        pin1.setHint("New PIN  (4–8 digits)");
        pin1.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(pin1);

        EditText pin2 = new EditText(this);
        pin2.setHint("Confirm PIN");
        pin2.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(10), 0, 0);
        pin2.setLayoutParams(lp);
        layout.addView(pin2);

        new AlertDialog.Builder(this)
                .setTitle("🔐  Setup PIN Lock")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("Setup", (d, w) -> {
                    String p1 = pin1.getText().toString().trim();
                    String p2 = pin2.getText().toString().trim();
                    if (p1.length() < 4) {
                        toast("PIN must be at least 4 digits"); return;
                    }
                    if (!p1.equals(p2)) { toast("PINs do not match"); return; }
                    _service.setupPin(p1);
                    _service.unlock10Min();
                    toast("PIN set! Unlocked for 10 minutes.");
                    buildUI();
                })
                .setNegativeButton("Skip", (d, w) -> buildUI())
                .show();
    }

    private void showPinDialog(boolean required) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(12));

        EditText pinInput = new EditText(this);
        pinInput.setHint("Enter PIN");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(pinInput);

        CheckBox keep10 = new CheckBox(this);
        keep10.setText("Keep unlocked for 10 minutes");
        keep10.setChecked(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(12), 0, 0);
        keep10.setLayoutParams(lp);
        layout.addView(keep10);

        new AlertDialog.Builder(this)
                .setTitle("🔐  Smart Clips — Enter PIN")
                .setView(layout)
                .setCancelable(!required)
                .setPositiveButton("Unlock", (d, w) -> {
                    if (_service.verifyPin(pinInput.getText().toString().trim())) {
                        if (keep10.isChecked()) _service.unlock10Min();
                        buildUI();
                    } else {
                        toast("Incorrect PIN");
                        if (required) finish();
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> finish())
                .show();
    }

    private void showChangePinDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(12));

        EditText oldPin = makeField(layout, "Current PIN");
        EditText newPin = makeField(layout, "New PIN  (4–8 digits)");
        EditText confPin = makeField(layout, "Confirm New PIN");

        new AlertDialog.Builder(this)
                .setTitle("Change PIN")
                .setView(layout)
                .setPositiveButton("Change", (d, w) -> {
                    if (!_service.verifyPin(oldPin.getText().toString().trim())) {
                        toast("Current PIN incorrect"); return;
                    }
                    String np = newPin.getText().toString().trim();
                    if (np.length() < 4) { toast("PIN must be ≥ 4 digits"); return; }
                    if (!np.equals(confPin.getText().toString().trim())) {
                        toast("PINs do not match"); return;
                    }
                    _service.setupPin(np);
                    toast("PIN changed!");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLockedClipDialog(SmartClipsService.SmartClip clip) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(12));
        TextView info = new TextView(this);
        info.setText("🔒  This clip is locked. Enter PIN to view or copy.");
        info.setTextSize(13);
        info.setPadding(0, 0, 0, dp(12));
        layout.addView(info);
        EditText pinInput = makeField(layout, "PIN");

        new AlertDialog.Builder(this)
                .setTitle("Unlock Clip #" + clip.serial)
                .setView(layout)
                .setPositiveButton("Show", (d, w) -> {
                    if (_service.verifyPin(pinInput.getText().toString().trim()))
                        showClipContent(clip);
                    else toast("Incorrect PIN");
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showUnlockClipDialog(SmartClipsService.SmartClip clip) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(12));
        EditText pinInput = makeField(layout, "Enter PIN to unlock clip");

        new AlertDialog.Builder(this)
                .setTitle(ICO_UNLOCK + "  Unlock Clip #" + clip.serial)
                .setView(layout)
                .setPositiveButton("Unlock", (d, w) -> {
                    if (_service.verifyPin(pinInput.getText().toString().trim()))
                        _service.updateClip(clip.withLocked(false));
                    else toast("Incorrect PIN");
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showLockConfirmDialog(SmartClipsService.SmartClip clip) {
        new AlertDialog.Builder(this)
                .setTitle(ICO_LOCK + "  Lock Clip #" + clip.serial)
                .setMessage("Lock this clip? PIN required to view or copy it.")
                .setPositiveButton("Lock", (d, w) -> _service.updateClip(clip.withLocked(true)))
                .setNegativeButton("Cancel", null).show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Dialogs — Serial rename
    // ══════════════════════════════════════════════════════════════════════════

    private void showEditSerialDialog(SmartClipsService.SmartClip clip) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(12));

        TextView info = new TextView(this);
        info.setText("Assign a new serial number to this clip.\nThe number must be unique — no two clips can share it.");
        info.setTextSize(12);
        info.setTextColor(C.textSecondary);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(0, 0, 0, dp(12));
        info.setLayoutParams(ilp);
        layout.addView(info);

        EditText et = new EditText(this);
        et.setHint("New serial number");
        et.setText(String.valueOf(clip.serial));
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setSelectAllOnFocus(true);
        et.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(et);

        new AlertDialog.Builder(this)
                .setTitle("✏  Rename Serial  #" + clip.serial)
                .setView(layout)
                .setPositiveButton("Rename", (d, w) -> {
                    String input = et.getText().toString().trim();
                    if (input.isEmpty()) { toast("Serial number cannot be empty"); return; }
                    int newSerial;
                    try { newSerial = Integer.parseInt(input); }
                    catch (NumberFormatException ex) { toast("Invalid number"); return; }
                    if (newSerial <= 0) { toast("Serial must be greater than 0"); return; }
                    if (!_service.renameSerial(clip.serial, newSerial)) {
                        toast("Serial #" + newSerial + " is already taken by another clip");
                    } else {
                        toast("Clip renamed to #" + newSerial);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Dialogs — CRUD
    // ══════════════════════════════════════════════════════════════════════════

    private void showAddDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(12));

        EditText content = makeMultilineField(layout, "Content  (text, password, token…)");
        EditText desc    = makeField(layout, "Description  (label — optional)");
        EditText keyword = makeField(layout, "Keyword  ({keyword} formula — optional)");

        new AlertDialog.Builder(this)
                .setTitle("✦  Add Smart Clip")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String c = content.getText().toString();
                    if (c.trim().isEmpty()) { toast("Content cannot be empty"); return; }
                    _service.addClip(c, desc.getText().toString(), keyword.getText().toString());
                    toast("Smart Clip added!");
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showEditDialog(SmartClipsService.SmartClip clip) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(12));

        EditText content = makeMultilineField(layout, "Content");
        content.setText(clip.content);
        EditText desc    = makeField(layout, "Description");
        desc.setText(clip.description);
        EditText keyword = makeField(layout, "Keyword  (optional)");
        keyword.setText(clip.keyword);

        new AlertDialog.Builder(this)
                .setTitle("Edit Clip #" + clip.serial)
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String c = content.getText().toString();
                    if (c.trim().isEmpty()) { toast("Content cannot be empty"); return; }
                    _service.updateClip(clip.withContent(c,
                            desc.getText().toString(),
                            keyword.getText().toString()));
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Delete", (d, w) ->
                    new AlertDialog.Builder(this)
                        .setTitle("Delete Clip #" + clip.serial)
                        .setMessage("Are you sure? This cannot be undone.")
                        .setPositiveButton("Delete", (d2, w2) -> _service.deleteClip(clip.serial))
                        .setNegativeButton("Cancel", null).show())
                .show();
    }

    private void showClipContent(SmartClipsService.SmartClip clip) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(clip.content);
        tv.setTextSize(15);
        tv.setTextColor(C.textPrimary);
        tv.setPadding(dp(24), dp(16), dp(24), dp(16));
        tv.setTextIsSelectable(true);
        if (_isMatrix) tv.setTypeface(Typeface.MONOSPACE);
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle("Clip #" + clip.serial
                        + (clip.description.isEmpty() ? "" : "  —  " + clip.description))
                .setView(sv)
                .setPositiveButton("Copy", (d, w) -> {
                    copyToClipboard(clip.content);
                    toast("Copied!");
                })
                .setNegativeButton("Close", null).show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Widget factories
    // ══════════════════════════════════════════════════════════════════════════

    /** Circular icon-only action button */
    private Button makeActionIcon(String icon, int color) {
        Button b = new Button(this);
        b.setText(icon);
        b.setTextSize(16);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0); b.setMinHeight(0);
        int sz = dp(40);
        b.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        bg.setAlpha(220);
        b.setBackground(bg);
        return b;
    }

    /** Rounded header icon button */
    private Button makeRoundHeaderBtn(String icon) {
        Button b = new Button(this);
        b.setText(icon);
        b.setTextSize(14);
        b.setTextColor(C.headerText);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0); b.setMinHeight(0);
        int sz = dp(34);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
        b.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(_isMatrix ? 0x33000000 : 0x28FFFFFF);
        b.setBackground(bg);
        return b;
    }

    /** Plain icon button (no background, just for back arrow etc.) */
    private Button plainIconBtn(String text, float size) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(size);
        b.setBackground(null);
        b.setPadding(0, 0, dp(8), 0);
        b.setMinWidth(0); b.setMinHeight(0);
        return b;
    }

    /** Small pill button with text label */
    private Button makePillTextBtn(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(16), dp(6), dp(16), dp(6));
        b.setMinWidth(0); b.setMinHeight(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(20));
        b.setBackground(bg);
        return b;
    }

    private TextView makeStatusPill(String label, int color) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(9);
        tv.setTextColor(color);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color); bg.setAlpha(30); bg.setCornerRadius(dp(20));
        tv.setBackground(bg);
        tv.setPadding(dp(6), dp(2), dp(6), dp(2));
        return tv;
    }

    // ── Layout utilities ──────────────────────────────────────────────────────

    private View spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return v;
    }

    private View withMargin(View v, int leftMargin) {
        LinearLayout.LayoutParams lp;
        if (v.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        } else {
            lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        }
        lp.setMargins(leftMargin, 0, 0, 0);
        v.setLayoutParams(lp);
        return v;
    }

    private EditText makeField(LinearLayout parent, String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(10), 0, 0);
        e.setLayoutParams(lp);
        parent.addView(e);
        return e;
    }

    private EditText makeMultilineField(LinearLayout parent, String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setMinLines(2);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(10), 0, 0);
        e.setLayoutParams(lp);
        parent.addView(e);
        return e;
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private void copyToClipboard(String text) {
        // Suppress history recording — Smart Clips copies must not pollute clipboard history
        juloo.keyboard2.ClipboardHistoryService.suppressNextClip();
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null)
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Clip", text));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) { return (int)(v * D); }

    public static void openWithPinCheck(Context ctx, SmartClipsService service) {
        android.content.Intent intent = new android.content.Intent(ctx, SmartClipsActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }
}
