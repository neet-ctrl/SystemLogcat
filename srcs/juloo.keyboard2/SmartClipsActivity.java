package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
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
    private ListView _listView;
    private GridView _gridView;
    private EditText _searchBar;
    private ClipAdapter _adapter;
    private GridClipAdapter _gridAdapter;
    private boolean _isGridView = false;
    private List<SmartClipsService.SmartClip> _allClips = new ArrayList<>();
    private List<SmartClipsService.SmartClip> _filteredClips = new ArrayList<>();
    private Handler _handler = new Handler();
    private Runnable _unlockCountdown;
    private TextView _unlockTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(android.R.style.Theme_Material_Light_NoActionBar);

        _service = SmartClipsService.getInstance(this);

        if (_service.isLockEnabled() && !_service.isUnlocked()) {
            if (!_service.isPinSetup()) {
                showFirstTimePinSetup();
            } else {
                showPinDialog(true);
            }
            return;
        }

        buildUI();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F5F5);

        LinearLayout header = buildHeader();
        root.addView(header);

        _searchBar = new EditText(this);
        _searchBar.setHint("Search clips...");
        _searchBar.setPadding(32, 20, 32, 20);
        _searchBar.setBackgroundColor(0xFFFFFFFF);
        _searchBar.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { filterClips(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(16, 8, 16, 4);
        root.addView(_searchBar, searchParams);

        _listView = new ListView(this);
        _listView.setDivider(null);
        _listView.setDividerHeight(8);
        _listView.setPadding(16, 8, 16, 8);

        _gridView = new GridView(this);
        _gridView.setNumColumns(2);
        _gridView.setHorizontalSpacing(8);
        _gridView.setVerticalSpacing(8);
        _gridView.setPadding(16, 8, 16, 8);
        _gridView.setVisibility(View.GONE);

        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(_listView, listParams);
        root.addView(_gridView, listParams);

        root.addView(buildFooter());

        if (_service.isLockEnabled() && _service.isUnlocked()) {
            _unlockTimer = new TextView(this);
            _unlockTimer.setPadding(16, 4, 16, 4);
            _unlockTimer.setTextSize(11);
            _unlockTimer.setTextColor(0xFF4CAF50);
            _unlockTimer.setGravity(Gravity.CENTER);
            root.addView(_unlockTimer);
            startUnlockCountdown();
        }

        setContentView(root);
        _service.addListener(this);
        refreshClips();
    }

    private LinearLayout buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(0xFF1565C0);
        header.setPadding(16, 16, 16, 16);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Smart Clips");
        title.setTextSize(20);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, titleParams);

        Button toggleViewBtn = new Button(this);
        toggleViewBtn.setText("Grid");
        toggleViewBtn.setTextColor(0xFFFFFFFF);
        toggleViewBtn.setBackgroundColor(0x33FFFFFF);
        toggleViewBtn.setTextSize(12);
        toggleViewBtn.setPadding(16, 8, 16, 8);
        toggleViewBtn.setOnClickListener(v -> {
            _isGridView = !_isGridView;
            toggleViewBtn.setText(_isGridView ? "List" : "Grid");
            _listView.setVisibility(_isGridView ? View.GONE : View.VISIBLE);
            _gridView.setVisibility(_isGridView ? View.VISIBLE : View.GONE);
            refreshAdapters();
        });
        header.addView(toggleViewBtn);

        if (_service.isPinSetup()) {
            Button lockBtn = new Button(this);
            lockBtn.setText(_service.isLockEnabled() ? "Lock: ON" : "Lock: OFF");
            lockBtn.setTextColor(0xFFFFFFFF);
            lockBtn.setBackgroundColor(0x33FFFFFF);
            lockBtn.setTextSize(12);
            lockBtn.setPadding(16, 8, 16, 8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(8, 0, 0, 0);
            lockBtn.setLayoutParams(lp);
            lockBtn.setOnClickListener(v -> {
                boolean newState = !_service.isLockEnabled();
                _service.setLockEnabled(newState);
                lockBtn.setText(newState ? "Lock: ON" : "Lock: OFF");
            });
            header.addView(lockBtn);

            Button changePinBtn = new Button(this);
            changePinBtn.setText("PIN");
            changePinBtn.setTextColor(0xFFFFFFFF);
            changePinBtn.setBackgroundColor(0x33FFFFFF);
            changePinBtn.setTextSize(12);
            changePinBtn.setPadding(12, 8, 12, 8);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp2.setMargins(8, 0, 0, 0);
            changePinBtn.setLayoutParams(lp2);
            changePinBtn.setOnClickListener(v -> showChangePinDialog());
            header.addView(changePinBtn);
        } else {
            Button setupPinBtn = new Button(this);
            setupPinBtn.setText("Setup PIN");
            setupPinBtn.setTextColor(0xFFFFFFFF);
            setupPinBtn.setBackgroundColor(0x33FFFFFF);
            setupPinBtn.setTextSize(12);
            setupPinBtn.setPadding(16, 8, 16, 8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(8, 0, 0, 0);
            setupPinBtn.setLayoutParams(lp);
            setupPinBtn.setOnClickListener(v -> showFirstTimePinSetup());
            header.addView(setupPinBtn);
        }

        return header;
    }

    private LinearLayout buildFooter() {
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setBackgroundColor(0xFFFFFFFF);
        footer.setPadding(16, 8, 16, 8);
        footer.setGravity(Gravity.CENTER);

        Button addBtn = new Button(this);
        addBtn.setText("+ Add Clip");
        addBtn.setBackgroundColor(0xFF1565C0);
        addBtn.setTextColor(0xFFFFFFFF);
        addBtn.setPadding(32, 16, 32, 16);
        addBtn.setOnClickListener(v -> showAddDialog());
        footer.addView(addBtn);

        return footer;
    }

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

    @Override
    public void onSmartClipsChanged() {
        runOnUiThread(this::refreshClips);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        _service.removeListener(this);
        if (_unlockCountdown != null) _handler.removeCallbacks(_unlockCountdown);
    }

    private void startUnlockCountdown() {
        if (_unlockTimer == null) return;
        _unlockCountdown = new Runnable() {
            @Override
            public void run() {
                if (_unlockTimer == null) return;
                long rem = _service.getUnlockRemainingMs();
                if (rem <= 0) {
                    _unlockTimer.setText("Auto-locked");
                    return;
                }
                long mins = rem / 60000;
                long secs = (rem % 60000) / 1000;
                _unlockTimer.setText(String.format("Auto-lock in %d:%02d", mins, secs));
                _handler.postDelayed(this, 1000);
            }
        };
        _handler.post(_unlockCountdown);
    }

    private void showFirstTimePinSetup() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 32);

        TextView info = new TextView(this);
        info.setText("Set up a PIN to protect your Smart Clips tab.");
        info.setTextSize(14);
        info.setTextColor(0xFF555555);
        info.setPadding(0, 0, 0, 16);
        layout.addView(info);

        EditText pin1 = new EditText(this);
        pin1.setHint("Enter PIN (4-8 digits)");
        pin1.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(pin1);

        EditText pin2 = new EditText(this);
        pin2.setHint("Confirm PIN");
        pin2.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 16, 0, 0);
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
                    Toast.makeText(this, "PIN setup! Smart Clips unlocked for 10 minutes.", Toast.LENGTH_SHORT).show();
                    buildUI();
                })
                .setNegativeButton("Skip", (d, w) -> buildUI())
                .show();
    }

    private void showPinDialog(boolean required) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 32);

        EditText pinInput = new EditText(this);
        pinInput.setHint("Enter PIN");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(pinInput);

        CheckBox keep10 = new CheckBox(this);
        keep10.setText("Keep unlocked for 10 minutes");
        keep10.setChecked(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 16, 0, 0);
        keep10.setLayoutParams(lp);
        layout.addView(keep10);

        new AlertDialog.Builder(this)
                .setTitle("Smart Clips - Enter PIN")
                .setView(layout)
                .setCancelable(!required)
                .setPositiveButton("Unlock", (d, w) -> {
                    String pin = pinInput.getText().toString().trim();
                    if (_service.verifyPin(pin)) {
                        if (keep10.isChecked()) {
                            _service.unlock10Min();
                        }
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
        layout.setPadding(48, 32, 48, 32);

        EditText oldPin = new EditText(this);
        oldPin.setHint("Current PIN");
        oldPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(oldPin);

        EditText newPin = new EditText(this);
        newPin.setHint("New PIN (4-8 digits)");
        newPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 16, 0, 0);
        newPin.setLayoutParams(lp);
        layout.addView(newPin);

        EditText confPin = new EditText(this);
        confPin.setHint("Confirm New PIN");
        confPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, 16, 0, 0);
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

    private void showAddDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 32);

        EditText content = new EditText(this);
        content.setHint("Content (text, password, token…)");
        content.setMinLines(2);
        layout.addView(content);

        EditText desc = new EditText(this);
        desc.setHint("Description");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 12, 0, 0);
        desc.setLayoutParams(lp);
        layout.addView(desc);

        EditText keyword = new EditText(this);
        keyword.setHint("Keyword (optional, use in {keyword})");
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, 12, 0, 0);
        keyword.setLayoutParams(lp2);
        layout.addView(keyword);

        new AlertDialog.Builder(this)
                .setTitle("Add Smart Clip")
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
        layout.setPadding(48, 32, 48, 32);

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
        lp.setMargins(0, 12, 0, 0);
        desc.setLayoutParams(lp);
        layout.addView(desc);

        EditText keyword = new EditText(this);
        keyword.setHint("Keyword (optional)");
        keyword.setText(clip.keyword);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, 12, 0, 0);
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
                .setNeutralButton("Delete", (d, w) -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Delete Clip #" + clip.serial)
                            .setMessage("Are you sure?")
                            .setPositiveButton("Delete", (d2, w2) -> _service.deleteClip(clip.serial))
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .show();
    }

    private void showLockedClipDialog(SmartClipsService.SmartClip clip) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 32);

        TextView info = new TextView(this);
        info.setText("This clip is locked. Enter PIN to view.");
        info.setTextSize(13);
        info.setTextColor(0xFF666666);
        layout.addView(info);

        EditText pinInput = new EditText(this);
        pinInput.setHint("PIN");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 16, 0, 0);
        pinInput.setLayoutParams(lp);
        layout.addView(pinInput);

        new AlertDialog.Builder(this)
                .setTitle("Unlock Clip #" + clip.serial)
                .setView(layout)
                .setPositiveButton("Show", (d, w) -> {
                    if (_service.verifyPin(pinInput.getText().toString().trim())) {
                        showClipContent(clip);
                    } else {
                        Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showClipContent(SmartClipsService.SmartClip clip) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(clip.content);
        tv.setTextSize(15);
        tv.setTextColor(0xFF212121);
        tv.setPadding(48, 32, 48, 32);
        tv.setTextIsSelectable(true);
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle("Clip #" + clip.serial + (clip.description.isEmpty() ? "" : " - " + clip.description))
                .setView(sv)
                .setPositiveButton("Copy", (d, w) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Clip", clip.content));
                    Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private View buildClipItemView(SmartClipsService.SmartClip clip, int position) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 20, 24, 16);
        card.setBackgroundColor(position % 2 == 0 ? 0xFFFFFFFF : 0xFFF9F9F9);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        TextView serial = new TextView(this);
        serial.setText("#" + clip.serial);
        serial.setTextSize(13);
        serial.setTextColor(0xFF1565C0);
        serial.setTypeface(Typeface.DEFAULT_BOLD);
        serial.setPadding(0, 0, 12, 0);
        row1.addView(serial);

        TextView contentView = new TextView(this);
        boolean isLocked = clip.locked;
        if (isLocked) {
            contentView.setText("●●●●●●●● (Locked)");
            contentView.setTextColor(0xFFAAAAAA);
            contentView.setAlpha(0.7f);
        } else {
            contentView.setText(clip.content);
            contentView.setTextColor(0xFF212121);
        }
        contentView.setTextSize(14);
        contentView.setMaxLines(2);
        contentView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams cvp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row1.addView(contentView, cvp);
        card.addView(row1);

        if (!clip.description.isEmpty() || !clip.keyword.isEmpty()) {
            TextView meta = new TextView(this);
            String metaText = "";
            if (!clip.description.isEmpty()) metaText += clip.description;
            if (!clip.keyword.isEmpty()) metaText += (metaText.isEmpty() ? "" : " | ") + "{" + clip.keyword + "}";
            meta.setText(metaText);
            meta.setTextSize(11);
            meta.setTextColor(0xFF888888);
            meta.setPadding(0, 4, 0, 0);
            card.addView(meta);
        }

        TextView tsView = new TextView(this);
        tsView.setText(clip.timestamp);
        tsView.setTextSize(10);
        tsView.setTextColor(0xFFAAAAAA);
        tsView.setPadding(0, 2, 0, 0);
        card.addView(tsView);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams btnRowp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRowp.setMargins(0, 8, 0, 0);
        btnRow.setLayoutParams(btnRowp);

        Button copyBtn = makeSmallBtn("Copy", 0xFF43A047);
        copyBtn.setOnClickListener(v -> {
            if (clip.locked) {
                showLockedClipDialog(clip);
            } else {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Clip", clip.content));
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
            }
        });
        btnRow.addView(copyBtn);

        Button editBtn = makeSmallBtn("Edit", 0xFF1565C0);
        editBtn.setOnClickListener(v -> {
            if (clip.locked) {
                showLockedClipDialog(clip);
            } else {
                showEditDialog(clip);
            }
        });
        LinearLayout.LayoutParams ebp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ebp.setMargins(8, 0, 0, 0);
        editBtn.setLayoutParams(ebp);
        btnRow.addView(editBtn);

        Button hideBtn = makeSmallBtn(clip.hidden ? "Show" : "Hide", 0xFFFF6F00);
        hideBtn.setOnClickListener(v -> {
            _service.updateClip(clip.withHidden(!clip.hidden));
        });
        LinearLayout.LayoutParams hbp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hbp.setMargins(8, 0, 0, 0);
        hideBtn.setLayoutParams(hbp);
        btnRow.addView(hideBtn);

        Button lockBtn = makeSmallBtn(clip.locked ? "Unlock" : "Lock", 0xFFAA00FF);
        lockBtn.setOnClickListener(v -> {
            if (clip.locked) {
                showUnlockClipDialog(clip);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Lock Clip #" + clip.serial)
                        .setMessage("Lock this clip? You'll need PIN to view/copy it.")
                        .setPositiveButton("Lock", (d, w) -> _service.updateClip(clip.withLocked(true)))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        LinearLayout.LayoutParams lbp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lbp.setMargins(8, 0, 0, 0);
        lockBtn.setLayoutParams(lbp);
        btnRow.addView(lockBtn);

        card.addView(btnRow);
        return card;
    }

    private void showUnlockClipDialog(SmartClipsService.SmartClip clip) {
        EditText pinInput = new EditText(this);
        pinInput.setHint("Enter PIN to unlock");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setPadding(48, 24, 48, 24);

        new AlertDialog.Builder(this)
                .setTitle("Unlock Clip #" + clip.serial)
                .setView(pinInput)
                .setPositiveButton("Unlock", (d, w) -> {
                    if (_service.verifyPin(pinInput.getText().toString().trim())) {
                        _service.updateClip(clip.withLocked(false));
                    } else {
                        Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Button makeSmallBtn(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setBackgroundColor(color);
        btn.setTextSize(11);
        btn.setPadding(16, 8, 16, 8);
        btn.setMinWidth(0);
        btn.setMinHeight(0);
        return btn;
    }

    class ClipAdapter extends BaseAdapter {
        @Override public int getCount() { return _filteredClips.size(); }
        @Override public Object getItem(int p) { return _filteredClips.get(p); }
        @Override public long getItemId(int p) { return _filteredClips.get(p).serial; }
        @Override public View getView(int p, View v, ViewGroup parent) {
            return buildClipItemView(_filteredClips.get(p), p);
        }
    }

    class GridClipAdapter extends BaseAdapter {
        @Override public int getCount() { return _filteredClips.size(); }
        @Override public Object getItem(int p) { return _filteredClips.get(p); }
        @Override public long getItemId(int p) { return _filteredClips.get(p).serial; }
        @Override public View getView(int p, View v, ViewGroup parent) {
            SmartClipsService.SmartClip clip = _filteredClips.get(p);
            LinearLayout card = new LinearLayout(SmartClipsActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(16, 16, 16, 12);
            card.setBackgroundColor(0xFFFFFFFF);

            TextView serial = new TextView(SmartClipsActivity.this);
            serial.setText("#" + clip.serial);
            serial.setTextSize(12);
            serial.setTextColor(0xFF1565C0);
            serial.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(serial);

            TextView contentView = new TextView(SmartClipsActivity.this);
            if (clip.locked) {
                contentView.setText("●●●● Locked");
                contentView.setTextColor(0xFFAAAAAA);
            } else {
                contentView.setText(clip.content);
                contentView.setTextColor(0xFF212121);
            }
            contentView.setTextSize(13);
            contentView.setMaxLines(3);
            contentView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams cvp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            card.addView(contentView, cvp);

            if (!clip.description.isEmpty()) {
                TextView descV = new TextView(SmartClipsActivity.this);
                descV.setText(clip.description);
                descV.setTextSize(10);
                descV.setTextColor(0xFF888888);
                descV.setPadding(0, 4, 0, 0);
                card.addView(descV);
            }

            LinearLayout btnRow = new LinearLayout(SmartClipsActivity.this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            brp.setMargins(0, 8, 0, 0);
            btnRow.setLayoutParams(brp);

            Button copyBtn = makeSmallBtn("Copy", 0xFF43A047);
            copyBtn.setOnClickListener(vv -> {
                if (clip.locked) { showLockedClipDialog(clip); return; }
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Clip", clip.content));
                Toast.makeText(SmartClipsActivity.this, "Copied!", Toast.LENGTH_SHORT).show();
            });
            btnRow.addView(copyBtn);

            Button editBtn = makeSmallBtn("Edit", 0xFF1565C0);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ep.setMargins(6, 0, 0, 0);
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

    public static void openWithPinCheck(Context ctx, SmartClipsService service) {
        if (service.isLockEnabled() && !service.isUnlocked() && service.isPinSetup()) {
        }
        android.content.Intent intent = new android.content.Intent(ctx, SmartClipsActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }
}
