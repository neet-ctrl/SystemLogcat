package juloo.keyboard2.devconsole;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import juloo.keyboard2.R;
import rikka.shizuku.Shizuku;

/**
 * System-wide floating developer console backed by Shizuku.
 *
 * Identical UI/features to DevConsoleService plus:
 *  - Shizuku-powered logcat reading (all device apps)
 *  - Enhanced per-entry metadata: PID, TID, App Name
 *  - 📦 APK filter — pick any installed app by display name to filter logs
 *  - ⊖ Collapse button — shrinks to a small 56 dp floating icon
 *  - Collapsed icon is fully draggable and snaps to screen edges
 *  - Shizuku connectivity dot in header (orange dot = Shizuku)
 */
public class SystemConsoleService extends Service {

    public static final String ACTION_SHOW = "juloo.keyboard2.sysconsole.SHOW";
    public static final String ACTION_HIDE = "juloo.keyboard2.sysconsole.HIDE";
    private static final String TAG = "SysConsole";

    // ── Window ────────────────────────────────────────────────────────────────
    private WindowManager              mWM;
    private View                       mRoot;
    private WindowManager.LayoutParams mParams;
    private int                        mSavedW, mSavedH, mSavedX, mSavedY;
    private boolean                    mIsMaximized;
    private boolean                    mIsCollapsed;

    // ── Root sub-views ────────────────────────────────────────────────────────
    private View mConsoleFullContainer;
    private View mCollapsedIconView;

    // ── Header views ──────────────────────────────────────────────────────────
    private View     mShizukuDot;
    private View     mConnectionDot;
    private TextView mBtnToggleSource;
    private TextView mTvStatusInfo;
    private TextView mTvSelectionBadge;
    private TextView mBtnPause;
    private TextView mBtnRefresh;
    private TextView mBtnErrors;
    private TextView mBtnCopy;
    private TextView mBtnSave;
    private TextView mBtnSaved;
    private TextView mBtnMaximize;
    private TextView mBtnCollapse;
    private TextView mBtnClose;
    private TextView mBtnFilter;
    private TextView mBtnApkFilter;

    // ── Selection bar ─────────────────────────────────────────────────────────
    private View   mSelectionBar;
    private Button mBtnSelectAll;
    private Button mBtnClearSelection;
    private Button mBtnCopySelected;
    private Button mBtnExitSelection;

    // ── Log area ──────────────────────────────────────────────────────────────
    private ListView mLogList;
    private View     mEmptyState;
    private TextView mBtnScrollTop;
    private TextView mBtnScrollBottom;

    // ── State ─────────────────────────────────────────────────────────────────
    private final List<DevConsoleLog> mAllLogs     = new ArrayList<>();
    private final List<DevConsoleLog> mLogs        = new ArrayList<>();
    private final Set<Integer>        mSelectedIds = new HashSet<>();
    private LogAdapter mAdapter;
    private boolean    mSelectionMode   = false;
    private boolean    mShowingSystem   = true;
    private boolean    mIsConnected     = false;
    private String     mFilterLevel     = "ALL";
    private String     mApkFilterPackage = null;
    private String     mApkFilterLabel  = "ALL";

    // Maps log entry id -> PID for APK-filter matching
    private final SparseArray<Integer> mLogPidMap = new SparseArray<>();

    // ── Shizuku / Logcat ──────────────────────────────────────────────────────
    private volatile boolean mShizukuReady   = false;
    private volatile boolean mLogcatRunning  = false;
    private volatile boolean mLogcatPaused   = false;
    private Process          mLogcatProcess;
    private Thread           mLogcatThread;

    // ── PID → App Name cache ──────────────────────────────────────────────────
    private final HashMap<Integer, String> mPidToAppName = new HashMap<>();

    // ── Core ──────────────────────────────────────────────────────────────────
    private final Handler            mHandler = new Handler(Looper.getMainLooper());
    private DevConsoleDatabaseHelper mDb;

    // ── Shizuku listeners ─────────────────────────────────────────────────────
    private final Shizuku.OnBinderReceivedListener mShizukuReceived = () -> {
        boolean granted = false;
        try { granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Exception ignored) {}
        mShizukuReady = granted;
        final boolean ok = granted;
        mHandler.post(() -> {
            updateShizukuDot(ok);
            if (ok && !mLogcatRunning) startShizukuLogcat();
        });
    };

    private final Shizuku.OnBinderDeadListener mShizukuDead = () -> {
        mShizukuReady = false;
        stopShizukuLogcat();
        mHandler.post(() -> updateShizukuDot(false));
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        mDb = DevConsoleDatabaseHelper.getInstance(this);
        Shizuku.addBinderReceivedListenerSticky(mShizukuReceived);
        Shizuku.addBinderDeadListener(mShizukuDead);
        createWindow();
        schedulePidRefresh();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_HIDE.equals(intent.getAction())) stopSelf();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Shizuku.removeBinderReceivedListener(mShizukuReceived);
        Shizuku.removeBinderDeadListener(mShizukuDead);
        stopShizukuLogcat();
        mHandler.removeCallbacksAndMessages(null);
        if (mRoot != null) { try { mWM.removeView(mRoot); } catch (Exception ignored) {} mRoot = null; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Window creation
    // ══════════════════════════════════════════════════════════════════════════

    private void createWindow() {
        mWM = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        mWM.getDefaultDisplay().getMetrics(dm);

        mRoot = LayoutInflater.from(this).inflate(R.layout.system_console_overlay, null);

        int layer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int w = (int) (dm.widthPixels  * 0.93f);
        int h = (int) (dm.heightPixels * 0.55f);
        mSavedW = w; mSavedH = h;

        mParams = new WindowManager.LayoutParams(w, h, layer,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = (dm.widthPixels - w) / 2;
        mParams.y = (int) (dm.heightPixels * 0.18f);
        mSavedX = mParams.x; mSavedY = mParams.y;

        mWM.addView(mRoot, mParams);
        bindViews();
        loadSnapshot();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Bind views
    // ══════════════════════════════════════════════════════════════════════════

    private void bindViews() {
        mConsoleFullContainer = mRoot.findViewById(R.id.console_full_container);
        mCollapsedIconView    = mRoot.findViewById(R.id.console_collapsed_icon);

        mShizukuDot       = mRoot.findViewById(R.id.shizuku_status_dot);
        mConnectionDot    = mRoot.findViewById(R.id.connection_dot);
        mBtnToggleSource  = mRoot.findViewById(R.id.btn_toggle_source);
        mTvStatusInfo     = mRoot.findViewById(R.id.tv_status_info);
        mTvSelectionBadge = mRoot.findViewById(R.id.tv_selection_badge);
        mBtnPause         = mRoot.findViewById(R.id.btn_pause);
        mBtnRefresh       = mRoot.findViewById(R.id.btn_refresh);
        mBtnErrors        = mRoot.findViewById(R.id.btn_errors);
        mBtnCopy          = mRoot.findViewById(R.id.btn_copy);
        mBtnSave          = mRoot.findViewById(R.id.btn_save);
        mBtnSaved         = mRoot.findViewById(R.id.btn_saved);
        mBtnMaximize      = mRoot.findViewById(R.id.btn_maximize);
        mBtnCollapse      = mRoot.findViewById(R.id.btn_collapse);
        mBtnClose         = mRoot.findViewById(R.id.btn_close);
        mBtnFilter        = mRoot.findViewById(R.id.btn_filter);
        mBtnApkFilter     = mRoot.findViewById(R.id.btn_apk_filter);

        mSelectionBar      = mRoot.findViewById(R.id.selection_bar);
        mBtnSelectAll      = mRoot.findViewById(R.id.btn_select_all);
        mBtnClearSelection = mRoot.findViewById(R.id.btn_clear_selection);
        mBtnCopySelected   = mRoot.findViewById(R.id.btn_copy_selected);
        mBtnExitSelection  = mRoot.findViewById(R.id.btn_exit_selection);

        mLogList         = mRoot.findViewById(R.id.console_log_list);
        mEmptyState      = mRoot.findViewById(R.id.empty_state);
        mBtnScrollTop    = mRoot.findViewById(R.id.btn_scroll_top);
        mBtnScrollBottom = mRoot.findViewById(R.id.btn_scroll_bottom);

        mAdapter = new LogAdapter();
        mLogList.setAdapter(mAdapter);

        mLogList.setOnItemClickListener((parent, view, pos, id) -> {
            DevConsoleLog log = mLogs.get(pos);
            if (mSelectionMode) toggleLogSelection(log.id);
            else copySpecificLog(log);
        });
        mLogList.setOnItemLongClickListener((parent, view, pos, id) -> {
            if (!mSelectionMode) enterSelectionMode();
            toggleLogSelection(mLogs.get(pos).id);
            return true;
        });

        mBtnPause.setOnClickListener(v -> handlePauseResume());
        mBtnRefresh.setOnClickListener(v -> handleRefresh());
        mBtnErrors.setOnClickListener(v -> showErrorDialog());
        mBtnCopy.setOnClickListener(v -> showCopyMenu());
        mBtnSave.setOnClickListener(v -> showSaveDialog());
        mBtnSaved.setOnClickListener(v -> showSavedLogsDialog());
        mBtnMaximize.setOnClickListener(v -> toggleMaximize());
        mBtnCollapse.setOnClickListener(v -> collapseToIcon());
        mBtnClose.setOnClickListener(v -> stopSelf());
        mBtnToggleSource.setOnClickListener(v -> toggleSource());
        mBtnFilter.setOnClickListener(v -> showFilterDialog());
        mBtnApkFilter.setOnClickListener(v -> showApkFilterDialog());

        mBtnSelectAll.setOnClickListener(v -> selectAllLogs());
        mBtnClearSelection.setOnClickListener(v -> clearSelection());
        mBtnCopySelected.setOnClickListener(v -> copySelectedLogs());
        mBtnExitSelection.setOnClickListener(v -> exitSelectionMode());

        mBtnScrollTop.setOnClickListener(v -> mLogList.setSelection(0));
        mBtnScrollBottom.setOnClickListener(v -> {
            if (mAdapter.getCount() > 0) mLogList.setSelection(mAdapter.getCount() - 1);
        });

        setupDrag();
        setupResize();
        updateShizukuDot(false);
        updateConnectionDot();
        updateStatusInfo();
        updateFilterButton();
        updateApkFilterButton();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Shizuku logcat reader
    // ══════════════════════════════════════════════════════════════════════════

    private void startShizukuLogcat() {
        if (mLogcatRunning) return;
        try {
            // Use Shizuku to spawn logcat as shell — reads ALL device logs
            mLogcatProcess = Shizuku.newProcess(
                    new String[]{"logcat", "-v", "threadtime", "-T", "500"}, null, null);
            mLogcatRunning = true;
            mIsConnected   = true;
            mLogcatThread  = new Thread(this::readLogcatStream, "SysConsole-Logcat");
            mLogcatThread.setDaemon(true);
            mLogcatThread.start();
            mHandler.post(() -> { updateConnectionDot(); updateStatusInfo(); });
            toast("System logcat started via Shizuku");
        } catch (Throwable t) {
            mIsConnected = false;
            Log.e(TAG, "Shizuku logcat start failed: " + t.getMessage());
            toast("Shizuku logcat failed: " + t.getMessage());
            mHandler.post(() -> { updateConnectionDot(); updateStatusInfo(); });
        }
    }

    private void stopShizukuLogcat() {
        mLogcatRunning = false;
        mIsConnected   = false;
        if (mLogcatProcess != null) {
            try { mLogcatProcess.destroy(); } catch (Exception ignored) {}
            mLogcatProcess = null;
        }
        if (mLogcatThread != null) {
            mLogcatThread.interrupt();
            mLogcatThread = null;
        }
    }

    private void readLogcatStream() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(mLogcatProcess.getInputStream()))) {
            String line;
            while (mLogcatRunning && (line = reader.readLine()) != null) {
                if (mLogcatPaused) continue;
                if (line.trim().isEmpty() || line.startsWith("-----")) continue;
                final DevConsoleLog log = parseThreadtimeLine(line);
                mHandler.post(() -> {
                    mAllLogs.add(0, log);
                    if (mAllLogs.size() > 2000) mAllLogs.remove(mAllLogs.size() - 1);
                    if (matchesLevelFilter(log) && matchesApkFilter(log)) {
                        mLogs.add(0, log);
                        if (mLogs.size() > 2000) mLogs.remove(mLogs.size() - 1);
                        refreshAdapter();
                    }
                });
            }
        } catch (Exception e) {
            Log.w(TAG, "Logcat stream ended: " + e.getMessage());
        } finally {
            mIsConnected = false;
            mHandler.post(() -> { updateConnectionDot(); updateStatusInfo(); });
        }
    }

    /**
     * Parse logcat line in -v threadtime format:
     *   MM-DD HH:MM:SS.mmm  PID  TID LEVEL TAG: message
     */
    private DevConsoleLog parseThreadtimeLine(String line) {
        String level    = DevConsoleLog.LEVEL_LOG;
        String source   = "system";
        String message  = line;
        String metadata = null;
        int pid = -1, tid = -1;

        try {
            String trimmed = line.trim();
            // Split into at most 7 tokens
            String[] p = trimmed.split("\\s+", 7);
            if (p.length >= 6) {
                pid = Integer.parseInt(p[2]);
                tid = Integer.parseInt(p[3]);
                switch (p[4]) {
                    case "E": case "F": level = DevConsoleLog.LEVEL_ERROR; break;
                    case "W":           level = DevConsoleLog.LEVEL_WARN;  break;
                    case "I":           level = DevConsoleLog.LEVEL_INFO;  break;
                    case "D": case "V": level = DevConsoleLog.LEVEL_DEBUG; break;
                    default:            level = DevConsoleLog.LEVEL_LOG;   break;
                }
                // p[5] may be "TAG:" and p[6] the message, or combined "TAG: message"
                String tagPart = p.length > 5 ? p[5] : "";
                String msgPart = p.length > 6 ? p[6] : "";
                // Rejoin and split on first ":"
                String combined = tagPart + (msgPart.isEmpty() ? "" : " " + msgPart);
                int colon = combined.indexOf(':');
                if (colon >= 0) {
                    source  = combined.substring(0, colon).trim();
                    message = combined.substring(colon + 1).trim();
                } else {
                    source  = combined.trim();
                    message = "";
                }
            }
        } catch (Exception ignored) {
            message = line;
        }

        // Build metadata — app name from PID cache + PID/TID
        StringBuilder sb = new StringBuilder();
        if (pid >= 0) {
            sb.append("PID: ").append(pid);
            if (tid >= 0) sb.append("   TID: ").append(tid);
            String appName;
            synchronized (mPidToAppName) { appName = mPidToAppName.get(pid); }
            if (appName != null) sb.append("\nApp: ").append(appName);
        }
        if (sb.length() > 0) metadata = sb.toString();

        DevConsoleLog log = new DevConsoleLog(level, message, source, metadata);
        // Store PID for APK filter matching
        if (pid >= 0) mLogPidMap.put(log.id, pid);
        return log;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PID → App Name cache (refreshed every 5 s)
    // ══════════════════════════════════════════════════════════════════════════

    private void schedulePidRefresh() {
        refreshPidMap();
    }

    private void refreshPidMap() {
        new Thread(() -> {
            try {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                HashMap<Integer, String> map = new HashMap<>();
                if (procs != null) {
                    PackageManager pm = getPackageManager();
                    for (ActivityManager.RunningAppProcessInfo proc : procs) {
                        String display = proc.processName;
                        if (proc.pkgList != null && proc.pkgList.length > 0) {
                            try {
                                ApplicationInfo ai = pm.getApplicationInfo(proc.pkgList[0], 0);
                                String label = pm.getApplicationLabel(ai).toString();
                                display = label + " (" + proc.pkgList[0] + ")";
                            } catch (Exception ignored) {}
                        }
                        map.put(proc.pid, display);
                    }
                }
                synchronized (mPidToAppName) {
                    mPidToAppName.clear();
                    mPidToAppName.putAll(map);
                }
            } catch (Exception e) {
                Log.w(TAG, "PID map refresh: " + e.getMessage());
            }
        }).start();
        mHandler.postDelayed(this::refreshPidMap, 5000);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Drag / Resize
    // ══════════════════════════════════════════════════════════════════════════

    private void setupDrag() {
        View header = mRoot.findViewById(R.id.console_header);
        header.setOnTouchListener(new View.OnTouchListener() {
            int ix, iy; float tx, ty;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ix = mParams.x; iy = mParams.y;
                        tx = e.getRawX(); ty = e.getRawY(); return true;
                    case MotionEvent.ACTION_MOVE:
                        mParams.x = ix + (int)(e.getRawX() - tx);
                        mParams.y = iy + (int)(e.getRawY() - ty);
                        mWM.updateViewLayout(mRoot, mParams); return true;
                }
                return false;
            }
        });
    }

    private void setupResize() {
        mRoot.findViewById(R.id.resize_handle).setOnTouchListener(new View.OnTouchListener() {
            int iw, ih; float tx, ty;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        iw = mParams.width; ih = mParams.height;
                        tx = e.getRawX(); ty = e.getRawY(); return true;
                    case MotionEvent.ACTION_MOVE:
                        DisplayMetrics dm = new DisplayMetrics();
                        mWM.getDefaultDisplay().getMetrics(dm);
                        mParams.width  = Math.max(320, Math.min(iw + (int)(e.getRawX() - tx), dm.widthPixels));
                        mParams.height = Math.max(200, Math.min(ih + (int)(e.getRawY() - ty), dm.heightPixels));
                        mWM.updateViewLayout(mRoot, mParams); return true;
                }
                return false;
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Collapse / Expand
    // ══════════════════════════════════════════════════════════════════════════

    private void collapseToIcon() {
        mIsCollapsed = true;
        // Save current size and position
        mSavedW = mParams.width;  mSavedH = mParams.height;
        mSavedX = mParams.x;      mSavedY = mParams.y;

        // Hide full console, show icon
        mConsoleFullContainer.setVisibility(View.GONE);
        mCollapsedIconView.setVisibility(View.VISIBLE);

        // Resize window to icon size and snap to right edge
        int iconSize = dp(56);
        DisplayMetrics dm = new DisplayMetrics();
        mWM.getDefaultDisplay().getMetrics(dm);
        mParams.width  = iconSize;
        mParams.height = iconSize;
        mParams.x = dm.widthPixels - iconSize;
        mParams.y = Math.min(mSavedY, dm.heightPixels - iconSize);
        mWM.updateViewLayout(mRoot, mParams);

        setupCollapsedIconDrag();
    }

    private void expandFromIcon() {
        mIsCollapsed = false;
        // Remove collapsed drag listener
        mRoot.setOnTouchListener(null);

        mConsoleFullContainer.setVisibility(View.VISIBLE);
        mCollapsedIconView.setVisibility(View.GONE);

        mParams.width  = mSavedW;
        mParams.height = mSavedH;
        mParams.x      = mSavedX;
        mParams.y      = mSavedY;
        mWM.updateViewLayout(mRoot, mParams);
    }

    private void setupCollapsedIconDrag() {
        mRoot.setOnTouchListener(new View.OnTouchListener() {
            int ix, iy;
            float tx, ty;
            boolean hasDragged;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ix = mParams.x; iy = mParams.y;
                        tx = e.getRawX(); ty = e.getRawY();
                        hasDragged = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int)(e.getRawX() - tx);
                        int dy = (int)(e.getRawY() - ty);
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) hasDragged = true;
                        mParams.x = ix + dx;
                        mParams.y = iy + dy;
                        mWM.updateViewLayout(mRoot, mParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!hasDragged) {
                            // Tap on icon → expand
                            expandFromIcon();
                        } else {
                            snapToEdge();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void snapToEdge() {
        DisplayMetrics dm = new DisplayMetrics();
        mWM.getDefaultDisplay().getMetrics(dm);
        int iconSize = dp(56);
        int centreX  = mParams.x + iconSize / 2;
        mParams.x = (centreX < dm.widthPixels / 2) ? 0 : dm.widthPixels - iconSize;
        mParams.y = Math.max(0, Math.min(mParams.y, dm.heightPixels - iconSize));
        mWM.updateViewLayout(mRoot, mParams);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Pause / Resume
    // ══════════════════════════════════════════════════════════════════════════

    private void handlePauseResume() {
        mLogcatPaused = !mLogcatPaused;
        mBtnPause.setText(mLogcatPaused ? "▶" : "⏸");
        mBtnPause.setTextColor(mLogcatPaused ? 0xFFEAB308 : 0xFFCBD5E1);
        updateConnectionDot();
        updateStatusInfo();
        toast(mLogcatPaused ? "Live logs paused" : "Resuming live logs");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Refresh
    // ══════════════════════════════════════════════════════════════════════════

    private void handleRefresh() {
        if (!mShizukuReady) {
            toast("Shizuku not authorized");
            return;
        }
        stopShizukuLogcat();
        mAllLogs.clear();
        mLogs.clear();
        refreshAdapter();
        startShizukuLogcat();
        toast("Refreshed — restarted logcat stream");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Source toggle (System-wide ↔ App-only)
    // ══════════════════════════════════════════════════════════════════════════

    private void toggleSource() {
        mShowingSystem = !mShowingSystem;
        if (mShowingSystem) {
            mBtnToggleSource.setText("🌐 System");
            mApkFilterPackage = null;
            mApkFilterLabel   = "ALL";
        } else {
            mBtnToggleSource.setText("⬛ App");
            mApkFilterPackage = getPackageName();
            mApkFilterLabel   = "Keyboard";
        }
        updateApkFilterButton();
        applyFilter();
        toast(mShowingSystem ? "Showing all device logs" : "Showing keyboard app logs only");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Maximize / Minimize
    // ══════════════════════════════════════════════════════════════════════════

    private void toggleMaximize() {
        DisplayMetrics dm = new DisplayMetrics();
        mWM.getDefaultDisplay().getMetrics(dm);
        if (mIsMaximized) {
            mParams.width  = mSavedW;
            mParams.height = mSavedH;
            mBtnMaximize.setText("⤢");
            mRoot.findViewById(R.id.resize_handle).setVisibility(View.VISIBLE);
        } else {
            mSavedW = mParams.width; mSavedH = mParams.height;
            mParams.width  = dm.widthPixels;
            mParams.height = dm.heightPixels;
            mParams.x = 0; mParams.y = 0;
            mBtnMaximize.setText("⤡");
            mRoot.findViewById(R.id.resize_handle).setVisibility(View.INVISIBLE);
        }
        mIsMaximized = !mIsMaximized;
        mWM.updateViewLayout(mRoot, mParams);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Data / Snapshot
    // ══════════════════════════════════════════════════════════════════════════

    private void loadSnapshot() {
        // Start fresh — real-time logs come via Shizuku
        refreshAdapter();
    }

    private void applyFilter() {
        mLogs.clear();
        for (DevConsoleLog l : mAllLogs) {
            if (matchesLevelFilter(l) && matchesApkFilter(l)) mLogs.add(l);
        }
        refreshAdapter();
        updateFilterButton();
        updateApkFilterButton();
    }

    private boolean matchesLevelFilter(DevConsoleLog l) {
        return "ALL".equals(mFilterLevel) || mFilterLevel.equalsIgnoreCase(l.level);
    }

    private boolean matchesApkFilter(DevConsoleLog l) {
        if (mApkFilterPackage == null) return true;
        // Match source tag against package name fragments
        if (l.source != null) {
            String src = l.source.toLowerCase(Locale.US);
            String pkg = mApkFilterPackage.toLowerCase(Locale.US);
            if (src.contains(pkg) || pkg.contains(src)) return true;
            String[] segs = mApkFilterPackage.split("\\.");
            if (segs.length > 0) {
                String last = segs[segs.length - 1].toLowerCase(Locale.US);
                if (last.length() > 3 && src.contains(last)) return true;
            }
        }
        // Match metadata (contains package name from PID lookup)
        if (l.metadata != null) {
            String meta = l.metadata.toLowerCase(Locale.US);
            String pkg  = mApkFilterPackage.toLowerCase(Locale.US);
            if (meta.contains(pkg)) return true;
        }
        return false;
    }

    private void addLog(DevConsoleLog log) {
        mAllLogs.add(0, log);
        if (mAllLogs.size() > 2000) mAllLogs.remove(mAllLogs.size() - 1);
        if (matchesLevelFilter(log) && matchesApkFilter(log)) {
            mLogs.add(0, log);
            if (mLogs.size() > 2000) mLogs.remove(mLogs.size() - 1);
            refreshAdapter();
        }
    }

    private void refreshAdapter() {
        if (mAdapter == null) return;
        mAdapter.notifyDataSetChanged();
        boolean empty = mLogs.isEmpty();
        mEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        mLogList.setVisibility(empty ? View.GONE : View.VISIBLE);
        updateStatusInfo();
        updateErrorBadge();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Status updates
    // ══════════════════════════════════════════════════════════════════════════

    private void updateStatusInfo() {
        if (mTvStatusInfo == null) return;
        String state;
        if (mLogcatPaused)      state = "Paused";
        else if (mIsConnected)  state = "Live";
        else if (mShizukuReady) state = "Starting";
        else                    state = "No Shizuku";
        String filterTag = "ALL".equals(mFilterLevel) ? "" : " · " + mFilterLevel;
        String apkTag    = (mApkFilterPackage != null) ? " · " + mApkFilterLabel : "";
        mTvStatusInfo.setText(mLogs.size() + " logs • " + state + filterTag + apkTag);
    }

    private void updateConnectionDot() {
        if (mConnectionDot == null) return;
        boolean active = mIsConnected && !mLogcatPaused;
        setDotColor(mConnectionDot, active ? 0xFF22C55E : 0xFFEF4444);
    }

    private void updateShizukuDot(boolean ok) {
        if (mShizukuDot == null) return;
        setDotColor(mShizukuDot, ok ? 0xFFF59E0B : 0xFF64748B);
        mShizukuReady = ok;
    }

    private void setDotColor(View dot, int color) {
        if (dot.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) dot.getBackground()).setColor(color);
        }
    }

    private void updateErrorBadge() {
        if (mBtnErrors == null) return;
        int errors = 0, warns = 0;
        for (DevConsoleLog l : mLogs) {
            if      (DevConsoleLog.LEVEL_ERROR.equals(l.level)) errors++;
            else if (DevConsoleLog.LEVEL_WARN.equals(l.level))  warns++;
        }
        int total = errors + warns;
        mBtnErrors.setText(total > 0 ? "\u26A0 " + total : "\u26A0");
        mBtnErrors.setTextColor(total > 0 ? 0xFFEF4444 : 0xFF64748B);
    }

    private void updateFilterButton() {
        if (mBtnFilter == null) return;
        if ("ALL".equals(mFilterLevel)) {
            mBtnFilter.setText("\u22a1 ALL");
            mBtnFilter.setTextColor(0xFFCBD5E1);
        } else {
            mBtnFilter.setText("\u22a1 " + mFilterLevel);
            switch (mFilterLevel.toUpperCase()) {
                case "ERROR": mBtnFilter.setTextColor(0xFFEF4444); break;
                case "WARN":  mBtnFilter.setTextColor(0xFFD97706); break;
                case "INFO":  mBtnFilter.setTextColor(0xFF60A5FA); break;
                case "DEBUG": mBtnFilter.setTextColor(0xFF94A3B8); break;
                default:      mBtnFilter.setTextColor(0xFFCBD5E1); break;
            }
        }
    }

    private void updateApkFilterButton() {
        if (mBtnApkFilter == null) return;
        if (mApkFilterPackage == null) {
            mBtnApkFilter.setText("📦 APP");
            mBtnApkFilter.setTextColor(0xFFCBD5E1);
        } else {
            String label = mApkFilterLabel;
            if (label.startsWith("[SYS] ")) label = label.substring(6);
            if (label.length() > 10) label = label.substring(0, 10) + "…";
            mBtnApkFilter.setText("📦 " + label);
            mBtnApkFilter.setTextColor(0xFF34D399);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Filter dialog (level)
    // ══════════════════════════════════════════════════════════════════════════

    private void showFilterDialog() {
        final String[] levels = {"ALL", "ERROR", "WARN", "INFO", "DEBUG", "LOG"};
        int current = 0;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i].equals(mFilterLevel)) { current = i; break; }
        }
        AlertDialog.Builder b = dialogBuilder();
        b.setTitle("Filter Logs by Level");
        b.setSingleChoiceItems(levels, current, null);
        b.setPositiveButton("Apply", (d, w) -> {
            int sel = ((AlertDialog) d).getListView().getCheckedItemPosition();
            if (sel >= 0 && sel < levels.length) {
                mFilterLevel = levels[sel];
                applyFilter();
                toast("Filter: " + mFilterLevel + " — showing " + mLogs.size() + " of " + mAllLogs.size() + " logs");
            }
        });
        b.setNegativeButton("Cancel", null);
        show(b);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // APK filter dialog
    // ══════════════════════════════════════════════════════════════════════════

    private void showApkFilterDialog() {
        new Thread(() -> {
            try {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                List<String[]> items = new ArrayList<>(); // [displayLabel, packageName]
                items.add(new String[]{"✓ All Apps (no filter)", null});
                for (ApplicationInfo app : apps) {
                    String label = pm.getApplicationLabel(app).toString();
                    boolean isSys = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    String displayName = isSys ? "[SYS] " + label : label;
                    items.add(new String[]{displayName, app.packageName});
                }
                Collections.sort(items.subList(1, items.size()),
                        (a, b) -> a[0].compareToIgnoreCase(b[0]));

                String[] names = new String[items.size()];
                for (int i = 0; i < items.size(); i++) names[i] = items.get(i)[0];

                mHandler.post(() -> {
                    AlertDialog.Builder b = dialogBuilder();
                    b.setTitle("Filter by App");
                    b.setItems(names, (dialog, which) -> {
                        if (which == 0) {
                            mApkFilterPackage = null;
                            mApkFilterLabel   = "ALL";
                        } else {
                            mApkFilterPackage = items.get(which)[1];
                            mApkFilterLabel   = items.get(which)[0];
                        }
                        applyFilter();
                        toast("App filter: " + (mApkFilterPackage == null ? "All" : mApkFilterLabel)
                                + " — " + mLogs.size() + " logs matching");
                    });
                    b.setNegativeButton("Cancel", null);
                    show(b);
                });
            } catch (Exception e) {
                mHandler.post(() -> toast("Could not load app list: " + e.getMessage()));
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Selection mode
    // ══════════════════════════════════════════════════════════════════════════

    private void enterSelectionMode() {
        mSelectionMode = true;
        mSelectedIds.clear();
        mSelectionBar.setVisibility(View.VISIBLE);
        updateSelectionBadge();
        mAdapter.notifyDataSetChanged();
    }

    private void exitSelectionMode() {
        mSelectionMode = false;
        mSelectedIds.clear();
        mSelectionBar.setVisibility(View.GONE);
        mTvSelectionBadge.setVisibility(View.GONE);
        mAdapter.notifyDataSetChanged();
    }

    private void toggleLogSelection(int logId) {
        if (mSelectedIds.contains(logId)) mSelectedIds.remove(logId);
        else mSelectedIds.add(logId);
        updateSelectionBadge();
        mAdapter.notifyDataSetChanged();
    }

    private void selectAllLogs() {
        for (DevConsoleLog l : mLogs) mSelectedIds.add(l.id);
        updateSelectionBadge();
        mAdapter.notifyDataSetChanged();
    }

    private void clearSelection() {
        mSelectedIds.clear();
        updateSelectionBadge();
        mAdapter.notifyDataSetChanged();
    }

    private void updateSelectionBadge() {
        int n = mSelectedIds.size();
        mTvSelectionBadge.setText(n + " selected");
        mTvSelectionBadge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        mBtnCopySelected.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        mBtnCopySelected.setText("Copy Selected (" + n + ")");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Copy functions
    // ══════════════════════════════════════════════════════════════════════════

    private void showCopyMenu() {
        List<String> options = new ArrayList<>();
        options.add("Copy All Logs");
        options.add("Select & Copy");
        options.add("Copy Range...");
        if (!mSelectedIds.isEmpty())
            options.add("Copy Selected (" + mSelectedIds.size() + ")");
        AlertDialog.Builder b = dialogBuilder();
        b.setTitle("Copy Options");
        b.setItems(options.toArray(new String[0]), (dialog, which) -> {
            switch (which) {
                case 0: copyAllLogs();    break;
                case 1: if (!mSelectionMode) enterSelectionMode(); break;
                case 2: showRangeDialog(); break;
                case 3: copySelectedLogs(); break;
            }
        });
        show(b);
    }

    private void copyAllLogs() {
        StringBuilder sb = new StringBuilder();
        List<DevConsoleLog> rev = new ArrayList<>(mLogs);
        Collections.reverse(rev);
        for (DevConsoleLog l : rev) sb.append(l.toLogLine()).append('\n');
        toClipboard(sb.toString());
        toast("All Logs Copied — " + mLogs.size() + " entries copied to clipboard");
    }

    private void copySelectedLogs() {
        List<DevConsoleLog> rev = new ArrayList<>(mLogs);
        Collections.reverse(rev);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (DevConsoleLog l : rev) {
            if (mSelectedIds.contains(l.id)) { sb.append(l.toLogLine()).append('\n'); count++; }
        }
        toClipboard(sb.toString());
        toast("Selected Logs Copied — " + count + " entries");
        exitSelectionMode();
    }

    private void copySpecificLog(DevConsoleLog log) {
        String full = log.toLogLine();
        if (log.metadata != null && !log.metadata.isEmpty()) {
            full += "\n[" + log.metadata + "]";
        }
        toClipboard(full);
        toast("Log Entry Copied");
    }

    private void showRangeDialog() {
        int total = mLogs.size();
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(24), dp(8), dp(24), 0);
        TextView desc = new TextView(this);
        desc.setText("Select a range of log entries to copy.");
        desc.setTextSize(12f); desc.setTextColor(0xFF6B7280);
        outer.addView(desc);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(12); row.setLayoutParams(rp);
        LinearLayout colS = new LinearLayout(this); colS.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cs = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        cs.rightMargin = dp(8); colS.setLayoutParams(cs);
        TextView lS = new TextView(this); lS.setText("Start Entry #"); lS.setTextSize(12f); lS.setTextColor(0xFF374151);
        EditText etS = new EditText(this); etS.setHint("1"); etS.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        colS.addView(lS); colS.addView(etS);
        LinearLayout colE = new LinearLayout(this); colE.setOrientation(LinearLayout.VERTICAL);
        colE.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView lE = new TextView(this); lE.setText("End Entry #"); lE.setTextSize(12f); lE.setTextColor(0xFF374151);
        EditText etE = new EditText(this); etE.setHint(String.valueOf(total)); etE.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        colE.addView(lE); colE.addView(etE);
        row.addView(colS); row.addView(colE);
        outer.addView(row);
        AlertDialog.Builder b = dialogBuilder();
        b.setTitle("Copy Log Range"); b.setView(outer);
        b.setPositiveButton("Copy Range", (d, w) -> {
            try {
                int start = Integer.parseInt(etS.getText().toString().trim());
                int end   = Integer.parseInt(etE.getText().toString().trim());
                copyRange(start, end);
            } catch (NumberFormatException ex) {
                toast("Invalid range");
            }
        });
        b.setNegativeButton("Cancel", null);
        show(b);
    }

    private void copyRange(int start, int end) {
        List<DevConsoleLog> sorted = new ArrayList<>(mLogs);
        Collections.reverse(sorted);
        start = Math.max(1, start); end = Math.min(sorted.size(), end);
        if (start > end) { toast("Invalid range: start must be ≤ end"); return; }
        StringBuilder sb = new StringBuilder();
        for (int i = start - 1; i < end; i++) sb.append(sorted.get(i).toLogLine()).append('\n');
        toClipboard(sb.toString());
        toast("Range Copied — entries " + start + "-" + end + " (" + (end - start + 1) + ")");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Save / Saved logs dialogs
    // ══════════════════════════════════════════════════════════════════════════

    private void showSaveDialog() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(24), dp(8), dp(24), 0);
        TextView lbl = new TextView(this);
        lbl.setText("Collection Name"); lbl.setTextSize(12f); lbl.setTextColor(0xFF374151);
        outer.addView(lbl);
        EditText etName = new EditText(this);
        String def = "SysLogs_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        etName.setText(def);
        outer.addView(etName);
        TextView info = new TextView(this);
        info.setText("Saves " + mLogs.size() + " log entries persistently.");
        info.setTextSize(11f); info.setTextColor(0xFF6B7280);
        outer.addView(info);
        AlertDialog.Builder b = dialogBuilder();
        b.setTitle("Save Current Logs"); b.setView(outer);
        b.setPositiveButton("Save Logs", (d, w) -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) name = def;
            final String finalName = name;
            final List<DevConsoleLog> snap = new ArrayList<>(mLogs);
            new Thread(() -> {
                String id      = UUID.randomUUID().toString();
                String savedAt = new SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.US).format(new Date());
                DevConsoleDatabaseHelper.SavedLogCollection col =
                        new DevConsoleDatabaseHelper.SavedLogCollection(id, finalName, snap, savedAt, snap.size());
                mDb.saveCollection(col);
                mHandler.post(() -> toast("Logs Saved — \"" + finalName + "\" with " + snap.size() + " entries"));
            }).start();
        });
        b.setNegativeButton("Cancel", null);
        show(b);
    }

    private void showSavedLogsDialog() {
        new Thread(() -> {
            List<DevConsoleDatabaseHelper.SavedLogCollection> cols = mDb.loadCollections();
            mHandler.post(() -> buildSavedLogsDialog(cols));
        }).start();
    }

    private void buildSavedLogsDialog(List<DevConsoleDatabaseHelper.SavedLogCollection> cols) {
        AlertDialog.Builder b = dialogBuilder();
        b.setTitle("Saved Log Collections");
        if (cols.isEmpty()) {
            b.setMessage("No saved log collections yet.");
            b.setNegativeButton("Close", null);
            show(b); return;
        }
        ScrollView sv = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(8), dp(8), dp(8), dp(8));
        sv.addView(container);
        for (DevConsoleDatabaseHelper.SavedLogCollection col : cols) {
            container.addView(buildCollectionRow(col));
        }
        b.setView(sv); b.setNegativeButton("Close", null);
        show(b);
    }

    private View buildCollectionRow(DevConsoleDatabaseHelper.SavedLogCollection col) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setBackgroundResource(R.drawable.dev_console_log_item_bg);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(8); row.setLayoutParams(rp);
        TextView tvName = new TextView(this);
        tvName.setText(col.name); tvName.setTextSize(14f); tvName.setTextColor(0xFF111827);
        tvName.setTypeface(tvName.getTypeface(), Typeface.BOLD);
        row.addView(tvName);
        TextView tvSub = new TextView(this);
        tvSub.setText(col.totalEntries + " entries • Saved " + col.savedAt);
        tvSub.setTextSize(11f); tvSub.setTextColor(0xFF6B7280);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(2); row.addView(tvSub, sp);
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(8); btns.setLayoutParams(bp);
        Button btnLoad = smallOutlineButton("Load");
        btnLoad.setOnClickListener(v -> loadSavedLogs(col));
        btns.addView(btnLoad);
        btns.addView(spacerView(dp(6)));
        Button btnExp = smallOutlineButton("⬇ Export");
        btnExp.setOnClickListener(v -> exportSavedLogs(col));
        btns.addView(btnExp);
        btns.addView(spacerView(dp(6)));
        Button btnDel = smallOutlineButton("🗑 Delete");
        btnDel.setTextColor(0xFFEF4444);
        btnDel.setOnClickListener(v -> new Thread(() -> {
            mDb.deleteCollection(col.id);
            mHandler.post(() -> {
                toast("Deleted \"" + col.name + "\"");
                showSavedLogsDialog();
            });
        }).start());
        btns.addView(btnDel);
        row.addView(btns);
        return row;
    }

    private void loadSavedLogs(DevConsoleDatabaseHelper.SavedLogCollection col) {
        new Thread(() -> {
            List<DevConsoleLog> logs = mDb.getLogsForCollection(col.id);
            mHandler.post(() -> {
                mLogcatPaused = true;
                mBtnPause.setText("▶"); mBtnPause.setTextColor(0xFFEAB308);
                mIsConnected = false; updateConnectionDot();
                mAllLogs.clear(); mAllLogs.addAll(logs);
                Collections.reverse(mAllLogs);
                applyFilter();
                toast("Loaded \"" + col.name + "\" — " + logs.size() + " entries. Tap ▶ to resume.");
            });
        }).start();
    }

    private void exportSavedLogs(DevConsoleDatabaseHelper.SavedLogCollection col) {
        new Thread(() -> {
            try {
                List<DevConsoleLog> logs = mDb.getLogsForCollection(col.id);
                if (logs.isEmpty()) { mHandler.post(() -> toast("Collection is empty")); return; }
                File pdfFile = writePdfFile(col.name, logs);
                mHandler.post(() -> sharePdfFile(pdfFile, col.name));
            } catch (Exception e) {
                mHandler.post(() -> toast("Export failed: " + e.getMessage()));
            }
        }).start();
    }

    private void sharePdfFile(File pdfFile, String collectionName) {
        try {
            String authority = getPackageName() + ".provider";
            Uri uri = FileProvider.getUriForFile(this, authority, pdfFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "System Console — " + collectionName);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            Intent chooser = Intent.createChooser(shareIntent, "Share PDF");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(chooser);
        } catch (Exception e) { toast("Share failed: " + e.getMessage()); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error dialog
    // ══════════════════════════════════════════════════════════════════════════

    private void showErrorDialog() {
        List<DevConsoleLog> errLogs = new ArrayList<>();
        int errors = 0, warns = 0;
        for (DevConsoleLog l : mLogs) {
            if      (DevConsoleLog.LEVEL_ERROR.equals(l.level)) { errors++; errLogs.add(l); }
            else if (DevConsoleLog.LEVEL_WARN.equals(l.level))  { warns++;  errLogs.add(l); }
        }
        AlertDialog.Builder b = dialogBuilder();
        b.setTitle("Errors & Warnings — E:" + errors + "  W:" + warns);
        if (errLogs.isEmpty()) {
            b.setMessage("No errors or warnings in current logs.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (DevConsoleLog l : errLogs) {
                sb.append("#").append(l.id).append("  [").append(l.level).append("]  ")
                  .append(l.timestamp).append("  ").append(l.source).append("\n")
                  .append(l.message).append("\n");
                if (l.metadata != null) sb.append("  ").append(l.metadata).append("\n");
                sb.append("\n");
            }
            final String text = sb.toString().trim();
            TextView tv = new TextView(this);
            tv.setText(text); tv.setTextSize(11f); tv.setTextColor(0xFF111827);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setPadding(dp(16), dp(12), dp(16), dp(12));
            ScrollView sv = new ScrollView(this); sv.addView(tv);
            b.setView(sv);
            b.setNeutralButton("Copy All", (d, w) -> {
                toClipboard(text);
                toast("Copied " + errLogs.size() + " error/warn entries");
            });
        }
        b.setNegativeButton("Close", null);
        show(b);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PDF export
    // ══════════════════════════════════════════════════════════════════════════

    private File writePdfFile(String title, List<DevConsoleLog> logs) throws IOException {
        final int PAGE_W = 842, PAGE_H = 1190, MARGIN = 28, CONTENT_W = PAGE_W - MARGIN * 2;
        final float CARD_RADIUS = 8f, BADGE_RADIUS = 5f, BADGE_PAD_H = 8f;
        final float CARD_PAD = 10f, CARD_GAP = 7f;

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE); borderPaint.setStrokeWidth(1f);
        Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        numPaint.setTypeface(Typeface.MONOSPACE); numPaint.setTextSize(11f);
        numPaint.setColor(Color.parseColor("#6B7280"));
        Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        badgeTextPaint.setTextSize(10f); badgeTextPaint.setColor(0xFFFFFFFF);
        Paint srcTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        srcTextPaint.setTypeface(Typeface.MONOSPACE); srcTextPaint.setTextSize(10f);
        srcTextPaint.setColor(Color.parseColor("#374151"));
        Paint tsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tsPaint.setTypeface(Typeface.MONOSPACE); tsPaint.setTextSize(10f);
        tsPaint.setColor(Color.parseColor("#9CA3AF"));
        Paint msgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        msgPaint.setTypeface(Typeface.MONOSPACE); msgPaint.setTextSize(11.5f);
        Paint metaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        metaPaint.setTypeface(Typeface.MONOSPACE); metaPaint.setTextSize(9.5f);
        metaPaint.setColor(Color.parseColor("#6B7280"));
        Paint headerBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerBgPaint.setColor(Color.parseColor("#0F172A"));
        Paint headerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        headerTextPaint.setTextSize(13f); headerTextPaint.setColor(0xFFFFFFFF);
        Paint headerSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerSubPaint.setTypeface(Typeface.MONOSPACE); headerSubPaint.setTextSize(10f);
        headerSubPaint.setColor(Color.parseColor("#94A3B8"));

        PdfDocument doc = new PdfDocument();
        int pageNum = 0; PdfDocument.Page page = null; Canvas canvas = null; float y = 0;
        String dateStr = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(new Date());

        for (int i = 0; i < logs.size(); i++) {
            DevConsoleLog log = logs.get(i);
            String lvl = log.level != null ? log.level.toUpperCase(Locale.US) : "LOG";
            int cardBg, cardBorder, badgeColor, msgColor;
            switch (lvl) {
                case "ERROR": cardBg=Color.parseColor("#FFF5F5"); cardBorder=Color.parseColor("#FECACA"); badgeColor=Color.parseColor("#EF4444"); msgColor=Color.parseColor("#7F1D1D"); break;
                case "WARN":  cardBg=Color.parseColor("#FEFCE8"); cardBorder=Color.parseColor("#FEF08A"); badgeColor=Color.parseColor("#EAB308"); msgColor=Color.parseColor("#78350F"); break;
                case "INFO":  cardBg=Color.parseColor("#EFF6FF"); cardBorder=Color.parseColor("#BFDBFE"); badgeColor=Color.parseColor("#3B82F6"); msgColor=Color.parseColor("#1E3A5F"); break;
                default:      cardBg=Color.parseColor("#F9FAFB"); cardBorder=Color.parseColor("#E5E7EB"); badgeColor=Color.parseColor("#6B7280"); msgColor=Color.parseColor("#374151"); break;
            }
            msgPaint.setColor(msgColor);
            String numText = "#" + (i + 1);
            String srcText = log.source != null ? log.source : "";
            String tsText  = log.timestamp != null ? log.timestamp : "";
            String msgText = log.message  != null ? log.message  : "";
            boolean hasMeta = log.metadata != null && !log.metadata.isEmpty();
            List<String> msgLines  = wrapText(msgText, CONTENT_W - CARD_PAD * 2, msgPaint);
            List<String> metaLines = hasMeta ? wrapText(log.metadata, CONTENT_W - CARD_PAD * 2 - 6f, metaPaint) : null;
            float badgeW = badgeTextPaint.measureText(lvl) + BADGE_PAD_H * 2;
            float srcW   = srcTextPaint.measureText(srcText) + BADGE_PAD_H * 2;
            float rowH   = 14f + 3f * 2;
            float msgH   = msgLines.size() * 14f;
            float metaH  = hasMeta ? (metaLines.size() * 12f + CARD_PAD + 6f) : 0f;
            float cardH  = CARD_PAD + rowH + CARD_PAD / 2f + msgH + metaH + CARD_PAD;
            float headerH = 48f;
            if (page == null || y + cardH > PAGE_H - MARGIN) {
                if (page != null) doc.finishPage(page);
                pageNum++;
                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create();
                page = doc.startPage(info);
                canvas = page.getCanvas();
                canvas.drawRect(0, 0, PAGE_W, headerH, headerBgPaint);
                canvas.drawText("● System Console — " + title, MARGIN, 18f, headerTextPaint);
                canvas.drawText(dateStr + "  ·  " + logs.size() + " entries  ·  page " + pageNum, MARGIN, 36f, headerSubPaint);
                y = headerH + MARGIN / 2f;
            }
            float cx = MARGIN, cy = y;
            cardPaint.setColor(cardBg); borderPaint.setColor(cardBorder);
            RectF cardRect = new RectF(cx, cy, cx + CONTENT_W, cy + cardH);
            canvas.drawRoundRect(cardRect, CARD_RADIUS, CARD_RADIUS, cardPaint);
            canvas.drawRoundRect(cardRect, CARD_RADIUS, CARD_RADIUS, borderPaint);
            float rx = cx + CARD_PAD;
            float ry = cy + CARD_PAD + rowH * 0.75f;
            canvas.drawText(numText, rx, ry, numPaint);
            rx += numPaint.measureText(numText) + 6f;
            badgePaint.setColor(badgeColor);
            float bx1 = rx, by1 = cy + CARD_PAD, bx2 = rx + badgeW, by2 = by1 + rowH;
            canvas.drawRoundRect(new RectF(bx1, by1, bx2, by2), BADGE_RADIUS, BADGE_RADIUS, badgePaint);
            canvas.drawText(lvl, bx1 + BADGE_PAD_H, ry, badgeTextPaint);
            rx = bx2 + 6f;
            badgePaint.setColor(Color.parseColor("#E5E7EB"));
            float sx1 = rx, sy1 = by1, sx2 = rx + srcW, sy2 = by2;
            canvas.drawRoundRect(new RectF(sx1, sy1, sx2, sy2), BADGE_RADIUS, BADGE_RADIUS, badgePaint);
            canvas.drawText(srcText, sx1 + BADGE_PAD_H, ry, srcTextPaint);
            rx = sx2 + 6f;
            canvas.drawText(tsText, rx, ry, tsPaint);
            float my = cy + CARD_PAD + rowH + CARD_PAD / 2f;
            for (String line : msgLines) { canvas.drawText(line, cx + CARD_PAD, my + 11f, msgPaint); my += 14f; }
            if (hasMeta) {
                Paint metaBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                metaBgPaint.setColor(Color.parseColor("#F3F4F6"));
                float mx1 = cx + CARD_PAD, my1 = my + 4f;
                float mx2 = cx + CONTENT_W - CARD_PAD, my2 = my1 + metaLines.size() * 12f + 6f;
                canvas.drawRoundRect(new RectF(mx1, my1, mx2, my2), 4f, 4f, metaBgPaint);
                float ty = my1 + 11f;
                for (String l : metaLines) { canvas.drawText(l, mx1 + 6f, ty, metaPaint); ty += 12f; }
            }
            y = cy + cardH + CARD_GAP;
        }
        if (page != null) doc.finishPage(page);
        String fname = "sysconsole_" + title.replaceAll("[^a-zA-Z0-9]", "_")
                + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf";
        File cacheDir = new File(getCacheDir(), "DevConsole");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        File f = new File(cacheDir, fname);
        FileOutputStream fos = new FileOutputStream(f);
        doc.writeTo(fos); fos.close(); doc.close();
        return f;
    }

    private List<String> wrapText(String text, float maxWidth, Paint paint) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) { result.add(""); return result; }
        String[] paragraphs = text.split("\n", -1);
        for (String para : paragraphs) {
            if (para.isEmpty()) { result.add(""); continue; }
            int start = 0;
            while (start < para.length()) {
                int count = paint.breakText(para, start, para.length(), true, maxWidth, null);
                if (count <= 0) count = 1;
                result.add(para.substring(start, start + count));
                start += count;
            }
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Log list adapter
    // ══════════════════════════════════════════════════════════════════════════

    private class LogAdapter extends BaseAdapter {
        @Override public int     getCount()         { return mLogs.size(); }
        @Override public Object  getItem(int pos)   { return mLogs.get(pos); }
        @Override public long    getItemId(int pos) { return mLogs.get(pos).id; }
        @Override public boolean hasStableIds()     { return true; }

        @Override
        public View getView(int pos, View cv, android.view.ViewGroup parent) {
            VH vh;
            if (cv == null) {
                cv  = LayoutInflater.from(SystemConsoleService.this)
                        .inflate(R.layout.dev_console_log_item, parent, false);
                vh              = new VH();
                vh.number       = cv.findViewById(R.id.log_number);
                vh.levelBadge   = cv.findViewById(R.id.log_level_badge);
                vh.sourceBadge  = cv.findViewById(R.id.log_source_badge);
                vh.timestamp    = cv.findViewById(R.id.log_timestamp);
                vh.selIndicator = cv.findViewById(R.id.log_selected_indicator);
                vh.copySingle   = cv.findViewById(R.id.btn_copy_single);
                vh.message      = cv.findViewById(R.id.log_message);
                vh.metadata     = cv.findViewById(R.id.log_metadata);
                cv.setTag(vh);
            } else {
                vh = (VH) cv.getTag();
            }
            DevConsoleLog log = mLogs.get(pos);
            boolean selected = mSelectedIds.contains(log.id);
            vh.number.setText("#" + (mLogs.size() - pos));
            styleLevelBadge(vh.levelBadge, log.level);
            vh.sourceBadge.setText(log.source);
            vh.timestamp.setText(log.timestamp);
            vh.message.setText(log.message);
            styleMessageColor(vh.message, log.level);
            if (log.metadata != null && !log.metadata.isEmpty()) {
                vh.metadata.setVisibility(View.VISIBLE);
                vh.metadata.setText(log.metadata);
            } else {
                vh.metadata.setVisibility(View.GONE);
            }
            setItemBg(cv, log.level, selected);
            if (mSelectionMode) {
                vh.selIndicator.setVisibility(View.VISIBLE);
                vh.selIndicator.setText(selected ? "\u2713" : "");
                vh.selIndicator.setTextColor(0xFFFFFFFF);
                vh.selIndicator.setBackgroundResource(selected
                        ? R.drawable.dev_console_check_checked
                        : R.drawable.dev_console_check_unchecked);
            } else {
                vh.selIndicator.setVisibility(View.GONE);
            }
            vh.copySingle.setVisibility(mSelectionMode ? View.GONE : View.VISIBLE);
            vh.copySingle.setOnClickListener(v -> copySpecificLog(log));
            return cv;
        }

        private void styleLevelBadge(TextView tv, String level) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(4));
            switch (level.toUpperCase()) {
                case "ERROR": bg.setColor(0xFFEF4444); break;
                case "WARN":  bg.setColor(0xFFEAB308); break;
                case "INFO":  bg.setColor(0xFF3B82F6); break;
                default:      bg.setColor(0xFF6B7280); break;
            }
            tv.setBackground(bg); tv.setText(level.toUpperCase()); tv.setTextColor(0xFFFFFFFF);
        }

        private void styleMessageColor(TextView tv, String level) {
            switch (level.toUpperCase()) {
                case "ERROR": tv.setTextColor(0xFFDC2626); break;
                case "WARN":  tv.setTextColor(0xFFCA8A04); break;
                case "INFO":  tv.setTextColor(0xFF2563EB); break;
                case "DEBUG": tv.setTextColor(0xFF4B5563); break;
                default:      tv.setTextColor(0xFF374151); break;
            }
        }

        private void setItemBg(View v, String level, boolean selected) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(6));
            if (selected) {
                bg.setColor(0xFFEFF6FF); bg.setStroke(dp(2), 0xFF3B82F6);
            } else {
                switch (level.toUpperCase()) {
                    case "ERROR": bg.setColor(0xFFFFF5F5); bg.setStroke(dp(1), 0xFFFECACA); break;
                    case "WARN":  bg.setColor(0xFFFEFCE8); bg.setStroke(dp(1), 0xFFFEF08A); break;
                    case "INFO":  bg.setColor(0xFFEFF6FF); bg.setStroke(dp(1), 0xFFBFDBFE); break;
                    case "DEBUG": bg.setColor(0xFFF9FAFB); bg.setStroke(dp(1), 0xFFE5E7EB); break;
                    default:      bg.setColor(0xFFFFFFFF); bg.setStroke(dp(1), 0xFFE5E7EB); break;
                }
            }
            v.setBackground(bg);
        }
    }

    private static class VH {
        TextView number, levelBadge, sourceBadge, timestamp, selIndicator, copySingle;
        TextView message, metadata;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private AlertDialog.Builder dialogBuilder() {
        return new AlertDialog.Builder(
                new android.view.ContextThemeWrapper(this,
                        android.R.style.Theme_Material_Light_Dialog_Alert));
    }

    private void show(AlertDialog.Builder b) {
        AlertDialog d = b.create();
        if (d.getWindow() != null) {
            d.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        }
        d.show();
    }

    private Button smallOutlineButton(String label) {
        Button btn = new Button(this);
        btn.setText(label); btn.setTextSize(11f); btn.setTextColor(0xFF374151);
        btn.setBackgroundResource(R.drawable.dev_console_outline_btn);
        btn.setPadding(dp(10), 0, dp(10), 0);
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)));
        return btn;
    }

    private View spacerView(int widthPx) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(widthPx, 1));
        return v;
    }

    private void toClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("SysConsole", text));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
