package juloo.keyboard2.devconsole;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DevConsoleManager {

    private static final String TAG       = "DevConsole";
    private static final int    MAX_LOGS  = 1000;

    private static volatile DevConsoleManager sInstance;

    private final List<DevConsoleLog>         mAppLogs     = new ArrayList<>();
    private final List<DevConsoleLog>         mLogcatLogs  = new ArrayList<>();
    private final List<OnNewLogListener>      mListeners   = new CopyOnWriteArrayList<>();
    private final Handler                     mMainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean mPaused   = false;
    private volatile boolean mRunning  = false;
    private Thread           mLogcatThread;

    public interface OnNewLogListener {
        void onNewLog(DevConsoleLog log, boolean isLogcat);
        void onLogsCleared();
    }

    public static DevConsoleManager getInstance() {
        if (sInstance == null) {
            synchronized (DevConsoleManager.class) {
                if (sInstance == null) sInstance = new DevConsoleManager();
            }
        }
        return sInstance;
    }

    private DevConsoleManager() {}

    public void startLogcatReader() {
        if (mRunning) return;
        mRunning = true;
        mLogcatThread = new Thread(this::readLogcat, "DevConsole-Logcat");
        mLogcatThread.setDaemon(true);
        mLogcatThread.start();
    }

    public void stopLogcatReader() {
        mRunning = false;
        if (mLogcatThread != null) mLogcatThread.interrupt();
    }

    private void readLogcat() {
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"logcat", "-v", "time", "-T", "100"});
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while (mRunning && (line = reader.readLine()) != null) {
                if (mPaused) continue;
                if (line.trim().isEmpty() || line.startsWith("-----")) continue;
                final DevConsoleLog log = parseLogcatLine(line);
                mMainHandler.post(() -> addLogInternal(log, true));
            }
        } catch (Exception e) {
            Log.w(TAG, "Logcat reader stopped: " + e.getMessage());
        }
    }

    private DevConsoleLog parseLogcatLine(String line) {
        String level   = DevConsoleLog.LEVEL_LOG;
        String source  = "logcat";
        String message = line;

        try {
            if (line.length() > 18) {
                char levelChar = ' ';
                int slashIdx = line.indexOf('/');
                int colonIdx = line.indexOf(':', slashIdx > 0 ? slashIdx : 0);
                if (slashIdx > 0 && slashIdx < line.length() - 1) {
                    levelChar = line.charAt(slashIdx - 1);
                    int paren = line.indexOf('(', slashIdx);
                    if (paren > 0) {
                        source = line.substring(slashIdx + 1, paren).trim();
                    }
                }
                if (colonIdx > 0 && colonIdx < line.length() - 2) {
                    message = line.substring(colonIdx + 2).trim();
                }
                switch (levelChar) {
                    case 'E': level = DevConsoleLog.LEVEL_ERROR; break;
                    case 'W': level = DevConsoleLog.LEVEL_WARN;  break;
                    case 'I': level = DevConsoleLog.LEVEL_INFO;  break;
                    case 'D': level = DevConsoleLog.LEVEL_DEBUG; break;
                    case 'V': level = DevConsoleLog.LEVEL_DEBUG; break;
                    default:  level = DevConsoleLog.LEVEL_LOG;   break;
                }
            }
        } catch (Exception ignored) {}

        return new DevConsoleLog(level, message, source, null);
    }

    private void addLogInternal(DevConsoleLog log, boolean isLogcat) {
        List<DevConsoleLog> list = isLogcat ? mLogcatLogs : mAppLogs;
        list.add(0, log);
        if (list.size() > MAX_LOGS) list.remove(list.size() - 1);
        for (OnNewLogListener l : mListeners) l.onNewLog(log, isLogcat);
    }

    public void addAppLog(String level, String tag, String message) {
        if (mPaused) return;
        DevConsoleLog log = new DevConsoleLog(level, message, tag, null);
        mMainHandler.post(() -> addLogInternal(log, false));
    }

    public void d(String tag, String message) { addAppLog(DevConsoleLog.LEVEL_DEBUG, tag, message); }
    public void i(String tag, String message) { addAppLog(DevConsoleLog.LEVEL_INFO,  tag, message); }
    public void w(String tag, String message) { addAppLog(DevConsoleLog.LEVEL_WARN,  tag, message); }
    public void e(String tag, String message) { addAppLog(DevConsoleLog.LEVEL_ERROR, tag, message); }

    public synchronized List<DevConsoleLog> getAppLogs() {
        return new ArrayList<>(mAppLogs);
    }

    public synchronized List<DevConsoleLog> getLogcatLogs() {
        return new ArrayList<>(mLogcatLogs);
    }

    public void clearAppLogs() {
        mAppLogs.clear();
        for (OnNewLogListener l : mListeners) l.onLogsCleared();
    }

    public void clearLogcatLogs() {
        mLogcatLogs.clear();
        for (OnNewLogListener l : mListeners) l.onLogsCleared();
    }

    public void setPaused(boolean paused) {
        mPaused = paused;
    }

    public boolean isPaused() {
        return mPaused;
    }

    public void addListener(OnNewLogListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(OnNewLogListener listener) {
        mListeners.remove(listener);
    }
}
