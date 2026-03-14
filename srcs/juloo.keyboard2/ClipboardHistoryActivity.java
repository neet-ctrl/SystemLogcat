package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ClipboardHistoryActivity extends Activity {
    private ListView listView;
    private ClipboardHistoryService service;
    private ClipboardAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // Apply a basic material theme to ensure UI consistency
            setTheme(android.R.style.Theme_Material_Light_NoActionBar);
            
            setContentView(R.layout.clipboard_history_activity);
            
            // Check if service is initialized. If not, try to initialize it.
            service = ClipboardHistoryService.get_service(this);
            if (service == null) {
                // If the service is null, it usually means VERSION.SDK_INT <= 11 or internal state issue.
                // We attempt to trigger initialization manually for the activity context.
                ClipboardHistoryService.on_startup(getApplicationContext(), null);
                service = ClipboardHistoryService.get_service(this);
            }
            
            if (service == null) {
                showError("Clipboard Service could not be initialized.\n\nPlease ensure Unexpected Keyboard is enabled in Settings > System > Languages & Input.");
                return;
            }

            listView = findViewById(R.id.clipboard_history_list);
            if (listView == null) {
                showError("UI Error: ListView not found in layout.");
                return;
            }
            
            EditText searchBar = findViewById(R.id.search_bar);
            if (searchBar != null) {
                searchBar.addTextChangedListener(new android.text.TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (adapter != null) adapter.filter(s.toString());
                    }
                    @Override public void afterTextChanged(android.text.Editable s) {}
                });
            }
            
            updateList();

            View btnAdd = findViewById(R.id.btn_add_new);
            if (btnAdd != null) btnAdd.setOnClickListener(v -> showAddDialog());

            View btnBulkDelete = findViewById(R.id.btn_bulk_delete);
            if (btnBulkDelete != null) btnBulkDelete.setOnClickListener(v -> showBulkDeleteDialog());

            View btnExport = findViewById(R.id.btn_export_history);
            if (btnExport != null) btnExport.setOnClickListener(v -> exportHistory());

            View btnClear = findViewById(R.id.btn_clear_all);
            if (btnClear != null) {
                btnClear.setOnClickListener(v -> {
                    new AlertDialog.Builder(this)
                        .setTitle("Clear All History")
                        .setMessage("Are you sure you want to delete ALL clips? This cannot be undone.")
                        .setPositiveButton("Delete All", (d, w) -> {
                            service.clear_history();
                            updateList();
                            Toast.makeText(this, "All history cleared.", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                });
            }
            
        } catch (Throwable t) {
            // Catching Throwable to include Errors and RuntimeExceptions
            String errorMsg = "Critical failure on startup.\n\nType: " + t.getClass().getName() + "\nMessage: " + t.getMessage();
            android.util.Log.e("ClipboardActivity", errorMsg, t);
            showError(errorMsg + "\n\nStack Trace:\n" + android.util.Log.getStackTraceString(t));
        }
    }

    private void showError(String message) {
        android.util.Log.e("ClipboardActivity", "Showing error UI: " + message);
        
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setBackgroundColor(0xFFFFFFFF); // White background
        ll.setPadding(48, 48, 48, 48);
        
        TextView title = new TextView(this);
        title.setText("Error Details");
        title.setTextSize(22);
        title.setTextColor(0xFFFF0000); // Red
        title.setPadding(0, 0, 0, 32);
        ll.addView(title);

        Button copyBtn = new Button(this);
        copyBtn.setText("Copy Error to Clipboard");
        copyBtn.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Error Log", message));
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
            }
        });
        ll.addView(copyBtn);

        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(14);
        tv.setTextColor(0xFF333333); // Dark Gray
        tv.setPadding(0, 32, 0, 0);
        tv.setTextIsSelectable(true);
        sv.addView(tv);
        
        ll.addView(sv);
        
        setContentView(ll);
    }

    private void updateList() {
        if (service == null) return;
        List<ClipboardHistoryService.HistoryEntry> history = service.get_history_entries();
        // Sort latest first
        java.util.Collections.sort(history, (a, b) -> b.timestamp.compareTo(a.timestamp));
        adapter = new ClipboardAdapter(history);
        listView.setAdapter(adapter);
    }

    class ClipboardAdapter extends BaseAdapter {
        List<ClipboardHistoryService.HistoryEntry> items;
        List<ClipboardHistoryService.HistoryEntry> filteredItems;
        ClipboardAdapter(List<ClipboardHistoryService.HistoryEntry> items) { 
            this.items = items; 
            this.filteredItems = new java.util.ArrayList<>(items);
        }
        void filter(String query) {
            filteredItems.clear();
            if (query.isEmpty()) {
                filteredItems.addAll(items);
            } else {
                for (ClipboardHistoryService.HistoryEntry ent : items) {
                    if (ent.content.toLowerCase().contains(query.toLowerCase()) || 
                        ent.description.toLowerCase().contains(query.toLowerCase())) {
                        filteredItems.add(ent);
                    }
                }
            }
            notifyDataSetChanged();
        }
        @Override public int getCount() { return filteredItems.size(); }
        @Override public Object getItem(int p) { return filteredItems.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View v, ViewGroup prnt) {
            if (v == null) {
                LinearLayout ll = new LinearLayout(ClipboardHistoryActivity.this);
                ll.setOrientation(LinearLayout.VERTICAL);
                ll.setPadding(40, 30, 40, 30);
                ll.setBackgroundResource(android.R.drawable.list_selector_background);
                
                TextView title = new TextView(ClipboardHistoryActivity.this);
                title.setId(android.R.id.text1);
                title.setTextSize(18);
                title.setTextColor(0xFF212121);
                title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                
                TextView sub = new TextView(ClipboardHistoryActivity.this);
                sub.setId(android.R.id.text2);
                sub.setTextSize(13);
                sub.setTextColor(0xFF757575);
                sub.setPadding(0, 8, 0, 0);
                
                ll.addView(title);
                ll.addView(sub);
                v = ll;
            }
            ClipboardHistoryService.HistoryEntry ent = filteredItems.get(p);
            ((TextView)v.findViewById(android.R.id.text1)).setText((p + 1) + ". " + ent.content);
            String info = "🕒 " + ent.timestamp + (ent.version.isEmpty() ? "" : " | 📦 v" + ent.version) + (ent.description.isEmpty() ? "" : "\n📝 " + ent.description);
            ((TextView)v.findViewById(android.R.id.text2)).setText(info);
            
            // Alternating backgrounds for advanced look
            v.setBackgroundColor(p % 2 == 0 ? 0xFFF5F5F5 : 0xFFFFFFFF);
            
            v.setOnClickListener(view -> showEditDialog(ent));
            v.setOnLongClickListener(view -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Clip", ent.content));
                    Toast.makeText(ClipboardHistoryActivity.this, "Copied to clipboard!", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            return v;
        }
    }

    private void showEditDialog(ClipboardHistoryService.HistoryEntry ent) {
        EditText edit = new EditText(this);
        edit.setText(ent.content);
        edit.setHint("Content");
        
        EditText desc = new EditText(this);
        desc.setText(ent.description);
        desc.setHint("Description");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);
        layout.addView(edit);
        layout.addView(desc);

        new AlertDialog.Builder(this)
            .setTitle("Edit Entry")
            .setView(layout)
            .setPositiveButton("Save as Ver 2", (d, w) -> {
                service.add_clip_with_metadata(edit.getText().toString(), desc.getText().toString(), "2");
                updateList();
            })
            .setNegativeButton("Delete", (d, w) -> {
                service.remove_history_entry(ent.content);
                updateList();
            })
            .setNeutralButton("Share", (d, w) -> {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, ent.content);
                sendIntent.setType("text/plain");
                startActivity(Intent.createChooser(sendIntent, null));
            })
            .show();
    }

    private void showAddDialog() {
        EditText input = new EditText(this);
        input.setHint("Content");
        EditText descInput = new EditText(this);
        descInput.setHint("Description");
        
        Spinner spinner = new Spinner(this);
        String[] formats = {".txt", ".pdf", ".js", ".java", ".html"};
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, formats);
        spinner.setAdapter(spinAdapter);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);
        layout.addView(input);
        layout.addView(descInput);
        layout.addView(spinner);

        new AlertDialog.Builder(this)
            .setTitle("Add New Note")
            .setView(layout)
            .setPositiveButton("Save", (d, w) -> {
                String text = input.getText().toString();
                String desc = descInput.getText().toString();
                String ext = spinner.getSelectedItem().toString();
                
                service.add_clip_with_metadata(text, desc, "1");
                updateList();
                
                if (ext.equals(".pdf")) {
                    exportAsPdf(text, desc);
                } else if (ext.equals(".txt")) {
                    exportAsTxt(text, desc);
                } else {
                    // Handle other extensions as generic text files for now
                    exportAsGenericText(text, desc, ext);
                }
            })
            .show();
    }

    private void exportAsGenericText(String text, String desc, String ext) {
        String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
        String content = "Time: " + timestamp + "\nDescription: " + desc + "\nContent:\n" + text;
        try {
            java.io.File file = new java.io.File(getExternalFilesDir(null), "note_" + System.currentTimeMillis() + ext);
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(content);
            writer.close();
            shareFile(file, "text/plain");
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportAsTxt(String text, String desc) {
        String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
        String content = "Time: " + timestamp + "\nDescription: " + desc + "\nContent: " + text;
        try {
            java.io.File file = new java.io.File(getExternalFilesDir(null), "note_" + System.currentTimeMillis() + ".txt");
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(content);
            writer.close();
            shareFile(file, "text/plain");
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportAsPdf(String text, String desc) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) {
            Toast.makeText(this, "PDF export requires Android 4.4+", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
            android.graphics.pdf.PdfDocument.PageInfo pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create();
            android.graphics.pdf.PdfDocument.Page page = document.startPage(pageInfo);
            android.graphics.Canvas canvas = page.getCanvas();
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setTextSize(12);
            
            int x = 50, y = 50;
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
            
            canvas.drawText("Time: " + timestamp, x, y, paint); y += 20;
            canvas.drawText("Description: " + desc, x, y, paint); y += 30;
            
            paint.setTextSize(10);
            String[] lines = text.split("\n");
            for (String line : lines) {
                canvas.drawText(line, x, y, paint);
                y += 15;
                if (y > 800) break; 
            }
            
            document.finishPage(page);
            java.io.File file = new java.io.File(getExternalFilesDir(null), "note_" + System.currentTimeMillis() + ".pdf");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            document.writeTo(fos);
            document.close();
            fos.close();
            shareFile(file, "application/pdf");
        } catch (Exception e) {
            Toast.makeText(this, "PDF Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(java.io.File file, String mimeType) {
        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, "juloo.keyboard2.provider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Download/Export Note"));
    }

    // ─────────────────────────────────────────────
    //  BULK DELETE
    // ─────────────────────────────────────────────

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
                    case 0:  confirmAndDeleteLastN(10);         break;
                    case 1:  confirmAndDeleteLastN(20);         break;
                    case 2:  confirmAndDeleteLastN(50);         break;
                    case 3:  confirmAndDeleteLastN(100);        break;
                    case 4:  showCustomNumberDialog(false);     break;
                    case 5:  showCustomNumberDialog(true);      break;
                    case 6:  showDeleteBeforeDatePicker();      break;
                    case 7:  showDateRangePicker();             break;
                    case 8:  deleteForPeriod("today");          break;
                    case 9:  deleteForPeriod("week");           break;
                    case 10: deleteForPeriod("month");          break;
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Ask the user for a custom number.
     * oldest=false → delete the N newest entries
     * oldest=true  → delete the N oldest entries
     */
    private void showCustomNumberDialog(boolean oldest) {
        EditText input = new EditText(this);
        input.setHint("e.g. 35");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setPadding(48, 24, 48, 24);

        String which = oldest ? "oldest" : "most recent";
        new AlertDialog.Builder(this)
            .setTitle("Delete N " + (oldest ? "Oldest" : "Newest") + " Entries")
            .setMessage("Enter how many " + which + " entries to delete:")
            .setView(input)
            .setPositiveButton("Delete", (d, w) -> {
                String raw = input.getText().toString().trim();
                if (raw.isEmpty()) return;
                try {
                    int n = Integer.parseInt(raw);
                    if (n <= 0) { Toast.makeText(this, "Enter a number greater than 0.", Toast.LENGTH_SHORT).show(); return; }
                    if (oldest) confirmAndDeleteOldestN(n);
                    else        confirmAndDeleteLastN(n);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid number.", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Confirm then delete the NEWEST [n] entries. */
    private void confirmAndDeleteLastN(int n) {
        int total = service.get_history_entries().size();
        int actual = Math.min(n, total);
        new AlertDialog.Builder(this)
            .setTitle("Confirm Delete Newest")
            .setMessage("Delete the " + actual + " most recent entr" + (actual == 1 ? "y" : "ies") + "?\n(" + total + " total in history)")
            .setPositiveButton("Delete", (d, w) -> {
                int removed = service.remove_last_n_entries(n);
                updateList();
                Toast.makeText(this, removed + " entr" + (removed == 1 ? "y" : "ies") + " deleted.", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Confirm then delete the OLDEST [n] entries. */
    private void confirmAndDeleteOldestN(int n) {
        int total = service.get_history_entries().size();
        int actual = Math.min(n, total);
        new AlertDialog.Builder(this)
            .setTitle("Confirm Delete Oldest")
            .setMessage("Delete the " + actual + " oldest entr" + (actual == 1 ? "y" : "ies") + "?\n(" + total + " total in history)")
            .setPositiveButton("Delete", (d, w) -> {
                int removed = service.remove_oldest_n_entries(n);
                updateList();
                Toast.makeText(this, removed + " oldest entr" + (removed == 1 ? "y" : "ies") + " deleted.", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Show a date picker; deletes all entries BEFORE the chosen date. */
    private void showDeleteBeforeDatePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dpd = new DatePickerDialog(this,
            (view, year, month, day) -> {
                Calendar cutoffCal = Calendar.getInstance();
                cutoffCal.set(year, month, day, 0, 0, 0);
                cutoffCal.set(Calendar.MILLISECOND, 0);
                Date cutoff = cutoffCal.getTime();
                String dateStr = year + "-" + String.format("%02d", month + 1) + "-" + String.format("%02d", day);
                new AlertDialog.Builder(this)
                    .setTitle("Confirm Delete Before Date")
                    .setMessage("Delete all entries before " + dateStr + "?")
                    .setPositiveButton("Delete", (d, w) -> {
                        int removed = service.remove_entries_before_date(cutoff);
                        updateList();
                        Toast.makeText(this, removed + " entr" + (removed == 1 ? "y" : "ies") + " deleted.", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dpd.setTitle("Delete entries BEFORE this date");
        dpd.show();
    }

    /** Two-step date picker: pick FROM date, then TO date, then delete the range. */
    private void showDateRangePicker() {
        final Calendar now = Calendar.getInstance();
        // Step 1: pick FROM date
        DatePickerDialog fromPicker = new DatePickerDialog(this,
            (view, fromYear, fromMonth, fromDay) -> {
                Calendar fromCal = Calendar.getInstance();
                fromCal.set(fromYear, fromMonth, fromDay, 0, 0, 0);
                fromCal.set(Calendar.MILLISECOND, 0);
                final Date fromDate = fromCal.getTime();
                final String fromStr = fromYear + "-" + String.format("%02d", fromMonth + 1) + "-" + String.format("%02d", fromDay);

                // Step 2: pick TO date
                DatePickerDialog toPicker = new DatePickerDialog(this,
                    (view2, toYear, toMonth, toDay) -> {
                        Calendar toCal = Calendar.getInstance();
                        toCal.set(toYear, toMonth, toDay, 23, 59, 59);
                        toCal.set(Calendar.MILLISECOND, 999);
                        final Date toDate = toCal.getTime();
                        final String toStr = toYear + "-" + String.format("%02d", toMonth + 1) + "-" + String.format("%02d", toDay);

                        if (fromDate.after(toDate)) {
                            Toast.makeText(this, "FROM date must be before TO date.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        new AlertDialog.Builder(this)
                            .setTitle("Confirm Delete Range")
                            .setMessage("Delete all entries from " + fromStr + " to " + toStr + " (inclusive)?")
                            .setPositiveButton("Delete", (d, w) -> {
                                int removed = service.remove_entries_in_range(fromDate, toDate);
                                updateList();
                                Toast.makeText(this, removed + " entr" + (removed == 1 ? "y" : "ies") + " deleted.", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    },
                    now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
                toPicker.setTitle("Select TO date (end of range)");
                toPicker.show();
            },
            now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        fromPicker.setTitle("Select FROM date (start of range)");
        fromPicker.show();
    }

    /** Delete entries for "today", "week", or "month". */
    private void deleteForPeriod(String period) {
        Calendar from = Calendar.getInstance();
        Calendar to   = Calendar.getInstance();

        switch (period) {
            case "today":
                from.set(Calendar.HOUR_OF_DAY, 0);
                from.set(Calendar.MINUTE, 0);
                from.set(Calendar.SECOND, 0);
                from.set(Calendar.MILLISECOND, 0);
                to.set(Calendar.HOUR_OF_DAY, 23);
                to.set(Calendar.MINUTE, 59);
                to.set(Calendar.SECOND, 59);
                to.set(Calendar.MILLISECOND, 999);
                break;
            case "week":
                from.set(Calendar.DAY_OF_WEEK, from.getFirstDayOfWeek());
                from.set(Calendar.HOUR_OF_DAY, 0);
                from.set(Calendar.MINUTE, 0);
                from.set(Calendar.SECOND, 0);
                from.set(Calendar.MILLISECOND, 0);
                to.set(Calendar.HOUR_OF_DAY, 23);
                to.set(Calendar.MINUTE, 59);
                to.set(Calendar.SECOND, 59);
                to.set(Calendar.MILLISECOND, 999);
                break;
            case "month":
                from.set(Calendar.DAY_OF_MONTH, 1);
                from.set(Calendar.HOUR_OF_DAY, 0);
                from.set(Calendar.MINUTE, 0);
                from.set(Calendar.SECOND, 0);
                from.set(Calendar.MILLISECOND, 0);
                to.set(Calendar.HOUR_OF_DAY, 23);
                to.set(Calendar.MINUTE, 59);
                to.set(Calendar.SECOND, 59);
                to.set(Calendar.MILLISECOND, 999);
                break;
        }

        String label = period.equals("today") ? "Today" : period.equals("week") ? "This Week" : "This Month";
        new AlertDialog.Builder(this)
            .setTitle("Confirm Delete — " + label)
            .setMessage("Delete all clipboard entries from " + label.toLowerCase() + "?")
            .setPositiveButton("Delete", (d, w) -> {
                int removed = service.remove_entries_in_range(from.getTime(), to.getTime());
                updateList();
                Toast.makeText(this, removed + " entr" + (removed == 1 ? "y" : "ies") + " deleted.", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ─────────────────────────────────────────────

    private void exportHistory() {
        List<ClipboardHistoryService.HistoryEntry> history = service.get_history_entries();
        if (history.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for (ClipboardHistoryService.HistoryEntry entry : history) {
            sb.append("Time: ").append(entry.timestamp).append("\n");
            sb.append("Version: ").append(entry.version).append("\n");
            sb.append("Description: ").append(entry.description).append("\n");
            sb.append("Content: ").append(entry.content).append("\n");
            sb.append("---\n");
        }

        try {
            java.io.File file = new java.io.File(getExternalFilesDir(null), "clipboard_export_" + System.currentTimeMillis() + ".txt");
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(sb.toString());
            writer.close();

            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, "juloo.keyboard2.provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Export History"));
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
