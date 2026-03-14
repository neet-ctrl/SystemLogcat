package juloo.keyboard2;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import android.preference.Preference;
import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;

public class SettingsActivity extends PreferenceActivity
{
  private static final int REQUEST_RESTORE_FILE = 1001;

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    // The preferences can't be read when in direct-boot mode. Avoid crashing
    // and don't allow changing the settings.
    // Run the config migration on this prefs as it might be different from the
    // one used by the keyboard, which have been migrated.
    try
    {
      Config.migrate(getPreferenceManager().getSharedPreferences());
    }
    catch (Exception _e) { fallbackEncrypted(); return; }
    addPreferencesFromResource(R.xml.settings);

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

        // Count clips so the title is informative
        int clipCount = 0;
        try {
            org.json.JSONObject parsed = new org.json.JSONObject(backupJson);
            if (parsed.has("clipboard")) clipCount = parsed.getJSONArray("clipboard").length();
        } catch (Exception ignored) {}

        final String json  = backupJson;
        final int    clips = clipCount;

        String[] options = {
            "📋   Copy JSON to Clipboard",
            "📤   Share as .json File",
            "🌐   Download via Browser"
        };

        new android.app.AlertDialog.Builder(this)
            .setTitle("Backup  —  " + clips + " clips")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: doBackupCopyClipboard(json, clips); break;
                    case 1: doBackupShareFile(json, clips);     break;
                    case 2: doBackupBrowser(json, clips);       break;
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
        return true;
    });

    // ── RESTORE ───────────────────────────────────────────────────────────────
    // Launches the system file picker so the user selects their backup .json
    // file directly — no size limit, works for any number of clips.
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
        boolean ok = BackupRestoreSystem.restoreBackupFromUri(this, uri);
        if (ok) {
          Toast.makeText(this, R.string.restore_success, Toast.LENGTH_LONG).show();
          recreate();
        } else {
          Toast.makeText(this, R.string.restore_failed, Toast.LENGTH_SHORT).show();
        }
      }
    }
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
          this, "juloo.keyboard2.provider", file);
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

  /**
   * Option 3 — encode the JSON as a data URI and open it in the browser.
   * Chrome / Firefox will trigger an automatic download of the .json file.
   * Falls back to share-file if the URI would be too large for a browser.
   */
  private void doBackupBrowser(String json, int clips) {
    try {
      // data URIs above ~2 MB can fail in some browsers; fall back to file share
      if (json.length() > 1_800_000) {
        Toast.makeText(this,
            "Backup is very large (" + clips + " clips). Falling back to file share.",
            Toast.LENGTH_SHORT).show();
        doBackupShareFile(json, clips);
        return;
      }

      String encoded  = android.net.Uri.encode(json);
      String dataUri  = "data:application/octet-stream;charset=utf-8," + encoded;
      Intent browser  = new Intent(Intent.ACTION_VIEW, Uri.parse(dataUri));
      browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

      if (browser.resolveActivity(getPackageManager()) != null) {
        startActivity(browser);
        Toast.makeText(this,
            "🌐 Opening in browser — tap Download when prompted.\n(" + clips + " clips)",
            Toast.LENGTH_LONG).show();
      } else {
        // No browser found — fall back to file share
        Toast.makeText(this, "No browser found. Using file share instead.", Toast.LENGTH_SHORT).show();
        doBackupShareFile(json, clips);
      }
    } catch (Exception e) {
      Toast.makeText(this, "Browser open failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

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
