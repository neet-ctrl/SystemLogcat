package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ClipboardHistoryActivity extends Activity {

    // ── State ─────────────────────────────────────────────────────────────────
    private ListView  _listView;
    private ClipboardHistoryService _service;
    private ClipAdapter _adapter;
    private ThemeManager.ThemeColors C;
    private float D;
    private String _createdSig;

    // ══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        C = ThemeManager.colors(this);
        D = getResources().getDisplayMetrics().density;
        _createdSig = ThemeManager.signature(this);

        _service = ClipboardHistoryService.get_service(this);
        if (_service == null) {
            ClipboardHistoryService.on_startup(getApplicationContext(), null);
            _service = ClipboardHistoryService.get_service(this);
        }
        if (_service == null) {
            showCriticalError("Clipboard Service could not be initialized.\n\nMake sure Unexpected Keyboard is enabled in Settings → System → Input.");
            return;
        }
        buildUI();
        ThemeManager.attachMatrixOverlay(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!ThemeManager.signature(this).equals(_createdSig)) recreate();
        else updateList();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Root UI
    // ══════════════════════════════════════════════════════════════════════════

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C.background);

        // ── Header ────────────────────────────────────────────────
        root.addView(buildHeader());

        // ── Search bar ────────────────────────────────────────────
        root.addView(buildSearchBar());

        // ── List ──────────────────────────────────────────────────
        _listView = new ListView(this);
        _listView.setDivider(null);
        _listView.setDividerHeight(dp(8));
        _listView.setPadding(dp(12), dp(6), dp(12), dp(8));
        _listView.setClipToPadding(false);
        _listView.setBackgroundColor(0);
        _listView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(_listView);

        // ── Bottom action bar ─────────────────────────────────────
        root.addView(buildBottomBar());

        setContentView(root);
        updateList();
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

        // ← Back
        Button back = new Button(this);
        back.setText("←");
        back.setTextSize(18);
        back.setTextColor(C.headerText);
        back.setBackground(null);
        back.setPadding(0, 0, dp(8), 0);
        back.setMinWidth(0); back.setMinHeight(0);
        back.setOnClickListener(v -> finish());
        h.addView(back);

        // Title column
        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("📋  Clipboard History");
        title.setTextSize(16);
        title.setTextColor(C.headerText);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(title);

        int count = _service != null ? _service.get_history_entries().size() : 0;
        TextView sub = new TextView(this);
        sub.setText(count + " entr" + (count != 1 ? "ies" : "y") + "  ·  tap = edit  ·  hold = copy");
        sub.setTextSize(9);
        sub.setTextColor(0xAAFFFFFF);
        titleCol.addView(sub);
        h.addView(titleCol);

        // 🔐 Smart Clips shortcut pill
        Button smartBtn = makeHeaderPill("🔐 Smart", C.primary);
        smartBtn.setOnClickListener(v -> startActivity(new Intent(this, SmartClipsActivity.class)));
        h.addView(smartBtn);

        return h;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Search bar
    // ══════════════════════════════════════════════════════════════════════════

    private View buildSearchBar() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setPadding(dp(12), dp(10), dp(12), dp(4));

        EditText searchBar = new EditText(this);
        searchBar.setHint("🔍  Search clips…");
        searchBar.setHintTextColor(C.textHint);
        searchBar.setTextColor(C.textPrimary);
        searchBar.setTextSize(13);
        searchBar.setSingleLine(true);
        searchBar.setPadding(dp(14), dp(11), dp(14), dp(11));
        searchBar.setBackground(makeRoundedBg(C.surfaceVariant, dp(22)));
        searchBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        searchBar.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (_adapter != null) _adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        wrap.addView(searchBar);
        return wrap;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Bottom action bar
    // ══════════════════════════════════════════════════════════════════════════

    private View buildBottomBar() {
        // Thin accent line above bar
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        View accent = new View(this);
        accent.setBackgroundColor(C.primary);
        accent.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        accent.setAlpha(0.4f);
        container.addView(accent);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(C.surface);
        bar.setPadding(dp(10), dp(10), dp(10), dp(12));

        // ── + Add
        Button btnAdd = makeBarBtn("＋", "Add", C.green);
        btnAdd.setOnClickListener(v -> showAddDialog());
        bar.addView(btnAdd);

        bar.addView(barDivider());

        // ── 🗑 Bulk Delete
        Button btnBulk = makeBarBtn("🗑", "Bulk", C.orange);
        btnBulk.setOnClickListener(v -> showBulkDeleteDialog());
        bar.addView(btnBulk);

        bar.addView(barDivider());

        // ── 📤 Export
        Button btnExport = makeBarBtn("📤", "Export", C.blue);
        btnExport.setOnClickListener(v -> exportHistory());
        bar.addView(btnExport);

        bar.addView(barDivider());

        // ── 🗑 Clear All
        Button btnClear = makeBarBtn("✕", "Clear", 0xFFEF4444);
        btnClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear All History")
                    .setMessage("Delete ALL clipboard entries? This cannot be undone.")
                    .setPositiveButton("Delete All", (d, w) -> {
                        _service.clear_history();
                        updateList();
                        toast("All history cleared.");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        bar.addView(btnClear);

        container.addView(bar);
        return container;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // List management
    // ══════════════════════════════════════════════════════════════════════════

    private void updateList() {
        if (_service == null || _listView == null) return;
        List<ClipboardHistoryService.HistoryEntry> history = _service.get_history_entries();
        // Pinned entries always float to the top; within each group sort newest-first
        java.util.Collections.sort(history, (a, b) -> {
            if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
            return b.timestamp.compareTo(a.timestamp);
        });
        _adapter = new ClipAdapter(history);
        _listView.setAdapter(_adapter);
        // Refresh subtitle count in header (quick rebuild avoided — update via tag)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Adapter — modern dark glass cards
    // ══════════════════════════════════════════════════════════════════════════

    class ClipAdapter extends BaseAdapter {
        List<ClipboardHistoryService.HistoryEntry> items;
        List<ClipboardHistoryService.HistoryEntry> filtered;

        ClipAdapter(List<ClipboardHistoryService.HistoryEntry> items) {
            this.items    = items;
            this.filtered = new java.util.ArrayList<>(items);
        }

        void filter(String q) {
            filtered.clear();
            String lq = q.toLowerCase();
            if (q.isEmpty()) {
                filtered.addAll(items);
            } else {
                for (ClipboardHistoryService.HistoryEntry e : items) {
                    if (e.content.toLowerCase().contains(lq)
                            || e.description.toLowerCase().contains(lq))
                        filtered.add(e);
                }
            }
            notifyDataSetChanged();
        }

        @Override public int     getCount()              { return filtered.size(); }
        @Override public Object  getItem(int p)          { return filtered.get(p); }
        @Override public long    getItemId(int p)        { return p; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            ClipboardHistoryService.HistoryEntry ent = filtered.get(pos);

            // ── Card container ────────────────────────────────────
            LinearLayout card = new LinearLayout(ClipboardHistoryActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(12), dp(12));
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(pos % 2 == 0 ? C.surface : C.surfaceVariant);
            cardBg.setCornerRadius(dp(14));
            card.setBackground(cardBg);
            if (android.os.Build.VERSION.SDK_INT >= 21)
                card.setElevation(dp(2));

            // ── Top row: index chip + content + action buttons ─────
            LinearLayout topRow = new LinearLayout(ClipboardHistoryActivity.this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            // #N index chip
            TextView idx = new TextView(ClipboardHistoryActivity.this);
            idx.setText("#" + (pos + 1));
            idx.setTextSize(9);
            idx.setTextColor(C.primary);
            idx.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable idxBg = new GradientDrawable();
            idxBg.setColor(C.surfaceVariant);
            idxBg.setCornerRadius(dp(10));
            idx.setBackground(idxBg);
            idx.setPadding(dp(6), dp(2), dp(6), dp(2));
            topRow.addView(idx);

            // Content preview
            TextView contentTv = new TextView(ClipboardHistoryActivity.this);
            contentTv.setTextSize(13);
            contentTv.setTextColor(C.textPrimary);
            contentTv.setMaxLines(2);
            contentTv.setEllipsize(TextUtils.TruncateAt.END);
            String preview = ent.content.trim();
            contentTv.setText(preview);
            LinearLayout.LayoutParams cvLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            cvLp.setMargins(dp(8), 0, dp(8), 0);
            contentTv.setLayoutParams(cvLp);
            topRow.addView(contentTv);

            // ── Copy button (green circle)
            Button copyBtn = makeCardActionBtn("📋", C.green);
            copyBtn.setOnClickListener(v -> {
                copyText(ent.content);
                toast("Copied!");
            });
            topRow.addView(copyBtn);

            // ── Pin button (📌 pinned / 📍 unpinned)
            boolean isPinned = ent.pinned;
            Button pinBtn = makeCardActionBtn(isPinned ? "📌" : "📍",
                    isPinned ? C.primary : C.textHint);
            pinBtn.setOnClickListener(v -> {
                ClipboardHistoryService.togglePin(ent.content);
                updateList();
            });
            LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            pinLp.setMargins(dp(4), 0, 0, 0);
            pinBtn.setLayoutParams(pinLp);
            topRow.addView(pinBtn);

            // ── Delete button (red circle)
            Button delBtn = makeCardActionBtn("🗑", 0xFFEF4444);
            delBtn.setOnClickListener(v -> {
                new AlertDialog.Builder(ClipboardHistoryActivity.this)
                        .setTitle("Delete Entry?")
                        .setMessage("Remove this clipboard entry?")
                        .setPositiveButton("Delete", (d, w) -> {
                            _service.remove_history_entry(ent.content);
                            updateList();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            LinearLayout.LayoutParams dlLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            dlLp.setMargins(dp(4), 0, 0, 0);
            delBtn.setLayoutParams(dlLp);
            topRow.addView(delBtn);

            card.addView(topRow);

            // ── Meta row: timestamp · description · version ────────
            StringBuilder meta = new StringBuilder("🕒 " + ent.timestamp);
            if (!ent.description.isEmpty()) meta.append("  ·  📝 ").append(ent.description);
            if (!ent.version.isEmpty())     meta.append("  ·  📦 v").append(ent.version);
            TextView metaTv = new TextView(ClipboardHistoryActivity.this);
            metaTv.setText(meta.toString());
            metaTv.setTextSize(10);
            metaTv.setTextColor(C.textSecondary);
            LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            mLp.setMargins(0, dp(5), 0, 0);
            metaTv.setLayoutParams(mLp);
            card.addView(metaTv);

            // ── "▼ Expand" if content is long ─────────────────────
            if (ent.content.length() > 80) {
                Button expandBtn = new Button(ClipboardHistoryActivity.this);
                expandBtn.setText("▼  Show full");
                expandBtn.setTextSize(10);
                expandBtn.setTextColor(C.primary);
                expandBtn.setBackground(null);
                expandBtn.setPadding(0, dp(4), 0, 0);
                expandBtn.setMinWidth(0); expandBtn.setMinHeight(0);
                LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                eLp.gravity = Gravity.START;
                expandBtn.setLayoutParams(eLp);
                expandBtn.setOnClickListener(v -> showFullContent(ent));
                card.addView(expandBtn);
            }

            // ── Card tap = edit, long = copy ──────────────────────
            card.setOnClickListener(v -> showEditDialog(ent));
            card.setOnLongClickListener(v -> {
                copyText(ent.content);
                toast("Copied!");
                return true;
            });

            return card;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Dialogs
    // ══════════════════════════════════════════════════════════════════════════

    private void showFullContent(ClipboardHistoryService.HistoryEntry ent) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(ent.content);
        tv.setTextSize(14);
        tv.setTextColor(C.textPrimary);
        tv.setPadding(dp(20), dp(16), dp(20), dp(16));
        tv.setTextIsSelectable(true);
        tv.setBackgroundColor(C.background);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("Full Content")
                .setView(sv)
                .setPositiveButton("Copy", (d, w) -> { copyText(ent.content); toast("Copied!"); })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showEditDialog(ClipboardHistoryService.HistoryEntry ent) {
        EditText editContent = new EditText(this);
        editContent.setText(ent.content);
        editContent.setHint("Content");
        editContent.setTextColor(C.textPrimary);

        EditText editDesc = new EditText(this);
        editDesc.setText(ent.description);
        editDesc.setHint("Description (optional)");
        editDesc.setTextColor(C.textPrimary);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(8));
        layout.addView(editContent);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.setMargins(0, dp(10), 0, 0);
        editDesc.setLayoutParams(descLp);
        layout.addView(editDesc);

        new AlertDialog.Builder(this)
                .setTitle("Edit Entry")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    _service.add_clip_with_metadata(
                            editContent.getText().toString(),
                            editDesc.getText().toString(), "2");
                    updateList();
                })
                .setNegativeButton("Delete", (d, w) -> {
                    _service.remove_history_entry(ent.content);
                    updateList();
                })
                .setNeutralButton("Share", (d, w) -> {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setAction(Intent.ACTION_SEND);
                    share.putExtra(Intent.EXTRA_TEXT, ent.content);
                    share.setType("text/plain");
                    startActivity(Intent.createChooser(share, null));
                })
                .show();
    }

    private void showAddDialog() {
        EditText inputContent = new EditText(this);
        inputContent.setHint("Content");
        inputContent.setTextColor(C.textPrimary);

        EditText inputDesc = new EditText(this);
        inputDesc.setHint("Description (optional)");
        inputDesc.setTextColor(C.textPrimary);

        Spinner fmtSpinner = new Spinner(this);
        String[] formats = {".txt", ".pdf", ".js", ".java", ".html"};
        ArrayAdapter<String> spinAdapt = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, formats);
        fmtSpinner.setAdapter(spinAdapt);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(8));
        layout.addView(inputContent);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.setMargins(0, dp(10), 0, 0);
        inputDesc.setLayoutParams(descLp);
        layout.addView(inputDesc);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spLp.setMargins(0, dp(10), 0, 0);
        fmtSpinner.setLayoutParams(spLp);
        layout.addView(fmtSpinner);

        new AlertDialog.Builder(this)
                .setTitle("Add New Entry")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String text = inputContent.getText().toString();
                    String desc = inputDesc.getText().toString();
                    String ext  = fmtSpinner.getSelectedItem().toString();
                    _service.add_clip_with_metadata(text, desc, "1");
                    updateList();
                    if (ext.equals(".pdf"))       exportAsPdf(text, desc);
                    else if (ext.equals(".txt"))  exportAsTxt(text, desc);
                    else                          exportAsGenericText(text, desc, ext);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Bulk delete
    // ══════════════════════════════════════════════════════════════════════════

    private void showBulkDeleteDialog() {
        final String[] options = {
            "🕐  Last 10 newest entries",
            "🕑  Last 20 newest entries",
            "🕒  Last 50 newest entries",
            "🕓  Last 100 newest entries",
            "🔢  Custom number (newest)…",
            "⏳  Remove N oldest entries…",
            "📅  Before a specific date…",
            "📆  Date range (from → to)…",
            "☀️  Today's entries",
            "📅  This week's entries",
            "🗓  This month's entries"
        };
        new AlertDialog.Builder(this)
                .setTitle("Bulk Delete — Choose Option")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:  confirmAndDeleteLastN(10);     break;
                        case 1:  confirmAndDeleteLastN(20);     break;
                        case 2:  confirmAndDeleteLastN(50);     break;
                        case 3:  confirmAndDeleteLastN(100);    break;
                        case 4:  showCustomNumberDialog(false); break;
                        case 5:  showCustomNumberDialog(true);  break;
                        case 6:  showDeleteBeforeDatePicker();  break;
                        case 7:  showDateRangePicker();         break;
                        case 8:  deleteForPeriod("today");      break;
                        case 9:  deleteForPeriod("week");       break;
                        case 10: deleteForPeriod("month");      break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCustomNumberDialog(boolean oldest) {
        EditText input = new EditText(this);
        input.setHint("e.g. 35");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setPadding(dp(20), dp(12), dp(20), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("Delete N " + (oldest ? "Oldest" : "Newest") + " Entries")
                .setMessage("Enter how many " + (oldest ? "oldest" : "most recent") + " entries to delete:")
                .setView(input)
                .setPositiveButton("Delete", (d, w) -> {
                    String raw = input.getText().toString().trim();
                    if (raw.isEmpty()) return;
                    try {
                        int n = Integer.parseInt(raw);
                        if (n <= 0) { toast("Enter a number > 0."); return; }
                        if (oldest) confirmAndDeleteOldestN(n);
                        else        confirmAndDeleteLastN(n);
                    } catch (NumberFormatException e) { toast("Invalid number."); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmAndDeleteLastN(int n) {
        int total = _service.get_history_entries().size();
        int actual = Math.min(n, total);
        new AlertDialog.Builder(this)
                .setTitle("Confirm Delete Newest")
                .setMessage("Delete the " + actual + " most recent entr" + (actual == 1 ? "y" : "ies") + "?\n(" + total + " total)")
                .setPositiveButton("Delete", (d, w) -> {
                    int rem = _service.remove_last_n_entries(n);
                    updateList();
                    toast(rem + " entr" + (rem == 1 ? "y" : "ies") + " deleted.");
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void confirmAndDeleteOldestN(int n) {
        int total = _service.get_history_entries().size();
        int actual = Math.min(n, total);
        new AlertDialog.Builder(this)
                .setTitle("Confirm Delete Oldest")
                .setMessage("Delete the " + actual + " oldest entr" + (actual == 1 ? "y" : "ies") + "?\n(" + total + " total)")
                .setPositiveButton("Delete", (d, w) -> {
                    int rem = _service.remove_oldest_n_entries(n);
                    updateList();
                    toast(rem + " oldest entr" + (rem == 1 ? "y" : "ies") + " deleted.");
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showDeleteBeforeDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar cc = Calendar.getInstance();
            cc.set(year, month, day, 0, 0, 0); cc.set(Calendar.MILLISECOND, 0);
            String dateStr = year + "-" + String.format("%02d", month + 1) + "-" + String.format("%02d", day);
            new AlertDialog.Builder(this)
                    .setTitle("Confirm Delete Before Date")
                    .setMessage("Delete all entries before " + dateStr + "?")
                    .setPositiveButton("Delete", (d, w) -> {
                        int rem = _service.remove_entries_before_date(cc.getTime());
                        updateList();
                        toast(rem + " entr" + (rem == 1 ? "y" : "ies") + " deleted.");
                    })
                    .setNegativeButton("Cancel", null).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showDateRangePicker() {
        final Calendar now = Calendar.getInstance();
        new DatePickerDialog(this, (v, fy, fm, fd) -> {
            Calendar from = Calendar.getInstance();
            from.set(fy, fm, fd, 0, 0, 0); from.set(Calendar.MILLISECOND, 0);
            final Date fromDate = from.getTime();
            final String fromStr = fy + "-" + String.format("%02d", fm + 1) + "-" + String.format("%02d", fd);
            new DatePickerDialog(this, (v2, ty, tm, td) -> {
                Calendar to = Calendar.getInstance();
                to.set(ty, tm, td, 23, 59, 59); to.set(Calendar.MILLISECOND, 999);
                final Date toDate = to.getTime();
                final String toStr = ty + "-" + String.format("%02d", tm + 1) + "-" + String.format("%02d", td);
                if (fromDate.after(toDate)) { toast("FROM date must be before TO date."); return; }
                new AlertDialog.Builder(this)
                        .setTitle("Confirm Delete Range")
                        .setMessage("Delete all entries from " + fromStr + " to " + toStr + "?")
                        .setPositiveButton("Delete", (d, w) -> {
                            int rem = _service.remove_entries_in_range(fromDate, toDate);
                            updateList();
                            toast(rem + " entr" + (rem == 1 ? "y" : "ies") + " deleted.");
                        })
                        .setNegativeButton("Cancel", null).show();
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void deleteForPeriod(String period) {
        Calendar from = Calendar.getInstance(), to = Calendar.getInstance();
        if (period.equals("today")) {
            from.set(Calendar.HOUR_OF_DAY, 0); from.set(Calendar.MINUTE, 0);
            from.set(Calendar.SECOND, 0);      from.set(Calendar.MILLISECOND, 0);
            to.set(Calendar.HOUR_OF_DAY, 23);  to.set(Calendar.MINUTE, 59);
            to.set(Calendar.SECOND, 59);        to.set(Calendar.MILLISECOND, 999);
        } else if (period.equals("week")) {
            from.set(Calendar.DAY_OF_WEEK, from.getFirstDayOfWeek());
            from.set(Calendar.HOUR_OF_DAY, 0); from.set(Calendar.MINUTE, 0);
            from.set(Calendar.SECOND, 0);      from.set(Calendar.MILLISECOND, 0);
            to.set(Calendar.HOUR_OF_DAY, 23);  to.set(Calendar.MINUTE, 59);
            to.set(Calendar.SECOND, 59);        to.set(Calendar.MILLISECOND, 999);
        } else {
            from.set(Calendar.DAY_OF_MONTH, 1);
            from.set(Calendar.HOUR_OF_DAY, 0); from.set(Calendar.MINUTE, 0);
            from.set(Calendar.SECOND, 0);      from.set(Calendar.MILLISECOND, 0);
            to.set(Calendar.HOUR_OF_DAY, 23);  to.set(Calendar.MINUTE, 59);
            to.set(Calendar.SECOND, 59);        to.set(Calendar.MILLISECOND, 999);
        }
        String label = period.equals("today") ? "Today" : period.equals("week") ? "This Week" : "This Month";
        new AlertDialog.Builder(this)
                .setTitle("Confirm Delete — " + label)
                .setMessage("Delete all entries from " + label.toLowerCase() + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    int rem = _service.remove_entries_in_range(from.getTime(), to.getTime());
                    updateList();
                    toast(rem + " entr" + (rem == 1 ? "y" : "ies") + " deleted.");
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Export helpers (unchanged logic, all kept)
    // ══════════════════════════════════════════════════════════════════════════

    private void exportHistory() {
        List<ClipboardHistoryService.HistoryEntry> history = _service.get_history_entries();
        if (history.isEmpty()) { toast("No history to export."); return; }
        StringBuilder sb = new StringBuilder();
        for (ClipboardHistoryService.HistoryEntry e : history) {
            sb.append("Time: ").append(e.timestamp).append("\n");
            sb.append("Version: ").append(e.version).append("\n");
            sb.append("Description: ").append(e.description).append("\n");
            sb.append("Content: ").append(e.content).append("\n---\n");
        }
        try {
            java.io.File file = new java.io.File(getExternalFilesDir(null),
                    "clipboard_export_" + System.currentTimeMillis() + ".txt");
            java.io.FileWriter w = new java.io.FileWriter(file);
            w.write(sb.toString()); w.close();
            shareFile(file, "text/plain");
        } catch (Exception e) { toast("Export failed: " + e.getMessage()); }
    }

    private void exportAsTxt(String text, String desc) {
        String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()).format(new java.util.Date());
        String content = "Time: " + ts + "\nDescription: " + desc + "\nContent: " + text;
        try {
            java.io.File file = new java.io.File(getExternalFilesDir(null),
                    "note_" + System.currentTimeMillis() + ".txt");
            java.io.FileWriter w = new java.io.FileWriter(file);
            w.write(content); w.close();
            shareFile(file, "text/plain");
        } catch (Exception e) { toast("Export failed: " + e.getMessage()); }
    }

    private void exportAsGenericText(String text, String desc, String ext) {
        String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()).format(new java.util.Date());
        String content = "Time: " + ts + "\nDescription: " + desc + "\nContent:\n" + text;
        try {
            java.io.File file = new java.io.File(getExternalFilesDir(null),
                    "note_" + System.currentTimeMillis() + ext);
            java.io.FileWriter w = new java.io.FileWriter(file);
            w.write(content); w.close();
            shareFile(file, "text/plain");
        } catch (Exception e) { toast("Export failed: " + e.getMessage()); }
    }

    private void exportAsPdf(String text, String desc) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) {
            toast("PDF export requires Android 4.4+"); return;
        }
        try {
            android.graphics.pdf.PdfDocument doc = new android.graphics.pdf.PdfDocument();
            android.graphics.pdf.PdfDocument.PageInfo pi =
                    new android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create();
            android.graphics.pdf.PdfDocument.Page page = doc.startPage(pi);
            android.graphics.Canvas cv = page.getCanvas();
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setTextSize(12);
            int x = 50, y = 50;
            String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    java.util.Locale.getDefault()).format(new java.util.Date());
            cv.drawText("Time: " + ts, x, y, paint); y += 20;
            cv.drawText("Description: " + desc, x, y, paint); y += 30;
            paint.setTextSize(10);
            for (String line : text.split("\n")) {
                cv.drawText(line, x, y, paint); y += 15;
                if (y > 800) break;
            }
            doc.finishPage(page);
            java.io.File file = new java.io.File(getExternalFilesDir(null),
                    "note_" + System.currentTimeMillis() + ".pdf");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            doc.writeTo(fos); doc.close(); fos.close();
            shareFile(file, "application/pdf");
        } catch (Exception e) { toast("PDF Export failed: " + e.getMessage()); }
    }

    private void shareFile(java.io.File file, String mimeType) {
        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Export"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // View helpers
    // ══════════════════════════════════════════════════════════════════════════

    /** Small circular action button inside a list card (copy, delete). */
    private Button makeCardActionBtn(String icon, int color) {
        Button b = new Button(this);
        b.setText(icon);
        b.setTextSize(13);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0); b.setMinHeight(0);
        int sz = dp(34);
        b.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(colorWithAlpha(color, 0x33));
        b.setBackground(bg);
        return b;
    }

    /** Bottom bar icon + label button. */
    private Button makeBarBtn(String icon, String label, int accentColor) {
        Button b = new Button(this);
        b.setText(icon + "\n" + label);
        b.setTextSize(9);
        b.setTextColor(accentColor);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(6), dp(6), dp(6), dp(6));
        b.setMinWidth(0); b.setMinHeight(0);
        b.setGravity(Gravity.CENTER);
        b.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colorWithAlpha(accentColor, 0x18));
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        return b;
    }

    /** Small pill text button for header. */
    private Button makeHeaderPill(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(10);
        b.setTextColor(0xFFFFFFFF);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(10), dp(5), dp(10), dp(5));
        b.setMinWidth(0); b.setMinHeight(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(20));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(6), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    /** Rounded rectangle drawable. */
    private android.graphics.drawable.Drawable makeRoundedBg(int color, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radius);
        return gd;
    }

    /** Thin vertical divider between bottom bar buttons. */
    private View barDivider() {
        View v = new View(this);
        v.setBackgroundColor(C.divider);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(1), dp(28)));
        return v;
    }

    private static int colorWithAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private void copyText(String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm != null)
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Clip", text));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) { return (int)(v * D); }

    // ══════════════════════════════════════════════════════════════════════════
    // Error fallback
    // ══════════════════════════════════════════════════════════════════════════

    private void showCriticalError(String message) {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setBackgroundColor(C.background);
        ll.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("⚠  Error");
        title.setTextSize(20);
        title.setTextColor(0xFFEF4444);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        ll.addView(title);

        Button copy = new Button(this);
        copy.setText("Copy Error Details");
        copy.setOnClickListener(v -> { copyText(message); toast("Copied!"); });
        ll.addView(copy);

        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(13);
        tv.setTextColor(C.textSecondary);
        tv.setPadding(0, dp(16), 0, 0);
        tv.setTextIsSelectable(true);
        sv.addView(tv);
        ll.addView(sv);
        setContentView(ll);
    }
}
