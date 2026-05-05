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

    private SmartClipsService _service;
    private ListView          _listView;
    private GridView          _gridView;
    private EditText          _searchBar;
    private ClipAdapter       _adapter;
    private GridClipAdapter   _gridAdapter;
    private boolean           _isGridView = false;
    private List<SmartClipsService.SmartClip> _allClips      = new ArrayList<>();
    private List<SmartClipsService.SmartClip> _filteredClips = new ArrayList<>();
    private Handler  _handler = new Handler();
    private Runnable _unlockCountdown;
    private TextView _unlockTimer;

    // Theme
    private ThemeManager.ThemeColors C;
    private float D;
    private String _createdWithSig;
    private boolean _isMatrix;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        _createdWithSig = ThemeManager.signature(this);
        C = ThemeManager.colors(this);
        D = getResources().getDisplayMetrics().density;
        _isMatrix = ThemeManager.isMatrixMode(this);
        _service = SmartClipsService.getInstance(this);

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
        if (!ThemeManager.signature(this).equals(_createdWithSig)) {
            recreate();
        }
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

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        C = ThemeManager.colors(this);
        _isMatrix = ThemeManager.isMatrixMode(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C.background);

        root.addView(buildHeader());

        // Search bar
        FrameLayout searchWrap = new FrameLayout(this);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(C.surface);
        searchBg.setCornerRadius(dp(12));
        if (_isMatrix) searchBg.setStroke((int)(1.5f * D), C.primary);
        searchWrap.setBackground(searchBg);
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        swLp.setMargins(dp(16), dp(10), dp(16), dp(6));
        searchWrap.setLayoutParams(swLp);

        _searchBar = new EditText(this);
        _searchBar.setHint("  🔍  Search clips…");
        _searchBar.setHintTextColor(C.textHint);
        _searchBar.setPadding(dp(16), dp(14), dp(16), dp(14));
        _searchBar.setBackground(null);
        _searchBar.setTextColor(C.textPrimary);
        _searchBar.setTextSize(14);
        if (_isMatrix) _searchBar.setTypeface(Typeface.MONOSPACE);
        _searchBar.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { filterClips(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        searchWrap.addView(_searchBar);
        if (Build.VERSION.SDK_INT >= 21) searchWrap.setElevation(dp(2));
        root.addView(searchWrap);

        // List / Grid views
        _listView = new ListView(this);
        _listView.setDivider(null);
        _listView.setDividerHeight(dp(8));
        _listView.setPadding(dp(16), dp(8), dp(16), dp(8));
        _listView.setClipToPadding(false);
        _listView.setBackgroundColor(0x00000000);

        _gridView = new GridView(this);
        _gridView.setNumColumns(2);
        _gridView.setHorizontalSpacing(dp(10));
        _gridView.setVerticalSpacing(dp(10));
        _gridView.setPadding(dp(16), dp(8), dp(16), dp(8));
        _gridView.setClipToPadding(false);
        _gridView.setVisibility(View.GONE);
        _gridView.setBackgroundColor(0x00000000);

        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(_listView, listLp);
        root.addView(_gridView, listLp);

        // Auto-unlock timer
        if (_service.isLockEnabled() && _service.isUnlocked()) {
            _unlockTimer = new TextView(this);
            _unlockTimer.setPadding(dp(16), dp(4), dp(16), dp(4));
            _unlockTimer.setTextSize(11);
            _unlockTimer.setTextColor(C.green);
            _unlockTimer.setGravity(Gravity.CENTER);
            if (_isMatrix) _unlockTimer.setTypeface(Typeface.MONOSPACE);
            root.addView(_unlockTimer);
            startUnlockCountdown();
        }

        root.addView(buildFooter());

        setContentView(root);
        ThemeManager.attachMatrixOverlay(this);
        _service.addListener(this);
        refreshClips();
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private LinearLayout buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(C.headerBg);
        header.setPadding(dp(18), dp(16), dp(12), dp(16));
        header.setGravity(Gravity.CENTER_VERTICAL);

        // Back arrow
        Button backBtn = new Button(this);
        backBtn.setText("←");
        backBtn.setTextColor(C.headerText);
        backBtn.setTextSize(18);
        backBtn.setBackground(null);
        backBtn.setPadding(0, 0, dp(6), 0);
        backBtn.setOnClickListener(v -> finish());
        header.addView(backBtn);

        // Title
        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(_isMatrix ? "[SMART_CLIPS]" : "Smart Clips");
        title.setTextSize(18);
        title.setTextColor(C.headerText);
        title.setTypeface(_isMatrix ? Typeface.MONOSPACE : Typeface.DEFAULT_BOLD);
        titleCol.addView(title);

        // Clip count badge
        int count = _service.getClips().size();
        TextView badge = new TextView(this);
        badge.setText(count + " clip" + (count != 1 ? "s" : ""));
        badge.setTextSize(10);
        badge.setTextColor(_isMatrix ? C.primary : 0xCCFFFFFF);
        badge.setTypeface(_isMatrix ? Typeface.MONOSPACE : Typeface.DEFAULT);
        titleCol.addView(badge);

        header.addView(titleCol);

        // View toggle (List/Grid)
        Button toggleViewBtn = makeHeaderBtn(_isGridView ? "≡ List" : "⊞ Grid");
        toggleViewBtn.setOnClickListener(v -> {
            _isGridView = !_isGridView;
            toggleViewBtn.setText(_isGridView ? "≡ List" : "⊞ Grid");
            _listView.setVisibility(_isGridView ? View.GONE  : View.VISIBLE);
            _gridView.setVisibility(_isGridView ? View.VISIBLE : View.GONE);
            refreshAdapters();
        });
        header.addView(toggleViewBtn);

        // Lock / PIN buttons
        if (_service.isPinSetup()) {
            Button lockBtn = makeHeaderBtn(_service.isLockEnabled() ? "🔒" : "🔓");
            lockBtn.setOnClickListener(v -> {
                boolean newState = !_service.isLockEnabled();
                _service.setLockEnabled(newState);
                lockBtn.setText(newState ? "🔒" : "🔓");
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(6), 0, 0, 0);
            lockBtn.setLayoutParams(lp);
            header.addView(lockBtn);

            Button pinBtn = makeHeaderBtn("✱ PIN");
            pinBtn.setOnClickListener(v -> showChangePinDialog());
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp2.setMargins(dp(6), 0, 0, 0);
            pinBtn.setLayoutParams(lp2);
            header.addView(pinBtn);
        } else {
            Button setupPinBtn = makeHeaderBtn("+ PIN");
            setupPinBtn.setOnClickListener(v -> showFirstTimePinSetup());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(6), 0, 0, 0);
            setupPinBtn.setLayoutParams(lp);
            header.addView(setupPinBtn);
        }

        return header;
    }

    private Button makeHeaderBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(C.headerText);
        b.setTextSize(12);
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        b.setMinWidth(0);
        b.setMinHeight(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(_isMatrix ? 0x33000000 : 0x30FFFFFF);
        bg.setCornerRadius(dp(20));
        b.setBackground(bg);
        if (_isMatrix) b.setTypeface(Typeface.MONOSPACE);
        return b;
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private LinearLayout buildFooter() {
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setBackgroundColor(C.surface);
        footer.setPadding(dp(20), dp(10), dp(20), dp(10));
        footer.setGravity(Gravity.CENTER);

        Button addBtn = new Button(this);
        addBtn.setText(_isMatrix ? "[+] ADD CLIP" : "+ Add Clip");
        addBtn.setTextColor(_isMatrix ? C.headerText : 0xFFFFFFFF);
        addBtn.setTextSize(14);
        addBtn.setPadding(dp(40), dp(14), dp(40), dp(14));
        addBtn.setMinWidth(0);
        addBtn.setMinHeight(0);
        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(C.primary);
        addBg.setCornerRadius(dp(24));
        addBtn.setBackground(addBg);
        if (_isMatrix) addBtn.setTypeface(Typeface.MONOSPACE);
        if (Build.VERSION.SDK_INT >= 21) addBtn.setElevation(dp(4));
        addBtn.setOnClickListener(v -> showAddDialog());
        footer.addView(addBtn);

        return footer;
    }

    // ── Data ──────────────────────────────────────────────────────────────────

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
        refreshAdapters();
    }

    private void refreshAdapters() {
        if (_adapter == null) {
            _adapter = new ClipAdapter();
            _listView.setAdapter(_adapter);
        } else {
            _adapter.notifyDataSetChanged();
        }
        if (_gridAdapter == null) {
            _gridAdapter = new GridClipAdapter();
            _gridView.setAdapter(_gridAdapter);
        } else {
            _gridAdapter.notifyDataSetChanged();
        }
    }

    // ── Auto-unlock countdown ─────────────────────────────────────────────────

    private void startUnlockCountdown() {
        if (_unlockTimer == null) return;
        _unlockCountdown = new Runnable() {
            @Override public void run() {
                if (_unlockTimer == null) return;
                long rem = _service.getUnlockRemainingMs();
                if (rem <= 0) { _unlockTimer.setText("🔒 Auto-locked"); return; }
                long mins = rem / 60000;
                long secs = (rem % 60000) / 1000;
                _unlockTimer.setText(String.format(
                        _isMatrix ? "[LOCK_IN %d:%02d]" : "🔓 Auto-lock in %d:%02d", mins, secs));
                _handler.postDelayed(this, 1000);
            }
        };
        _handler.post(_unlockCountdown);
    }

    // ── PIN dialogs ───────────────────────────────────────────────────────────

    private void showFirstTimePinSetup() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(20));

        TextView info = new TextView(this);
        info.setText("Set a PIN to protect your Smart Clips.\nMinimum 4 digits.");
        info.setTextSize(13);
        info.setPadding(0, 0, 0, dp(16));
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
        lp.setMargins(0, dp(12), 0, 0);
        pin2.setLayoutParams(lp);
        layout.addView(pin2);

        new AlertDialog.Builder(this)
                .setTitle("Setup PIN Lock")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("Setup", (d, w) -> {
                    String p1 = pin1.getText().toString().trim();
                    String p2 = pin2.getText().toString().trim();
                    if (p1.length() < 4) {
                        Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!p1.equals(p2)) {
                        Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    _service.setupPin(p1);
                    _service.unlock10Min();
                    Toast.makeText(this, "PIN set! Smart Clips unlocked for 10 minutes.", Toast.LENGTH_SHORT).show();
                    buildUI();
                })
                .setNegativeButton("Skip", (d, w) -> buildUI())
                .show();
    }

    private void showPinDialog(boolean required) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(20));

        EditText pinInput = new EditText(this);
        pinInput.setHint("Enter PIN");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(pinInput);

        CheckBox keep10 = new CheckBox(this);
        keep10.setText("Keep unlocked for 10 minutes");
        keep10.setChecked(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(14), 0, 0);
        keep10.setLayoutParams(lp);
        layout.addView(keep10);

        new AlertDialog.Builder(this)
                .setTitle("🔐 Smart Clips — Enter PIN")
                .setView(layout)
                .setCancelable(!required)
                .setPositiveButton("Unlock", (d, w) -> {
                    String pin = pinInput.getText().toString().trim();
                    if (_service.verifyPin(pin)) {
                        if (keep10.isChecked()) _service.unlock10Min();
                        buildUI();
                    } else {
                        Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
                        if (required) finish();
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> finish())
                .show();
    }

    private void showChangePinDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(20));

        EditText oldPin = new EditText(this);
        oldPin.setHint("Current PIN");
        oldPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(oldPin);

        EditText newPin = new EditText(this);
        newPin.setHint("New PIN  (4–8 digits)");
        newPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(12), 0, 0);
        newPin.setLayoutParams(lp);
        layout.addView(newPin);

        EditText confPin = new EditText(this);
        confPin.setHint("Confirm New PIN");
        confPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, dp(12), 0, 0);
        confPin.setLayoutParams(lp2);
        layout.addView(confPin);

        new AlertDialog.Builder(this)
                .setTitle("Change PIN")
                .setView(layout)
                .setPositiveButton("Change", (d, w) -> {
                    if (!_service.verifyPin(oldPin.getText().toString().trim())) {
                        Toast.makeText(this, "Current PIN incorrect", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String np = newPin.getText().toString().trim();
                    String cp = confPin.getText().toString().trim();
                    if (np.length() < 4) {
                        Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!np.equals(cp)) {
                        Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    _service.setupPin(np);
                    Toast.makeText(this, "PIN changed!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Clip CRUD dialogs ─────────────────────────────────────────────────────

    private void showAddDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(20));

        EditText content = new EditText(this);
        content.setHint("Content  (text, password, token…)");
        content.setMinLines(2);
        layout.addView(content);

        EditText desc = new EditText(this);
        desc.setHint("Description  (optional label)");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(10), 0, 0);
        desc.setLayoutParams(lp);
        layout.addView(desc);

        EditText keyword = new EditText(this);
        keyword.setHint("Keyword  (use in {keyword} formula)");
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, dp(10), 0, 0);
        keyword.setLayoutParams(lp2);
        layout.addView(keyword);

        new AlertDialog.Builder(this)
                .setTitle("✦  Add Smart Clip")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String c = content.getText().toString();
                    if (c.trim().isEmpty()) {
                        Toast.makeText(this, "Content cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    _service.addClip(c, desc.getText().toString(), keyword.getText().toString());
                    Toast.makeText(this, "Smart Clip added!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditDialog(SmartClipsService.SmartClip clip) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(20));

        EditText content = new EditText(this);
        content.setHint("Content");
        content.setText(clip.content);
        content.setMinLines(2);
        layout.addView(content);

        EditText desc = new EditText(this);
        desc.setHint("Description");
        desc.setText(clip.description);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(10), 0, 0);
        desc.setLayoutParams(lp);
        layout.addView(desc);

        EditText keyword = new EditText(this);
        keyword.setHint("Keyword  (optional)");
        keyword.setText(clip.keyword);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, dp(10), 0, 0);
        keyword.setLayoutParams(lp2);
        layout.addView(keyword);

        new AlertDialog.Builder(this)
                .setTitle("Edit Clip #" + clip.serial)
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String c = content.getText().toString();
                    if (c.trim().isEmpty()) {
                        Toast.makeText(this, "Content cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    _service.updateClip(clip.withContent(c, desc.getText().toString(), keyword.getText().toString()));
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Delete", (d, w) -> new AlertDialog.Builder(this)
                        .setTitle("Delete Clip #" + clip.serial)
                        .setMessage("Are you sure?")
                        .setPositiveButton("Delete", (d2, w2) -> _service.deleteClip(clip.serial))
                        .setNegativeButton("Cancel", null)
                        .show())
                .show();
    }

    private void showLockedClipDialog(SmartClipsService.SmartClip clip) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(20));

        TextView info = new TextView(this);
        info.setText("🔒  This clip is locked. Enter PIN to view or copy it.");
        info.setTextSize(13);
        layout.addView(info);

        EditText pinInput = new EditText(this);
        pinInput.setHint("PIN");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(14), 0, 0);
        pinInput.setLayoutParams(lp);
        layout.addView(pinInput);

        new AlertDialog.Builder(this)
                .setTitle("Unlock Clip #" + clip.serial)
                .setView(layout)
                .setPositiveButton("Show", (d, w) -> {
                    if (_service.verifyPin(pinInput.getText().toString().trim()))
                        showClipContent(clip);
                    else
                        Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showClipContent(SmartClipsService.SmartClip clip) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(clip.content);
        tv.setTextSize(15);
        tv.setTextColor(C.textPrimary);
        tv.setPadding(dp(24), dp(20), dp(24), dp(20));
        tv.setTextIsSelectable(true);
        if (_isMatrix) tv.setTypeface(Typeface.MONOSPACE);
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle("Clip #" + clip.serial
                        + (clip.description.isEmpty() ? "" : "  —  " + clip.description))
                .setView(sv)
                .setPositiveButton("Copy", (d, w) -> {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null)
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Clip", clip.content));
                    Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showUnlockClipDialog(SmartClipsService.SmartClip clip) {
        EditText pinInput = new EditText(this);
        pinInput.setHint("Enter PIN to unlock clip");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setPadding(dp(24), dp(20), dp(24), dp(20));

        new AlertDialog.Builder(this)
                .setTitle("Unlock Clip #" + clip.serial)
                .setView(pinInput)
                .setPositiveButton("Unlock", (d, w) -> {
                    if (_service.verifyPin(pinInput.getText().toString().trim()))
                        _service.updateClip(clip.withLocked(false));
                    else
                        Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Clip item view ────────────────────────────────────────────────────────

    private View buildClipItemView(SmartClipsService.SmartClip clip, int position) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(12));
        GradientDrawable cardBg = new GradientDrawable();
        // Alternate subtle surface tint
        cardBg.setColor(position % 2 == 0 ? C.surface : C.surfaceVariant);
        cardBg.setCornerRadius(dp(12));
        card.setBackground(cardBg);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));

        // ── Row 1: serial + content ────────────────────────────────────────
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        TextView serial = new TextView(this);
        serial.setText("#" + clip.serial);
        serial.setTextSize(11);
        serial.setTextColor(C.primary);
        serial.setTypeface(_isMatrix ? Typeface.MONOSPACE : Typeface.DEFAULT_BOLD);
        serial.setPadding(0, 0, dp(10), 0);
        GradientDrawable serialBg = new GradientDrawable();
        serialBg.setColor(C.surfaceVariant);
        serialBg.setCornerRadius(dp(6));
        serial.setBackground(serialBg);
        serial.setPadding(dp(6), dp(2), dp(6), dp(2));
        row1.addView(serial);

        TextView contentView = new TextView(this);
        boolean isLocked = clip.locked;
        if (isLocked) {
            contentView.setText("⬛⬛⬛⬛⬛⬛⬛⬛  (Locked)");
            contentView.setTextColor(C.textHint);
            contentView.setAlpha(0.7f);
        } else {
            contentView.setText(clip.content);
            contentView.setTextColor(C.textPrimary);
        }
        contentView.setTextSize(14);
        contentView.setMaxLines(2);
        contentView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (_isMatrix) contentView.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams cvp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cvp.setMargins(dp(8), 0, 0, 0);
        row1.addView(contentView, cvp);
        card.addView(row1);

        // ── Row 2: meta (description + keyword) ───────────────────────────
        if (!clip.description.isEmpty() || !clip.keyword.isEmpty()) {
            TextView meta = new TextView(this);
            String mt = "";
            if (!clip.description.isEmpty()) mt += clip.description;
            if (!clip.keyword.isEmpty())
                mt += (mt.isEmpty() ? "" : "  ·  ") + "{" + clip.keyword + "}";
            meta.setText(mt);
            meta.setTextSize(11);
            meta.setTextColor(C.textSecondary);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            mlp.setMargins(0, dp(4), 0, 0);
            meta.setLayoutParams(mlp);
            card.addView(meta);
        }

        // ── Row 3: timestamp ──────────────────────────────────────────────
        TextView ts = new TextView(this);
        ts.setText(clip.timestamp);
        ts.setTextSize(9);
        ts.setTextColor(C.textHint);
        LinearLayout.LayoutParams tslp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tslp.setMargins(0, dp(2), 0, dp(6));
        ts.setLayoutParams(tslp);
        card.addView(ts);

        // ── Button row ────────────────────────────────────────────────────
        // Divider
        View div = new View(this);
        div.setBackgroundColor(C.divider);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dlp.setMargins(0, 0, 0, dp(8));
        div.setLayoutParams(dlp);
        card.addView(div);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);

        Button copyBtn = makePillBtn("⊕ Copy", C.green);
        copyBtn.setOnClickListener(v -> {
            if (clip.locked) { showLockedClipDialog(clip); return; }
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Clip", clip.content));
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
        });
        btnRow.addView(copyBtn);

        Button editBtn = makePillBtn("✎ Edit", C.blue);
        editBtn.setOnClickListener(v -> {
            if (clip.locked) { showLockedClipDialog(clip); return; }
            showEditDialog(clip);
        });
        LinearLayout.LayoutParams ebp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ebp.setMargins(dp(6), 0, 0, 0);
        editBtn.setLayoutParams(ebp);
        btnRow.addView(editBtn);

        Button hideBtn = makePillBtn(clip.hidden ? "◎ Show" : "◉ Hide", C.orange);
        hideBtn.setOnClickListener(v -> _service.updateClip(clip.withHidden(!clip.hidden)));
        LinearLayout.LayoutParams hbp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hbp.setMargins(dp(6), 0, 0, 0);
        hideBtn.setLayoutParams(hbp);
        btnRow.addView(hideBtn);

        Button lockBtn = makePillBtn(clip.locked ? "🔓 Unlock" : "🔒 Lock", C.purple);
        lockBtn.setOnClickListener(v -> {
            if (clip.locked) {
                showUnlockClipDialog(clip);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Lock Clip #" + clip.serial)
                        .setMessage("Lock this clip? PIN required to view or copy.")
                        .setPositiveButton("Lock", (d, w) -> _service.updateClip(clip.withLocked(true)))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        LinearLayout.LayoutParams lbp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lbp.setMargins(dp(6), 0, 0, 0);
        lockBtn.setLayoutParams(lbp);
        btnRow.addView(lockBtn);

        card.addView(btnRow);
        return card;
    }

    // ── Button factory ────────────────────────────────────────────────────────

    private Button makePillBtn(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(_isMatrix ? C.headerText : 0xFFFFFFFF);
        btn.setTextSize(11);
        btn.setPadding(dp(14), dp(6), dp(14), dp(6));
        btn.setMinWidth(0);
        btn.setMinHeight(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(16));
        btn.setBackground(bg);
        if (_isMatrix) btn.setTypeface(Typeface.MONOSPACE);
        return btn;
    }

    // ── Adapters ──────────────────────────────────────────────────────────────

    class ClipAdapter extends BaseAdapter {
        @Override public int   getCount()                  { return _filteredClips.size(); }
        @Override public Object getItem(int p)             { return _filteredClips.get(p); }
        @Override public long  getItemId(int p)            { return _filteredClips.get(p).serial; }
        @Override public View  getView(int p, View v, ViewGroup parent) {
            return buildClipItemView(_filteredClips.get(p), p);
        }
    }

    class GridClipAdapter extends BaseAdapter {
        @Override public int    getCount()               { return _filteredClips.size(); }
        @Override public Object getItem(int p)           { return _filteredClips.get(p); }
        @Override public long   getItemId(int p)         { return _filteredClips.get(p).serial; }

        @Override
        public View getView(int p, View v, ViewGroup parent) {
            SmartClipsService.SmartClip clip = _filteredClips.get(p);

            LinearLayout card = new LinearLayout(SmartClipsActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(12));
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(p % 2 == 0 ? C.surface : C.surfaceVariant);
            cardBg.setCornerRadius(dp(12));
            card.setBackground(cardBg);
            if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));

            // Serial badge
            TextView serial = new TextView(SmartClipsActivity.this);
            serial.setText("#" + clip.serial);
            serial.setTextSize(10);
            serial.setTextColor(C.primary);
            serial.setTypeface(_isMatrix ? Typeface.MONOSPACE : Typeface.DEFAULT_BOLD);
            GradientDrawable sBg = new GradientDrawable();
            sBg.setColor(C.surfaceVariant);
            sBg.setCornerRadius(dp(5));
            serial.setBackground(sBg);
            serial.setPadding(dp(5), dp(2), dp(5), dp(2));
            card.addView(serial);

            // Content
            TextView contentView = new TextView(SmartClipsActivity.this);
            if (clip.locked) {
                contentView.setText("⬛⬛⬛⬛ Locked");
                contentView.setTextColor(C.textHint);
            } else {
                contentView.setText(clip.content);
                contentView.setTextColor(C.textPrimary);
            }
            contentView.setTextSize(12);
            contentView.setMaxLines(3);
            contentView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (_isMatrix) contentView.setTypeface(Typeface.MONOSPACE);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            clp.setMargins(0, dp(6), 0, 0);
            contentView.setLayoutParams(clp);
            card.addView(contentView);

            if (!clip.description.isEmpty()) {
                TextView descV = new TextView(SmartClipsActivity.this);
                descV.setText(clip.description);
                descV.setTextSize(9);
                descV.setTextColor(C.textSecondary);
                descV.setPadding(0, dp(4), 0, 0);
                card.addView(descV);
            }

            // Button row
            LinearLayout btnRow = new LinearLayout(SmartClipsActivity.this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            brp.setMargins(0, dp(8), 0, 0);
            btnRow.setLayoutParams(brp);

            Button copyBtn = makePillBtn("Copy", C.green);
            copyBtn.setTextSize(10);
            copyBtn.setOnClickListener(vv -> {
                if (clip.locked) { showLockedClipDialog(clip); return; }
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager)
                                SmartClipsActivity.this.getSystemService(CLIPBOARD_SERVICE);
                if (cm != null)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Clip", clip.content));
                Toast.makeText(SmartClipsActivity.this, "Copied!", Toast.LENGTH_SHORT).show();
            });
            btnRow.addView(copyBtn);

            Button editBtn = makePillBtn("Edit", C.blue);
            editBtn.setTextSize(10);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ep.setMargins(dp(5), 0, 0, 0);
            editBtn.setLayoutParams(ep);
            editBtn.setOnClickListener(vv -> {
                if (clip.locked) { showLockedClipDialog(clip); return; }
                showEditDialog(clip);
            });
            btnRow.addView(editBtn);

            card.addView(btnRow);
            return card;
        }
    }

    // ── Static helper ─────────────────────────────────────────────────────────

    public static void openWithPinCheck(Context ctx, SmartClipsService service) {
        android.content.Intent intent = new android.content.Intent(ctx, SmartClipsActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    private int dp(int v) { return (int)(v * D); }
}
