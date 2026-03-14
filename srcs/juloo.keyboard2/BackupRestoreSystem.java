package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import juloo.keyboard2.ClipboardHistoryService.HistoryEntry;

public class BackupRestoreSystem {
    private static final String BACKUP_FILE_NAME = "keyboard_backup.json";

    /**
     * Build the full backup JSON string.
     * Supports unlimited clipboard entries.
     */
    public static String createBackupJson(Context context) {
        try {
            org.json.JSONObject backup = new org.json.JSONObject();

            // 1. Settings
            SharedPreferences settings = DirectBootAwarePreferences.get_shared_preferences(context);
            org.json.JSONObject settingsJson = new org.json.JSONObject();
            for (Map.Entry<String, ?> entry : settings.getAll().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof java.util.Set) {
                    org.json.JSONArray setArray = new org.json.JSONArray();
                    for (Object item : (java.util.Set<?>) value) setArray.put(item);
                    settingsJson.put(entry.getKey(), setArray);
                } else {
                    settingsJson.put(entry.getKey(), value);
                }
            }
            backup.put("settings", settingsJson);

            // 2. Clipboard History (unlimited — no cap)
            ClipboardHistoryService clipboardService = ClipboardHistoryService.get_service(context);
            if (clipboardService != null) {
                org.json.JSONArray clipboardJson = new org.json.JSONArray();
                for (HistoryEntry entry : clipboardService.get_history_entries()) {
                    org.json.JSONObject item = new org.json.JSONObject();
                    item.put("content", entry.content);
                    item.put("timestamp", entry.timestamp);
                    item.put("description", entry.description);
                    item.put("version", entry.version);
                    clipboardJson.put(item);
                }
                backup.put("clipboard", clipboardJson);
            }

            // 3. Learned Words
            java.io.File learnedFile = new java.io.File(context.getFilesDir(), "user_dictionary.txt");
            if (learnedFile.exists()) {
                org.json.JSONArray learnedJson = new org.json.JSONArray();
                java.util.Scanner s = new java.util.Scanner(learnedFile);
                while (s.hasNextLine()) learnedJson.put(s.nextLine());
                s.close();
                backup.put("learned_words", learnedJson);
            }

            return backup.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Write the backup JSON to a named file in external files dir.
     * Sharing a File URI avoids the Android Binder 1 MB limit that causes
     * TransactionTooLargeException when passing large strings via Intent.EXTRA_TEXT.
     * Returns the File on success, null on failure.
     */
    public static File createBackupFile(Context context) {
        String json = createBackupJson(context);
        if (json == null) return null;
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) dir = context.getCacheDir();
            dir.mkdirs();
            File file = new File(dir, BACKUP_FILE_NAME);
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(json);
            writer.close();
            return file;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Legacy string-based backup — kept for backward compatibility.
     * For large backups prefer createBackupFile() + share via FileProvider URI.
     */
    public static String createBackup(Context context) {
        return createBackupJson(context);
    }

    /**
     * Restore from a content URI obtained via ACTION_OPEN_DOCUMENT file picker.
     * Reads the full JSON file in one pass — handles unlimited clip counts.
     */
    public static boolean restoreBackupFromUri(Context context, Uri uri) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return false;
            java.util.Scanner scanner = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
            String json = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
            is.close();
            return restoreBackup(context, json);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Restore from a JSON string.
     * Uses batch import for clipboard entries: calls import_history_batch() which
     * writes SharedPreferences only ONCE regardless of how many clips are restored.
     * Duplicate clips (by content) are skipped automatically.
     */
    public static boolean restoreBackup(Context context, String backupData) {
        try {
            org.json.JSONObject backup = new org.json.JSONObject(backupData);

            // 1. Restore Settings
            if (backup.has("settings")) {
                org.json.JSONObject settingsJson = backup.getJSONObject("settings");
                SharedPreferences.Editor editor =
                    DirectBootAwarePreferences.get_shared_preferences(context).edit();
                java.util.Iterator<String> keys = settingsJson.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = settingsJson.get(key);
                    if (value instanceof Boolean)             editor.putBoolean(key, (Boolean) value);
                    else if (value instanceof Integer)        editor.putInt(key, (Integer) value);
                    else if (value instanceof Long)           editor.putLong(key, (Long) value);
                    else if (value instanceof Float)          editor.putFloat(key, (Float) value);
                    else if (value instanceof String)         editor.putString(key, (String) value);
                    else if (value instanceof org.json.JSONArray) {
                        org.json.JSONArray array = (org.json.JSONArray) value;
                        java.util.Set<String> set = new java.util.HashSet<>();
                        for (int i = 0; i < array.length(); i++) set.add(array.getString(i));
                        editor.putStringSet(key, set);
                    }
                }
                editor.apply();
            }

            // 2. Restore Clipboard — single batch write (O(n) instead of O(n²))
            if (backup.has("clipboard")) {
                org.json.JSONArray clipboardJson = backup.getJSONArray("clipboard");
                ClipboardHistoryService clipboardService =
                    ClipboardHistoryService.get_service(context);
                if (clipboardService != null) {
                    List<HistoryEntry> toImport = new ArrayList<>(clipboardJson.length());
                    for (int i = 0; i < clipboardJson.length(); i++) {
                        org.json.JSONObject item = clipboardJson.getJSONObject(i);
                        toImport.add(new HistoryEntry(
                            item.getString("content"),
                            item.optString("timestamp", ""),
                            item.optString("description", ""),
                            item.optString("version", "")
                        ));
                    }
                    clipboardService.import_history_batch(toImport);
                }
            }

            // 3. Restore Learned Words (append, skip duplicates)
            if (backup.has("learned_words")) {
                org.json.JSONArray learnedJson = backup.getJSONArray("learned_words");
                java.io.File learnedFile =
                    new java.io.File(context.getFilesDir(), "user_dictionary.txt");
                java.util.Set<String> existing = new java.util.HashSet<>();
                if (learnedFile.exists()) {
                    java.util.Scanner s = new java.util.Scanner(learnedFile);
                    while (s.hasNextLine()) existing.add(s.nextLine().trim());
                    s.close();
                }
                java.io.FileWriter writer = new java.io.FileWriter(learnedFile, true);
                for (int i = 0; i < learnedJson.length(); i++) {
                    String word = learnedJson.getString(i).trim();
                    if (!existing.contains(word)) {
                        writer.write(word + "\n");
                        existing.add(word);
                    }
                }
                writer.close();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
