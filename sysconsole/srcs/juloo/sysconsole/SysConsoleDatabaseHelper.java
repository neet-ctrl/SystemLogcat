package juloo.sysconsole;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class SysConsoleDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME    = "sys_console.db";
    private static final int    DB_VERSION = 1;

    private static final String TABLE_COLLECTIONS = "saved_collections";
    private static final String TABLE_LOG_ENTRIES = "saved_log_entries";

    private static SysConsoleDatabaseHelper sInstance;

    public static synchronized SysConsoleDatabaseHelper getInstance(Context ctx) {
        if (sInstance == null) {
            sInstance = new SysConsoleDatabaseHelper(ctx.getApplicationContext());
        }
        return sInstance;
    }

    private SysConsoleDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_COLLECTIONS + " ("
                + "id TEXT PRIMARY KEY, "
                + "name TEXT NOT NULL, "
                + "saved_at TEXT NOT NULL, "
                + "total_entries INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE TABLE " + TABLE_LOG_ENTRIES + " ("
                + "id INTEGER NOT NULL, "
                + "collection_id TEXT NOT NULL, "
                + "level TEXT NOT NULL, "
                + "message TEXT NOT NULL, "
                + "source TEXT NOT NULL, "
                + "timestamp TEXT NOT NULL, "
                + "metadata TEXT, "
                + "FOREIGN KEY(collection_id) REFERENCES " + TABLE_COLLECTIONS + "(id) ON DELETE CASCADE"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOG_ENTRIES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_COLLECTIONS);
        onCreate(db);
    }

    public void saveCollection(SavedLogCollection collection) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put("id", collection.id);
            cv.put("name", collection.name);
            cv.put("saved_at", collection.savedAt);
            cv.put("total_entries", collection.totalEntries);
            db.insertOrThrow(TABLE_COLLECTIONS, null, cv);
            for (SysConsoleLog log : collection.logs) {
                ContentValues lcv = new ContentValues();
                lcv.put("id", log.id);
                lcv.put("collection_id", collection.id);
                lcv.put("level", log.level);
                lcv.put("message", log.message);
                lcv.put("source", log.source);
                lcv.put("timestamp", log.timestamp);
                lcv.put("metadata", log.metadata);
                db.insertOrThrow(TABLE_LOG_ENTRIES, null, lcv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<SavedLogCollection> loadCollections() {
        List<SavedLogCollection> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_COLLECTIONS, null, null, null, null, null, "saved_at DESC");
        while (c.moveToNext()) {
            String id           = c.getString(c.getColumnIndexOrThrow("id"));
            String name         = c.getString(c.getColumnIndexOrThrow("name"));
            String savedAt      = c.getString(c.getColumnIndexOrThrow("saved_at"));
            int    totalEntries = c.getInt(c.getColumnIndexOrThrow("total_entries"));
            result.add(new SavedLogCollection(id, name, new ArrayList<>(), savedAt, totalEntries));
        }
        c.close();
        return result;
    }

    public List<SysConsoleLog> getLogsForCollection(String collectionId) {
        List<SysConsoleLog> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_LOG_ENTRIES, null,
                "collection_id=?", new String[]{collectionId},
                null, null, "id ASC");
        while (c.moveToNext()) {
            int    id        = c.getInt(c.getColumnIndexOrThrow("id"));
            String level     = c.getString(c.getColumnIndexOrThrow("level"));
            String message   = c.getString(c.getColumnIndexOrThrow("message"));
            String source    = c.getString(c.getColumnIndexOrThrow("source"));
            String timestamp = c.getString(c.getColumnIndexOrThrow("timestamp"));
            String metadata  = c.getString(c.getColumnIndexOrThrow("metadata"));
            result.add(new SysConsoleLog(id, level, message, source, timestamp, metadata));
        }
        c.close();
        return result;
    }

    public void deleteCollection(String collectionId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_LOG_ENTRIES, "collection_id=?", new String[]{collectionId});
        db.delete(TABLE_COLLECTIONS, "id=?", new String[]{collectionId});
    }

    public static class SavedLogCollection {
        public final String              id;
        public final String              name;
        public final List<SysConsoleLog> logs;
        public final String              savedAt;
        public final int                 totalEntries;

        public SavedLogCollection(String id, String name, List<SysConsoleLog> logs,
                                  String savedAt, int totalEntries) {
            this.id           = id;
            this.name         = name;
            this.logs         = logs;
            this.savedAt      = savedAt;
            this.totalEntries = totalEntries;
        }
    }
}
