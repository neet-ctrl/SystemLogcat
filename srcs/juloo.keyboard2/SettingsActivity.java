package juloo.keyboard2;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import android.preference.Preference;
import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;

public class SettingsActivity extends PreferenceActivity
{
  private static final int REQUEST_RESTORE_FILE = 1001;
  private float _dp;

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    _dp = getResources().getDisplayMetrics().density;
    try
    {
      Config.migrate(getPreferenceManager().getSharedPreferences());
    }
    catch (Exception _e) { fallbackEncrypted(); return; }
    addPreferencesFromResource(R.xml.settings);
    injectModernHeader();

    findPreference("learned_words_list").setOnPreferenceClickListener(p -> {
        Suggestions suggestions = new Suggestions(null);
        java.util.List<String> words = suggestions.getDictionary();
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.pref_learned_words_title);
        if (words == null || words.isEmpty()) {
            builder.setMessage("No learned words yet.");
        } else {
            String[] wordsArray = words.toArray(new String[0]);
            builder.setItems(wordsArray, null);
        }
        builder.setPositiveButton("OK", null);
        builder.show();
        return true;
    });

    // ── BACKUP ────────────────────────────────────────────────────────────────
    findPreference("backup_data").setOnPreferenceClickListener(p -> {
        String backupJson = BackupRestoreSystem.createBackupJson(this);
        if (backupJson == null) {
            Toast.makeText(this, "Could not create backup.", Toast.LENGTH_SHORT).show();
            return true;
        }

        int clipCount  = 0;
        int smartCount = 0;
        try {
            org.json.JSONObject parsed = new org.json.JSONObject(backupJson);
            if (parsed.has("clipboard"))   clipCount  = parsed.getJSONArray("clipboard").length();
            if (parsed.has("smart_clips")) smartCount = parsed.getJSONArray("smart_clips").length();
        } catch (Exception ignored) {}

        final String json       = backupJson;
        final int    clips      = clipCount;
        final int    smartClips = smartCount;

        // Step 1 — choose format
        new android.app.AlertDialog.Builder(this)
            .setTitle("Choose Backup Format")
            .setItems(new String[]{"📄   PDF Backup", "🗂   JSON Backup"}, (fmtDlg, fmtWhich) -> {
                if (fmtWhich == 0) {
                    // PDF path — generate and share directly
                    doBackupAsPdf(json, clips);
                } else {
                    // JSON path — show existing 3-option dialog
                    new android.app.AlertDialog.Builder(this)
                        .setTitle("JSON Backup  —  " + clips + " clips")
                        .setItems(new String[]{
                            "📋   Copy JSON to Clipboard",
                            "📤   Share as .json File",
                            "🌐   Download via Browser"
                        }, (dialog, which) -> {
                            switch (which) {
                                case 0: doBackupCopyClipboard(json, clips); break;
                                case 1: doBackupShareFile(json, clips);     break;
                                case 2: doBackupBrowser(json, clips);       break;
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
        return true;
    });

    // ── RESTORE ───────────────────────────────────────────────────────────────
    findPreference("restore_data").setOnPreferenceClickListener(p -> {
        Intent pickIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
        pickIntent.setType("*/*");
        startActivityForResult(
            Intent.createChooser(pickIntent, getString(R.string.pref_restore_title)),
            REQUEST_RESTORE_FILE);
        return true;
    });

    boolean foldableDevice = FoldStateTracker.isFoldableDevice(this);
    findPreference("margin_bottom_portrait_unfolded").setEnabled(foldableDevice);
    findPreference("margin_bottom_landscape_unfolded").setEnabled(foldableDevice);
    findPreference("horizontal_margin_portrait_unfolded").setEnabled(foldableDevice);
    findPreference("horizontal_margin_landscape_unfolded").setEnabled(foldableDevice);
    findPreference("keyboard_height_unfolded").setEnabled(foldableDevice);
    findPreference("keyboard_height_landscape_unfolded").setEnabled(foldableDevice);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_RESTORE_FILE && resultCode == RESULT_OK && data != null) {
      Uri uri = data.getData();
      if (uri != null) {
        boolean isPdf = isUriPdf(uri);
        boolean ok;
        if (isPdf) {
          ok = restoreFromPdfUri(uri);
        } else {
          ok = BackupRestoreSystem.restoreBackupFromUri(this, uri);
        }
        if (ok) {
          Toast.makeText(this, R.string.restore_success, Toast.LENGTH_LONG).show();
          recreate();
        } else {
          Toast.makeText(this, R.string.restore_failed, Toast.LENGTH_SHORT).show();
        }
      }
    }
  }

  // ── PDF DETECTION ─────────────────────────────────────────────────────────

  private boolean isUriPdf(Uri uri) {
    String mime = getContentResolver().getType(uri);
    if (mime != null && mime.equals("application/pdf")) return true;
    String path = uri.getLastPathSegment();
    return path != null && path.toLowerCase(java.util.Locale.US).endsWith(".pdf");
  }

  // ── RESTORE FROM PDF ──────────────────────────────────────────────────────

  /**
   * Read the PDF file, find the embedded JSON block appended after %%EOF,
   * and restore from it.
   */
  private boolean restoreFromPdfUri(Uri uri) {
    try {
      java.io.InputStream is = getContentResolver().openInputStream(uri);
      if (is == null) return false;
      byte[] bytes = readAllBytes(is);
      is.close();
      String raw = new String(bytes, "UTF-8");
      int start = raw.indexOf("%%KEYBOARD_BACKUP_JSON_START\n");
      int end   = raw.indexOf("\n%%KEYBOARD_BACKUP_JSON_END");
      if (start < 0 || end < 0 || end <= start) return false;
      String json = raw.substring(start + "%%KEYBOARD_BACKUP_JSON_START\n".length(), end);
      return BackupRestoreSystem.restoreBackup(this, json.trim());
    } catch (Exception e) {
      Toast.makeText(this, "PDF restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
      return false;
    }
  }

  private byte[] readAllBytes(java.io.InputStream is) throws java.io.IOException {
    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int n;
    while ((n = is.read(chunk)) != -1) buffer.write(chunk, 0, n);
    return buffer.toByteArray();
  }

  // ── BACKUP HELPERS ────────────────────────────────────────────────────────

  /** Option 1 — copy the raw JSON to clipboard. */
  private void doBackupCopyClipboard(String json, int clips) {
    try {
      android.content.ClipboardManager cm =
          (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
      if (cm != null) {
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Keyboard Backup", json));
        Toast.makeText(this,
            "✅ Backup copied to clipboard!\n(" + clips + " clips — paste it anywhere to save)",
            Toast.LENGTH_LONG).show();
      }
    } catch (Exception e) {
      Toast.makeText(this, "Copy failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  /** Option 2 — write to keyboard_backup.json and open the system share sheet. */
  private void doBackupShareFile(String json, int clips) {
    try {
      java.io.File dir = getExternalFilesDir(null);
      if (dir == null) dir = getCacheDir();
      dir.mkdirs();
      java.io.File file = new java.io.File(dir, "keyboard_backup.json");
      java.io.FileWriter w = new java.io.FileWriter(file);
      w.write(json);
      w.close();

      Uri uri = androidx.core.content.FileProvider.getUriForFile(
          this, getPackageName() + ".provider", file);
      Intent share = new Intent(Intent.ACTION_SEND);
      share.setType("application/json");
      share.putExtra(Intent.EXTRA_STREAM, uri);
      share.putExtra(Intent.EXTRA_SUBJECT, "Keyboard Backup (" + clips + " clips)");
      share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(Intent.createChooser(share, "Save / Send Backup File"));
      Toast.makeText(this, "📤 " + clips + " clips — choose where to save the file.",
          Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  /** Option 3 — encode the JSON as a data URI and open it in the browser. */
  private void doBackupBrowser(String json, int clips) {
    try {
      if (json.length() > 1_800_000) {
        Toast.makeText(this,
            "Backup is very large (" + clips + " clips). Falling back to file share.",
            Toast.LENGTH_SHORT).show();
        doBackupShareFile(json, clips);
        return;
      }
      String encoded = android.net.Uri.encode(json);
      String dataUri = "data:application/octet-stream;charset=utf-8," + encoded;
      Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(dataUri));
      browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      if (browser.resolveActivity(getPackageManager()) != null) {
        startActivity(browser);
        Toast.makeText(this,
            "🌐 Opening in browser — tap Download when prompted.\n(" + clips + " clips)",
            Toast.LENGTH_LONG).show();
      } else {
        Toast.makeText(this, "No browser found. Using file share instead.", Toast.LENGTH_SHORT).show();
        doBackupShareFile(json, clips);
      }
    } catch (Exception e) {
      Toast.makeText(this, "Browser open failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  // ── PDF BACKUP ────────────────────────────────────────────────────────────

  /**
   * Generate a formatted PDF backup and share it.
   * All backup data is rendered as human-readable content on the pages.
   * The full JSON is also appended after the PDF's %%EOF marker so the
   * file can be used for restore just like a .json file.
   */
  private void doBackupAsPdf(String json, int clips) {
    new Thread(() -> {
      try {
        org.json.JSONObject backup = new org.json.JSONObject(json);

        int clipCount     = backup.has("clipboard")    ? backup.getJSONArray("clipboard").length()   : 0;
        int settingsCount = backup.has("settings")     ? backup.getJSONObject("settings").length()   : 0;
        int wordsCount    = backup.has("learned_words")? backup.getJSONArray("learned_words").length(): 0;
        int smartCount    = backup.has("smart_clips")  ? backup.getJSONArray("smart_clips").length() : 0;

        android.graphics.pdf.PdfDocument doc = new android.graphics.pdf.PdfDocument();

        final int PW = 595, PH = 842, M = 40, CW = PW - 2 * M;
        int pageNum = 1;

        // ── paints ──────────────────────────────────────────────────────────
        android.graphics.Paint bg     = new android.graphics.Paint();
        android.graphics.Paint txtB   = new android.graphics.Paint();
        android.graphics.Paint txtN   = new android.graphics.Paint();
        android.graphics.Paint txtS   = new android.graphics.Paint();
        android.graphics.Paint divPt  = new android.graphics.Paint();

        txtB.setColor(0xFF212121);
        txtB.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        txtN.setColor(0xFF212121);
        txtN.setTypeface(android.graphics.Typeface.MONOSPACE);

        txtS.setColor(0xFF757575);

        divPt.setColor(0xFFDDDDDD);
        divPt.setStrokeWidth(1);

        // ── page/cursor helpers ─────────────────────────────────────────────
        final float[] yRef = {0};
        final android.graphics.pdf.PdfDocument.Page[] pageRef = {null};
        final android.graphics.Canvas[] cvRef = {null};
        final int[] pgNumRef = {1};

        java.lang.Runnable startPage = () -> {
          android.graphics.pdf.PdfDocument.PageInfo pi =
              new android.graphics.pdf.PdfDocument.PageInfo.Builder(PW, PH, pgNumRef[0]++).create();
          pageRef[0] = doc.startPage(pi);
          cvRef[0]   = pageRef[0].getCanvas();
          yRef[0]    = M;
        };

        java.lang.Runnable finishPage = () -> {
          if (pageRef[0] != null) { doc.finishPage(pageRef[0]); pageRef[0] = null; }
        };

        // start first page
        startPage.run();
        android.graphics.Canvas cv = cvRef[0];

        // ── TITLE HEADER ────────────────────────────────────────────────────
        bg.setColor(0xFF1A237E);
        cv.drawRect(0, 0, PW, 68, bg);

        txtB.setColor(0xFFFFFFFF);
        txtB.setTextSize(20);
        cv.drawText("Keyboard Backup", M, 30, txtB);

        txtS.setColor(0xFFBBCCFF);
        txtS.setTextSize(11);
        String dateStr = new java.text.SimpleDateFormat("dd MMM yyyy  HH:mm:ss",
            java.util.Locale.US).format(new java.util.Date());
        cv.drawText("Generated: " + dateStr, M, 50, txtS);
        cv.drawText(clips + " clips", PW - M - 60, 50, txtS);

        yRef[0] = 82;

        // ── SUMMARY BOX ─────────────────────────────────────────────────────
        bg.setColor(0xFFE8EAF6);
        cvRef[0].drawRect(M, yRef[0], PW - M, yRef[0] + 56, bg);
        txtB.setColor(0xFF1A237E);
        txtB.setTextSize(11);
        cvRef[0].drawText(
            "Clipboard: " + clipCount + " entries    |    Smart Clips: " + smartCount,
            M + 10, yRef[0] + 18, txtB);
        cvRef[0].drawText(
            "Settings: " + settingsCount + " keys    |    Learned Words: " + wordsCount,
            M + 10, yRef[0] + 38, txtB);
        yRef[0] += 68;

        // helper: ensure enough room, else new page
        // (Java lambda can't reassign outer local, so use array refs throughout)

        // ── CLIPBOARD HISTORY SECTION ───────────────────────────────────────
        if (backup.has("clipboard")) {
          org.json.JSONArray clips_arr = backup.getJSONArray("clipboard");

          // section heading
          checkNewPage(doc, pageRef, cvRef, yRef, pgNumRef, M, PH, PW, 30);
          cv = cvRef[0];
          bg.setColor(0xFF283593);
          cv.drawRect(M, yRef[0], PW - M, yRef[0] + 22, bg);
          txtB.setColor(0xFFFFFFFF);
          txtB.setTextSize(12);
          cv.drawText("Clipboard History  (" + clipCount + " entries)", M + 8, yRef[0] + 15, txtB);
          yRef[0] += 28;

          for (int i = 0; i < clips_arr.length(); i++) {
            org.json.JSONObject entry = clips_arr.getJSONObject(i);
            String content   = entry.optString("content", "");
            String timestamp = entry.optString("timestamp", "");
            String desc      = entry.optString("description", "");
            String ver       = entry.optString("version", "");

            // calculate lines needed
            int linesNeeded = (int) Math.ceil(content.length() / 70.0) + 3;
            float blockH = Math.min(linesNeeded * 13 + 20, 120);
            checkNewPage(doc, pageRef, cvRef, yRef, pgNumRef, M, PH, PW, blockH + 6);
            cv = cvRef[0];

            // card background (alternating)
            bg.setColor(i % 2 == 0 ? 0xFFF5F5F5 : 0xFFFFFFFF);
            cv.drawRect(M, yRef[0], PW - M, yRef[0] + blockH, bg);

            // entry number + meta line
            txtB.setColor(0xFF1A237E);
            txtB.setTextSize(10);
            String meta = "#" + (i + 1) + "   " + timestamp
                + (ver.isEmpty() ? "" : "  v" + ver)
                + (desc.isEmpty() ? "" : "   " + desc);
            cv.drawText(truncate(meta, 90), M + 6, yRef[0] + 13, txtB);

            // content text (wrapped)
            txtN.setColor(0xFF212121);
            txtN.setTextSize(9.5f);
            float ty = yRef[0] + 24;
            for (String line : wrapText(content, 88)) {
              if (ty > yRef[0] + blockH - 6) break;
              cv.drawText(line, M + 6, ty, txtN);
              ty += 12;
            }

            // bottom divider
            divPt.setColor(0xFFDDDDDD);
            cv.drawLine(M, yRef[0] + blockH, PW - M, yRef[0] + blockH, divPt);
            yRef[0] += blockH + 4;
          }
        }

        // ── SMART CLIPS SECTION ──────────────────────────────────────────────
        if (backup.has("smart_clips")) {
          org.json.JSONArray sc_arr = backup.getJSONArray("smart_clips");
          checkNewPage(doc, pageRef, cvRef, yRef, pgNumRef, M, PH, PW, 30);
          cv = cvRef[0];

          yRef[0] += 8;
          bg.setColor(0xFF4527A0);
          cv.drawRect(M, yRef[0], PW - M, yRef[0] + 22, bg);
          txtB.setColor(0xFFFFFFFF);
          txtB.setTextSize(12);
          cv.drawText("Smart Clips  (" + smartCount + ")", M + 8, yRef[0] + 15, txtB);
          yRef[0] += 28;

          for (int i = 0; i < sc_arr.length(); i++) {
            org.json.JSONObject sc  = sc_arr.getJSONObject(i);
            int    serial  = sc.optInt("serial", i + 1);
            String content = sc.optString("content", "");
            String desc    = sc.optString("description", "");
            String kw      = sc.optString("keyword", "");
            boolean locked = sc.optBoolean("locked", false);
            boolean hidden = sc.optBoolean("hidden", false);
            String  ts     = sc.optString("timestamp", "");

            int linesNeeded = (int) Math.ceil(content.length() / 70.0) + 3;
            float blockH = Math.min(linesNeeded * 13 + 24, 110);
            checkNewPage(doc, pageRef, cvRef, yRef, pgNumRef, M, PH, PW, blockH + 6);
            cv = cvRef[0];

            // card background (alternating)
            bg.setColor(i % 2 == 0 ? 0xFFF3E5F5 : 0xFFFFFFFF);
            cv.drawRect(M, yRef[0], PW - M, yRef[0] + blockH, bg);

            // meta line: #serial  keyword  flags  timestamp
            txtB.setColor(0xFF4527A0);
            txtB.setTextSize(10);
            String flags = (locked ? " 🔒LOCKED" : "") + (hidden ? "  HIDDEN" : "");
            String meta  = "#" + serial
                    + (kw.isEmpty()   ? "" : "  🔑 " + kw)
                    + (desc.isEmpty() ? "" : "  — " + desc)
                    + flags
                    + (ts.isEmpty()   ? "" : "  " + ts);
            cv.drawText(truncate(meta, 88), M + 6, yRef[0] + 14, txtB);

            // content (masked if locked)
            txtN.setColor(locked ? 0xFFAAAAAA : 0xFF212121);
            txtN.setTextSize(9.5f);
            String displayContent = locked ? "(content hidden — clip is locked)" : content;
            float ty = yRef[0] + 26;
            for (String line : wrapText(displayContent, 88)) {
              if (ty > yRef[0] + blockH - 6) break;
              cv.drawText(line, M + 6, ty, txtN);
              ty += 12;
            }

            divPt.setColor(0xFFDDDDDD);
            cv.drawLine(M, yRef[0] + blockH, PW - M, yRef[0] + blockH, divPt);
            yRef[0] += blockH + 4;
          }
        }

        // ── LEARNED WORDS SECTION ────────────────────────────────────────────
        if (backup.has("learned_words")) {
          org.json.JSONArray words_arr = backup.getJSONArray("learned_words");
          checkNewPage(doc, pageRef, cvRef, yRef, pgNumRef, M, PH, PW, 30);
          cv = cvRef[0];

          yRef[0] += 8;
          bg.setColor(0xFF1B5E20);
          cv.drawRect(M, yRef[0], PW - M, yRef[0] + 22, bg);
          txtB.setColor(0xFFFFFFFF);
          txtB.setTextSize(12);
          cv.drawText("Learned Words  (" + wordsCount + ")", M + 8, yRef[0] + 15, txtB);
          yRef[0] += 28;

          StringBuilder wordLine = new StringBuilder();
          int colWords = 0;
          for (int i = 0; i < words_arr.length(); i++) {
            String w = words_arr.getString(i);
            if (wordLine.length() > 0) wordLine.append(",  ");
            wordLine.append(w);
            colWords++;
            if (colWords == 6 || wordLine.length() > 80) {
              checkNewPage(doc, pageRef, cvRef, yRef, pgNumRef, M, PH, PW, 14);
              cv = cvRef[0];
              txtN.setColor(0xFF2E7D32);
              txtN.setTextSize(9.5f);
              cv.drawText(wordLine.toString(), M + 6, yRef[0] + 11, txtN);
              yRef[0] += 14;
              wordLine.setLength(0);
              colWords = 0;
            }
          }
          if (wordLine.length() > 0) {
            checkNewPage(doc, pageRef, cvRef, yRef, pgNumRef, M, PH, PW, 14);
            cv = cvRef[0];
            txtN.setColor(0xFF2E7D32);
            txtN.setTextSize(9.5f);
            cv.drawText(wordLine.toString(), M + 6, yRef[0] + 11, txtN);
            yRef[0] += 14;
          }
        }

        // ── SETTINGS SECTION ─────────────────────────────────────────────────
        if (backup.has("settings")) {
          org.json.JSONObject settings = backup.getJSONObject("settings");
          checkNewPage(doc, pageRef, cvRef, yRef, pgNumRef, M, PH, PW, 30);
          cv = cvRef[0];

          yRef[0] += 8;
          bg.setColor(0xFF4A148C);
          cv.drawRect(M, yRef[0], PW - M, yRef[0] + 22, bg);
          txtB.setColor(0xFFFFFFFF);
          txtB.setTextSize(12);
          cv.drawText("Settings  (" + settingsCount + " keys)", M + 8, yRef[0] + 15, txtB);
          yRef[0] += 28;

          java.util.Iterator<String> keys = settings.keys();
          int si = 0;
          while (keys.hasNext()) {
            String key = keys.next();
            String val = String.valueOf(settings.opt(key));
            String line = key + " = " + val;
            checkNewPage(doc, pageRef, cvRef, yRef, pgNumRef, M, PH, PW, 13);
            cv = cvRef[0];
            bg.setColor(si % 2 == 0 ? 0xFFF3E5F5 : 0xFFFFFFFF);
            cv.drawRect(M, yRef[0], PW - M, yRef[0] + 13, bg);
            txtN.setColor(0xFF4A148C);
            txtN.setTextSize(8.5f);
            cv.drawText(truncate(line, 100), M + 4, yRef[0] + 10, txtN);
            yRef[0] += 13;
            si++;
          }
        }

        // ── RESTORE FOOTER NOTE ───────────────────────────────────────────────
        checkNewPage(doc, pageRef, cvRef, yRef, pgNumRef, M, PH, PW, 36);
        cv = cvRef[0];
        yRef[0] += 12;
        bg.setColor(0xFFFFF9C4);
        cv.drawRect(M, yRef[0], PW - M, yRef[0] + 28, bg);
        txtS.setColor(0xFF5D4037);
        txtS.setTextSize(9);
        cv.drawText("This PDF contains an embedded machine-readable backup.", M + 8, yRef[0] + 12, txtS);
        cv.drawText("To restore: tap Restore in Settings and select this PDF file.", M + 8, yRef[0] + 23, txtS);
        yRef[0] += 32;

        finishPage.run();

        // ── WRITE PDF TO FILE ─────────────────────────────────────────────────
        java.io.File dir = getExternalFilesDir(null);
        if (dir == null) dir = getCacheDir();
        dir.mkdirs();
        java.io.File file = new java.io.File(dir, "keyboard_backup.pdf");
        java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
        doc.writeTo(fos);
        doc.close();

        // ── APPEND EMBEDDED JSON FOR RESTORE ──────────────────────────────────
        java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(fos, "UTF-8");
        osw.write("\n%%KEYBOARD_BACKUP_JSON_START\n");
        osw.write(json);
        osw.write("\n%%KEYBOARD_BACKUP_JSON_END\n");
        osw.flush();
        fos.close();

        // ── SHARE ─────────────────────────────────────────────────────────────
        final java.io.File finalFile = file;
        final int finalClips = clips;
        runOnUiThread(() -> {
          try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".provider", finalFile);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, "Keyboard Backup (" + finalClips + " clips)");
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Save / Send PDF Backup"));
          } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
          }
        });

      } catch (Exception e) {
        runOnUiThread(() ->
            Toast.makeText(this, "PDF backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
      }
    }).start();
  }

  // ── PDF HELPERS ───────────────────────────────────────────────────────────

  /**
   * If current y would overflow the page, finish it and start a new one.
   * All mutable state is passed as single-element arrays so lambdas can mutate them.
   */
  private void checkNewPage(
      android.graphics.pdf.PdfDocument doc,
      android.graphics.pdf.PdfDocument.Page[] pageRef,
      android.graphics.Canvas[] cvRef,
      float[] yRef,
      int[] pgNumRef,
      int margin, int pageH, int pageW, float neededH)
  {
    if (yRef[0] + neededH > pageH - margin) {
      if (pageRef[0] != null) { doc.finishPage(pageRef[0]); }
      android.graphics.pdf.PdfDocument.PageInfo pi =
          new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, pgNumRef[0]++).create();
      pageRef[0] = doc.startPage(pi);
      cvRef[0]   = pageRef[0].getCanvas();
      yRef[0]    = margin;
    }
  }

  /** Wrap text into lines of at most maxChars characters. */
  private java.util.List<String> wrapText(String text, int maxChars) {
    java.util.List<String> lines = new java.util.ArrayList<>();
    if (text == null || text.isEmpty()) return lines;
    String[] paragraphs = text.split("\n", -1);
    for (String para : paragraphs) {
      if (para.length() <= maxChars) {
        lines.add(para);
      } else {
        int pos = 0;
        while (pos < para.length()) {
          int end = Math.min(pos + maxChars, para.length());
          lines.add(para.substring(pos, end));
          pos = end;
        }
      }
    }
    return lines;
  }

  /** Truncate a string to maxLen characters with "…" if needed. */
  private String truncate(String s, int maxLen) {
    if (s == null) return "";
    return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
  }

  // ──────────────────────────────────────────────────────────────────────────

  // ── Modern header injected into the PreferenceActivity ListView ───────────

  private void injectModernHeader()
  {
    ListView lv = getListView();
    if (lv == null) return;

    // ── Outer wrapper (dark background matching modern UI) ─────────────────
    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.VERTICAL);
    header.setLayoutParams(new ListView.LayoutParams(
        ListView.LayoutParams.MATCH_PARENT,
        ListView.LayoutParams.WRAP_CONTENT));

    // ── Top header bar ─────────────────────────────────────────────────────
    LinearLayout topBar = new LinearLayout(this);
    topBar.setOrientation(LinearLayout.HORIZONTAL);
    topBar.setGravity(Gravity.CENTER_VERTICAL);
    topBar.setBackgroundColor(0xFF1A1A2E);
    topBar.setPadding(dp(12), dp(16), dp(16), dp(16));

    // Back button
    Button backBtn = new Button(this);
    backBtn.setText("←");
    backBtn.setTextSize(18);
    backBtn.setTextColor(0xFFE2E8F0);
    backBtn.setBackground(null);
    backBtn.setPadding(0, 0, dp(8), 0);
    backBtn.setMinWidth(0); backBtn.setMinHeight(0);
    backBtn.setOnClickListener(v -> finish());
    topBar.addView(backBtn);

    // Title column
    LinearLayout titleCol = new LinearLayout(this);
    titleCol.setOrientation(LinearLayout.VERTICAL);
    titleCol.setLayoutParams(new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

    TextView title = new TextView(this);
    title.setText("⚙  Keyboard Settings");
    title.setTextSize(18);
    title.setTextColor(0xFFE2E8F0);
    title.setTypeface(Typeface.DEFAULT_BOLD);
    titleCol.addView(title);

    TextView sub = new TextView(this);
    sub.setText("Layout · Typing · Style · Behaviour");
    sub.setTextSize(11);
    sub.setTextColor(0xFF94A3B8);
    LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    subLp.setMargins(0, dp(2), 0, 0);
    sub.setLayoutParams(subLp);
    titleCol.addView(sub);

    topBar.addView(titleCol);

    // Accent right badge
    TextView badge = new TextView(this);
    badge.setText("⚙");
    badge.setTextSize(22);
    badge.setTextColor(0x554F46E5);
    topBar.addView(badge);

    header.addView(topBar);

    // ── Thin accent divider ────────────────────────────────────────────────
    View accent = new View(this);
    accent.setBackgroundColor(0xFF4F46E5);
    accent.setLayoutParams(new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));
    header.addView(accent);

    // ── Spacer below header before prefs list ─────────────────────────────
    View spacer = new View(this);
    spacer.setBackgroundColor(0xFF0F1117);
    spacer.setLayoutParams(new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(6)));
    header.addView(spacer);

    lv.addHeaderView(header, null, false);

    // Style the ListView itself — dark background
    lv.setBackgroundColor(0xFF0F1117);
    lv.setDivider(new android.graphics.drawable.ColorDrawable(0xFF1E2040));
    lv.setDividerHeight(1);
    lv.setCacheColorHint(Color.TRANSPARENT);

    // Style the window background dark
    if (getWindow() != null) {
      getWindow().getDecorView().setBackgroundColor(0xFF0F1117);
    }
  }

  private int dp(int v) { return (int)(v * _dp); }

  // ──────────────────────────────────────────────────────────────────────────

  void fallbackEncrypted()
  {
    finish();
  }

  protected void onStop()
  {
    DirectBootAwarePreferences
      .copy_preferences_to_protected_storage(this,
          getPreferenceManager().getSharedPreferences());
    super.onStop();
  }
}
