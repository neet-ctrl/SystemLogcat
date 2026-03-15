package juloo.sysconsole;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import androidx.core.app.NotificationCompat;
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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
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
import java.lang.reflect.Method;
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

import rikka.shizuku.Shizuku;

public class SysConsoleService extends Service {

    public static final String ACTION_SHOW = "juloo.sysconsole.SHOW";
    public static final String ACTION_HIDE = "juloo.sysconsole.HIDE";
    private static final String TAG = "SysConsole";

    // ── Window ────────────────────────────────────────────────────────────────
    private WindowManager              mWM;
    private View                       mRoot;
    private WindowManager.LayoutParams mParams;
    private int                        mSavedW, mSavedH, mSavedX, mSavedY;
    private boolean                    mIsMaximized;
    private boolean                    mIsCollapsed;

    // ── Sub-views ─────────────────────────────────────────────────────────────
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
    private final List<SysConsoleLog> mAllLogs     = new ArrayList<>();
    private final List<SysConsoleLog> mLogs        = new ArrayList<>();
    private final Set<Integer>        mSelectedIds = new HashSet<>();
    private LogAdapter mAdapter;
    private boolean    mSelectionMode    = false;
    private boolean    mShowingSystem    = true;
    private boolean    mIsConnected      = false;
    private String     mFilterLevel      = "ALL";
    private String     mApkFilterPackage = null;
    private String     mApkFilterLabel   = "ALL";

    private final SparseArray<Integer> mLogPidMap = new SparseArray<>();

    // ── Shizuku / Logcat ──────────────────────────────────────────────────────
    private volatile boolean mShizukuReady  = false;
    private volatile boolean mLogcatRunning = false;
    private volatile boolean mLogcatPaused  = false;
    private Process          mLogcatProcess;
    private Thread           mLogcatThread;

    // ── PID cache ─────────────────────────────────────────────────────────────
    private final HashMap<Integer, String> mPidToAppName = new HashMap<>();

    // ── Core ──────────────────────────────────────────────────────────────────
    private final Handler               mHandler = new Handler(Looper.getMainLooper());
    private SysConsoleDatabaseHelper    mDb;

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
        mDb = SysConsoleDatabaseHelper.getInstance(this);
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
    // Window
    // ══════════════════════════════════════════════════════════════════════════

    private void createWindow() {
        mWM = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        mWM.getDefaultDisplay().getMetrics(dm);

        mRoot = LayoutInflater.from(this).inflate(R.layout.console_overlay, null);

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
    }

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

        mLogList.setOnItemClickListener((p, v, pos, id) -> {
            SysConsoleLog log = mLogs.get(pos);
            if (mSelectionMode) toggleLogSelection(log.id);
            else copySpecificLog(log);
        });
        mLogList.setOnItemLongClickListener((p, v, pos, id) -> {
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
    // Shizuku logcat
    // ══════════════════════════════════════════════════════════════════════════

    private Process shizukuNewProcess(String[] cmd, String[] env, String dir) throws Throwable {
        Method m = Shizuku.class.getDeclaredMethod("newProcess",
                String[].class, String[].class, String.class);
        m.setAccessible(true);
        return (Process) m.invoke(null, cmd, env, dir);
    }

    private void startShizukuLogcat() {
        if (mLogcatRunning) return;
        try {
            mLogcatProcess = shizukuNewProcess(
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
                final SysConsoleLog log = parseThreadtimeLine(line);
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

    private SysConsoleLog parseThreadtimeLine(String line) {
        String level   = SysConsoleLog.LEVEL_LOG;
        String source  = "system";
        String message = line;
        int pid = -1, tid = -1;

        try {
            String[] p = line.trim().split("\\s+", 7);
            if (p.length >= 6) {
                pid = Integer.parseInt(p[2]);
                tid = Integer.parseInt(p[3]);
                switch (p[4]) {
                    case "E": case "F": level = SysConsoleLog.LEVEL_ERROR; break;
                    case "W":           level = SysConsoleLog.LEVEL_WARN;  break;
                    case "I":           level = SysConsoleLog.LEVEL_INFO;  break;
                    case "D": case "V": level = SysConsoleLog.LEVEL_DEBUG; break;
                    default:            level = SysConsoleLog.LEVEL_LOG;   break;
                }
                String combined = p[5] + (p.length > 6 ? " " + p[6] : "");
                int colon = combined.indexOf(':');
                if (colon >= 0) {
                    source  = combined.substring(0, colon).trim();
                    message = combined.substring(colon + 1).trim();
                } else {
                    source = combined.trim(); message = "";
                }
            }
        } catch (Exception ignored) { message = line; }

        StringBuilder sb = new StringBuilder();
        if (pid >= 0) {
            sb.append("PID: ").append(pid);
            if (tid >= 0) sb.append("   TID: ").append(tid);
            String appName;
            synchronized (mPidToAppName) { appName = mPidToAppName.get(pid); }
            if (appName != null) sb.append("\nApp: ").append(appName);
        }
        String metadata = sb.length() > 0 ? sb.toString() : null;
        SysConsoleLog log = new SysConsoleLog(level, message, source, metadata);
        if (pid >= 0) mLogPidMap.put(log.id, pid);
        return log;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PID cache
    // ══════════════════════════════════════════════════════════════════════════

    private void schedulePidRefresh() { refreshPidMap(); }

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
                                display = pm.getApplicationLabel(ai) + " (" + proc.pkgList[0] + ")";
                            } catch (Exception ignored) {}
                        }
                        map.put(proc.pid, display);
                    }
                }
                synchronized (mPidToAppName) { mPidToAppName.clear(); mPidToAppName.putAll(map); }
            } catch (Exception e) { Log.w(TAG, "PID refresh: " + e.getMessage()); }
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
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: ix=mParams.x; iy=mParams.y; tx=e.getRawX(); ty=e.getRawY(); return true;
                    case MotionEvent.ACTION_MOVE: mParams.x=ix+(int)(e.getRawX()-tx); mParams.y=iy+(int)(e.getRawY()-ty); mWM.updateViewLayout(mRoot,mParams); return true;
                } return false;
            }
        });
    }

    private void setupResize() {
        mRoot.findViewById(R.id.resize_handle).setOnTouchListener(new View.OnTouchListener() {
            int iw, ih; float tx, ty;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: iw=mParams.width; ih=mParams.height; tx=e.getRawX(); ty=e.getRawY(); return true;
                    case MotionEvent.ACTION_MOVE:
                        DisplayMetrics dm=new DisplayMetrics(); mWM.getDefaultDisplay().getMetrics(dm);
                        mParams.width=Math.max(320,Math.min(iw+(int)(e.getRawX()-tx),dm.widthPixels));
                        mParams.height=Math.max(200,Math.min(ih+(int)(e.getRawY()-ty),dm.heightPixels));
                        mWM.updateViewLayout(mRoot,mParams); return true;
                } return false;
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Collapse / Expand
    // ══════════════════════════════════════════════════════════════════════════

    private void collapseToIcon() {
        mIsCollapsed = true;
        mSavedW = mParams.width; mSavedH = mParams.height;
        mSavedX = mParams.x;    mSavedY = mParams.y;
        mConsoleFullContainer.setVisibility(View.GONE);
        mCollapsedIconView.setVisibility(View.VISIBLE);
        int iconSize = dp(56);
        DisplayMetrics dm = new DisplayMetrics(); mWM.getDefaultDisplay().getMetrics(dm);
        mParams.width = iconSize; mParams.height = iconSize;
        mParams.x = dm.widthPixels - iconSize;
        mParams.y = Math.min(mSavedY, dm.heightPixels - iconSize);
        mWM.updateViewLayout(mRoot, mParams);
        setupCollapsedIconDrag();
    }

    private void expandFromIcon() {
        mIsCollapsed = false;
        mRoot.setOnTouchListener(null);
        mConsoleFullContainer.setVisibility(View.VISIBLE);
        mCollapsedIconView.setVisibility(View.GONE);
        mParams.width = mSavedW; mParams.height = mSavedH;
        mParams.x = mSavedX;    mParams.y = mSavedY;
        mWM.updateViewLayout(mRoot, mParams);
    }

    private void setupCollapsedIconDrag() {
        mRoot.setOnTouchListener(new View.OnTouchListener() {
            int ix, iy; float tx, ty; boolean hasDragged;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: ix=mParams.x; iy=mParams.y; tx=e.getRawX(); ty=e.getRawY(); hasDragged=false; return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx=(int)(e.getRawX()-tx), dy=(int)(e.getRawY()-ty);
                        if (Math.abs(dx)>8||Math.abs(dy)>8) hasDragged=true;
                        mParams.x=ix+dx; mParams.y=iy+dy; mWM.updateViewLayout(mRoot,mParams); return true;
                    case MotionEvent.ACTION_UP:
                        if (!hasDragged) expandFromIcon(); else snapToEdge(); return true;
                } return false;
            }
        });
    }

    private void snapToEdge() {
        DisplayMetrics dm = new DisplayMetrics(); mWM.getDefaultDisplay().getMetrics(dm);
        int iconSize = dp(56);
        mParams.x = (mParams.x + iconSize/2 < dm.widthPixels/2) ? 0 : dm.widthPixels - iconSize;
        mParams.y = Math.max(0, Math.min(mParams.y, dm.heightPixels - iconSize));
        mWM.updateViewLayout(mRoot, mParams);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Pause / Refresh / Toggle
    // ══════════════════════════════════════════════════════════════════════════

    private void handlePauseResume() {
        mLogcatPaused = !mLogcatPaused;
        mBtnPause.setText(mLogcatPaused ? "▶" : "⏸");
        mBtnPause.setTextColor(mLogcatPaused ? 0xFFEAB308 : 0xFFCBD5E1);
        updateConnectionDot(); updateStatusInfo();
        toast(mLogcatPaused ? "Live logs paused" : "Resuming live logs");
    }

    private void handleRefresh() {
        if (!mShizukuReady) { toast("Shizuku not authorized"); return; }
        stopShizukuLogcat();
        mAllLogs.clear(); mLogs.clear(); refreshAdapter();
        startShizukuLogcat();
        toast("Refreshed — restarted logcat");
    }

    private void toggleSource() {
        mShowingSystem = !mShowingSystem;
        if (mShowingSystem) {
            mBtnToggleSource.setText("🌐 System");
            mApkFilterPackage = null; mApkFilterLabel = "ALL";
        } else {
            mBtnToggleSource.setText("⬛ App");
            mApkFilterPackage = getPackageName(); mApkFilterLabel = "SysConsole";
        }
        updateApkFilterButton(); applyFilter();
    }

    private void toggleMaximize() {
        DisplayMetrics dm = new DisplayMetrics(); mWM.getDefaultDisplay().getMetrics(dm);
        if (mIsMaximized) {
            mParams.width = mSavedW; mParams.height = mSavedH;
            mBtnMaximize.setText("⤢");
            mRoot.findViewById(R.id.resize_handle).setVisibility(View.VISIBLE);
        } else {
            mSavedW = mParams.width; mSavedH = mParams.height;
            mParams.width = dm.widthPixels; mParams.height = dm.heightPixels;
            mParams.x = 0; mParams.y = 0;
            mBtnMaximize.setText("⤡");
            mRoot.findViewById(R.id.resize_handle).setVisibility(View.INVISIBLE);
        }
        mIsMaximized = !mIsMaximized;
        mWM.updateViewLayout(mRoot, mParams);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Filters
    // ══════════════════════════════════════════════════════════════════════════

    private void applyFilter() {
        mLogs.clear();
        for (SysConsoleLog l : mAllLogs) {
            if (matchesLevelFilter(l) && matchesApkFilter(l)) mLogs.add(l);
        }
        refreshAdapter(); updateFilterButton(); updateApkFilterButton();
    }

    private boolean matchesLevelFilter(SysConsoleLog l) {
        return "ALL".equals(mFilterLevel) || mFilterLevel.equalsIgnoreCase(l.level);
    }

    private boolean matchesApkFilter(SysConsoleLog l) {
        if (mApkFilterPackage == null) return true;
        if (l.source != null) {
            String src = l.source.toLowerCase(Locale.US);
            String pkg = mApkFilterPackage.toLowerCase(Locale.US);
            if (src.contains(pkg) || pkg.contains(src)) return true;
            String[] segs = mApkFilterPackage.split("\\.");
            if (segs.length > 0) {
                String last = segs[segs.length-1].toLowerCase(Locale.US);
                if (last.length() > 3 && src.contains(last)) return true;
            }
        }
        if (l.metadata != null && l.metadata.toLowerCase(Locale.US)
                .contains(mApkFilterPackage.toLowerCase(Locale.US))) return true;
        return false;
    }

    private void showFilterDialog() {
        final String[] levels = {"ALL","ERROR","WARN","INFO","DEBUG","LOG"};
        int current = 0;
        for (int i=0; i<levels.length; i++) if (levels[i].equals(mFilterLevel)) { current=i; break; }
        AlertDialog.Builder b = dialogBuilder();
        b.setTitle("Filter by Level");
        b.setSingleChoiceItems(levels, current, null);
        b.setPositiveButton("Apply", (d,w) -> {
            int sel = ((AlertDialog)d).getListView().getCheckedItemPosition();
            if (sel>=0 && sel<levels.length) { mFilterLevel=levels[sel]; applyFilter(); toast("Filter: "+mFilterLevel); }
        });
        b.setNegativeButton("Cancel", null);
        show(b);
    }

    private void showApkFilterDialog() {
        new Thread(() -> {
            try {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                List<String[]> allItems = new ArrayList<>();
                allItems.add(new String[]{"\u2713 All Apps (no filter)", null});
                for (ApplicationInfo app : apps) {
                    String label = pm.getApplicationLabel(app).toString();
                    boolean isSys = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    allItems.add(new String[]{isSys ? "[SYS] " + label : label, app.packageName});
                }
                Collections.sort(allItems.subList(1, allItems.size()),
                        (a, b) -> a[0].compareToIgnoreCase(b[0]));

                mHandler.post(() -> {
                    LinearLayout container = new LinearLayout(this);
                    container.setOrientation(LinearLayout.VERTICAL);

                    EditText searchBox = new EditText(this);
                    searchBox.setHint("Search apps…");
                    searchBox.setSingleLine(true);
                    searchBox.setTextSize(13f);
                    searchBox.setTextColor(0xFF111827);
                    searchBox.setHintTextColor(0xFF9CA3AF);
                    searchBox.setBackgroundColor(0xFFF3F4F6);
                    searchBox.setPadding(dp(12), dp(10), dp(12), dp(10));
                    LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    searchLp.setMargins(dp(4), dp(4), dp(4), dp(4));
                    container.addView(searchBox, searchLp);

                    final ListView listView = new ListView(this);
                    ArrayList<String> initNames = new ArrayList<>();
                    for (String[] item : allItems) initNames.add(item[0]);
                    final ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_list_item_1, initNames);
                    listView.setAdapter(adapter);
                    LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dp(320));
                    container.addView(listView, listLp);

                    final AlertDialog[] dialogRef = new AlertDialog[1];

                    listView.setOnItemClickListener((parent, view, pos, id) -> {
                        String chosen = adapter.getItem(pos);
                        String pkg = null;
                        String lbl = "ALL";
                        for (String[] item : allItems) {
                            if (item[0].equals(chosen)) { pkg = item[1]; lbl = item[0]; break; }
                        }
                        mApkFilterPackage = pkg;
                        mApkFilterLabel   = (pkg == null) ? "ALL" : lbl;
                        applyFilter();
                        toast("App filter: " + (mApkFilterPackage == null ? "All" : mApkFilterLabel)
                                + " — " + mLogs.size() + " logs");
                        if (dialogRef[0] != null) dialogRef[0].dismiss();
                    });

                    searchBox.addTextChangedListener(new TextWatcher() {
                        public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                        public void onTextChanged(CharSequence s, int st, int b, int c) {}
                        public void afterTextChanged(Editable s) {
                            String query = s.toString().trim().toLowerCase(Locale.US);
                            adapter.clear();
                            for (String[] item : allItems) {
                                if (query.isEmpty()
                                        || item[0].toLowerCase(Locale.US).contains(query)
                                        || (item[1] != null && item[1].toLowerCase(Locale.US).contains(query))) {
                                    adapter.add(item[0]);
                                }
                            }
                            adapter.notifyDataSetChanged();
                        }
                    });

                    AlertDialog.Builder b = dialogBuilder();
                    b.setTitle("Filter by App");
                    b.setView(container);
                    b.setNegativeButton("Cancel", null);
                    AlertDialog dlg = b.create();
                    dialogRef[0] = dlg;
                    if (dlg.getWindow() != null) {
                        dlg.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                        dlg.getWindow().setSoftInputMode(
                                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                    }
                    dlg.show();
                });
            } catch (Exception e) { mHandler.post(() -> toast("App list error: " + e.getMessage())); }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Status updates
    // ══════════════════════════════════════════════════════════════════════════

    private void updateStatusInfo() {
        if (mTvStatusInfo == null) return;
        String state = mLogcatPaused ? "Paused" : mIsConnected ? "Live" : mShizukuReady ? "Starting" : "No Shizuku";
        String ft = "ALL".equals(mFilterLevel) ? "" : " · "+mFilterLevel;
        String at = mApkFilterPackage!=null ? " · "+mApkFilterLabel : "";
        mTvStatusInfo.setText(mLogs.size()+" logs • "+state+ft+at);
    }

    private void updateConnectionDot() {
        if (mConnectionDot == null) return;
        setDotColor(mConnectionDot, mIsConnected && !mLogcatPaused ? 0xFF22C55E : 0xFFEF4444);
    }

    private void updateShizukuDot(boolean ok) {
        if (mShizukuDot == null) return;
        setDotColor(mShizukuDot, ok ? 0xFFF59E0B : 0xFF64748B);
        mShizukuReady = ok;
    }

    private void setDotColor(View dot, int color) {
        if (dot.getBackground() instanceof GradientDrawable)
            ((GradientDrawable)dot.getBackground()).setColor(color);
    }

    private void updateFilterButton() {
        if (mBtnFilter == null) return;
        mBtnFilter.setText("\u22a1 "+mFilterLevel);
        switch (mFilterLevel.toUpperCase()) {
            case "ERROR": mBtnFilter.setTextColor(0xFFEF4444); break;
            case "WARN":  mBtnFilter.setTextColor(0xFFD97706); break;
            case "INFO":  mBtnFilter.setTextColor(0xFF60A5FA); break;
            default:      mBtnFilter.setTextColor(0xFFCBD5E1); break;
        }
    }

    private void updateApkFilterButton() {
        if (mBtnApkFilter == null) return;
        if (mApkFilterPackage == null) {
            mBtnApkFilter.setText("📦 APP"); mBtnApkFilter.setTextColor(0xFFCBD5E1);
        } else {
            String label = mApkFilterLabel.startsWith("[SYS] ") ? mApkFilterLabel.substring(6) : mApkFilterLabel;
            if (label.length() > 10) label = label.substring(0,10)+"\u2026";
            mBtnApkFilter.setText("📦 "+label); mBtnApkFilter.setTextColor(0xFF34D399);
        }
    }

    private void updateErrorBadge() {
        if (mBtnErrors == null) return;
        int e=0, w=0;
        for (SysConsoleLog l : mLogs) { if (SysConsoleLog.LEVEL_ERROR.equals(l.level)) e++; else if (SysConsoleLog.LEVEL_WARN.equals(l.level)) w++; }
        int total = e+w;
        mBtnErrors.setText(total>0 ? "\u26A0 "+total : "\u26A0");
        mBtnErrors.setTextColor(total>0 ? 0xFFEF4444 : 0xFF64748B);
    }

    private void refreshAdapter() {
        if (mAdapter==null) return;
        mAdapter.notifyDataSetChanged();
        boolean empty = mLogs.isEmpty();
        mEmptyState.setVisibility(empty?View.VISIBLE:View.GONE);
        mLogList.setVisibility(empty?View.GONE:View.VISIBLE);
        updateStatusInfo(); updateErrorBadge();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Selection
    // ══════════════════════════════════════════════════════════════════════════

    private void enterSelectionMode() {
        mSelectionMode=true; mSelectedIds.clear();
        mSelectionBar.setVisibility(View.VISIBLE);
        updateSelectionBadge(); mAdapter.notifyDataSetChanged();
    }
    private void exitSelectionMode() {
        mSelectionMode=false; mSelectedIds.clear();
        mSelectionBar.setVisibility(View.GONE);
        mTvSelectionBadge.setVisibility(View.GONE);
        mAdapter.notifyDataSetChanged();
    }
    private void toggleLogSelection(int id) {
        if (mSelectedIds.contains(id)) mSelectedIds.remove(id); else mSelectedIds.add(id);
        updateSelectionBadge(); mAdapter.notifyDataSetChanged();
    }
    private void selectAllLogs()  { for (SysConsoleLog l:mLogs) mSelectedIds.add(l.id); updateSelectionBadge(); mAdapter.notifyDataSetChanged(); }
    private void clearSelection() { mSelectedIds.clear(); updateSelectionBadge(); mAdapter.notifyDataSetChanged(); }
    private void updateSelectionBadge() {
        int n=mSelectedIds.size();
        mTvSelectionBadge.setText(n+" selected");
        mTvSelectionBadge.setVisibility(n>0?View.VISIBLE:View.GONE);
        mBtnCopySelected.setVisibility(n>0?View.VISIBLE:View.GONE);
        mBtnCopySelected.setText("Copy Selected ("+n+")");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Copy
    // ══════════════════════════════════════════════════════════════════════════

    private void showCopyMenu() {
        String[] opts = {"Copy All Logs","Select & Copy","Copy Range..."};
        AlertDialog.Builder b = dialogBuilder().setTitle("Copy");
        b.setItems(opts,(d,w)->{
            if (w==0) copyAllLogs();
            else if (w==1) { if(!mSelectionMode) enterSelectionMode(); }
            else showRangeDialog();
        });
        b.setNegativeButton("Cancel",null);
        show(b);
    }

    private void copyAllLogs() {
        StringBuilder sb=new StringBuilder(); List<SysConsoleLog> rev=new ArrayList<>(mLogs); Collections.reverse(rev);
        for (SysConsoleLog l:rev) sb.append(l.toLogLine()).append('\n');
        toClipboard(sb.toString()); toast("Copied "+mLogs.size()+" logs");
    }

    private void copySelectedLogs() {
        List<SysConsoleLog> rev=new ArrayList<>(mLogs); Collections.reverse(rev);
        StringBuilder sb=new StringBuilder(); int count=0;
        for (SysConsoleLog l:rev) if (mSelectedIds.contains(l.id)) { sb.append(l.toLogLine()).append('\n'); count++; }
        toClipboard(sb.toString()); toast("Copied "+count+" selected logs"); exitSelectionMode();
    }

    private void copySpecificLog(SysConsoleLog log) {
        String full = log.toLogLine() + (log.metadata!=null&&!log.metadata.isEmpty() ? "\n["+log.metadata+"]" : "");
        toClipboard(full); toast("Log entry copied");
    }

    private void showRangeDialog() {
        LinearLayout outer=new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.setPadding(dp(24),dp(8),dp(24),0);
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        EditText etS=new EditText(this); etS.setHint("Start"); etS.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText etE=new EditText(this); etE.setHint("End"); etE.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        row.addView(etS, new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        row.addView(etE, new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        outer.addView(row);
        AlertDialog.Builder b=dialogBuilder(); b.setTitle("Copy Range"); b.setView(outer);
        b.setPositiveButton("Copy",(d,w)->{
            try { copyRange(Integer.parseInt(etS.getText().toString()),Integer.parseInt(etE.getText().toString())); }
            catch(NumberFormatException ex) { toast("Invalid range"); }
        }); b.setNegativeButton("Cancel",null); show(b);
    }

    private void copyRange(int start, int end) {
        List<SysConsoleLog> sorted=new ArrayList<>(mLogs); Collections.reverse(sorted);
        start=Math.max(1,start); end=Math.min(sorted.size(),end);
        if (start>end) { toast("Invalid range"); return; }
        StringBuilder sb=new StringBuilder();
        for (int i=start-1; i<end; i++) sb.append(sorted.get(i).toLogLine()).append('\n');
        toClipboard(sb.toString()); toast("Copied range "+start+"-"+end);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Save / Saved
    // ══════════════════════════════════════════════════════════════════════════

    private void showSaveDialog() {
        LinearLayout outer=new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.setPadding(dp(24),dp(8),dp(24),0);
        EditText et=new EditText(this);
        String def="SysLogs_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
        et.setText(def); outer.addView(et);
        AlertDialog.Builder b=dialogBuilder(); b.setTitle("Save Logs"); b.setView(outer);
        b.setPositiveButton("Save",(d,w)->{
            String name=et.getText().toString().trim(); if(name.isEmpty()) name=def;
            final String n=name; final List<SysConsoleLog> snap=new ArrayList<>(mLogs);
            new Thread(()->{
                String id=UUID.randomUUID().toString();
                String savedAt=new SimpleDateFormat("dd/MM/yy HH:mm:ss",Locale.US).format(new Date());
                mDb.saveCollection(new SysConsoleDatabaseHelper.SavedLogCollection(id,n,snap,savedAt,snap.size()));
                mHandler.post(()->toast("Saved \""+n+"\" — "+snap.size()+" entries"));
            }).start();
        }); b.setNegativeButton("Cancel",null); show(b);
    }

    private void showSavedLogsDialog() {
        new Thread(()->{
            List<SysConsoleDatabaseHelper.SavedLogCollection> cols=mDb.loadCollections();
            mHandler.post(()->buildSavedDialog(cols));
        }).start();
    }

    private void buildSavedDialog(List<SysConsoleDatabaseHelper.SavedLogCollection> cols) {
        AlertDialog.Builder b=dialogBuilder(); b.setTitle("Saved Collections");
        if (cols.isEmpty()) { b.setMessage("No saved collections."); b.setNegativeButton("Close",null); show(b); return; }
        ScrollView sv=new ScrollView(this); LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(8),dp(8),dp(8),dp(8)); sv.addView(c);
        for (SysConsoleDatabaseHelper.SavedLogCollection col:cols) c.addView(buildCollectionRow(col));
        b.setView(sv); b.setNegativeButton("Close",null); show(b);
    }

    private View buildCollectionRow(SysConsoleDatabaseHelper.SavedLogCollection col) {
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(10),dp(10),dp(10),dp(10));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xFFF9FAFB); bg.setCornerRadius(dp(6)); bg.setStroke(dp(1),0xFFE5E7EB); row.setBackground(bg);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT); rp.bottomMargin=dp(8); row.setLayoutParams(rp);
        TextView tvN=new TextView(this); tvN.setText(col.name); tvN.setTextSize(14f); tvN.setTextColor(0xFF111827); tvN.setTypeface(tvN.getTypeface(),Typeface.BOLD); row.addView(tvN);
        TextView tvS=new TextView(this); tvS.setText(col.totalEntries+" entries • "+col.savedAt); tvS.setTextSize(11f); tvS.setTextColor(0xFF6B7280); row.addView(tvS);
        LinearLayout btns=new LinearLayout(this); btns.setOrientation(LinearLayout.HORIZONTAL); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT); bp.topMargin=dp(8); btns.setLayoutParams(bp);
        Button btnL=smallBtn("Load"); btnL.setOnClickListener(v->loadSavedLogs(col)); btns.addView(btnL);
        Button btnE=smallBtn("\u2b07 Export"); btnE.setOnClickListener(v->exportSavedLogs(col)); btns.addView(btnE);
        Button btnD=smallBtn("\uD83D\uDDD1 Delete"); btnD.setTextColor(0xFFEF4444); btnD.setOnClickListener(v->new Thread(()->{mDb.deleteCollection(col.id);mHandler.post(()->{ toast("Deleted \""+col.name+"\""); showSavedLogsDialog();});}).start()); btns.addView(btnD);
        row.addView(btns); return row;
    }

    private void loadSavedLogs(SysConsoleDatabaseHelper.SavedLogCollection col) {
        new Thread(()->{
            List<SysConsoleLog> logs=mDb.getLogsForCollection(col.id);
            mHandler.post(()->{
                mLogcatPaused=true; mBtnPause.setText("▶"); mBtnPause.setTextColor(0xFFEAB308);
                mIsConnected=false; updateConnectionDot();
                mAllLogs.clear(); mAllLogs.addAll(logs); Collections.reverse(mAllLogs);
                applyFilter(); toast("Loaded \""+col.name+"\" — "+logs.size()+" entries. Tap ▶ to resume.");
            });
        }).start();
    }

    private void exportSavedLogs(SysConsoleDatabaseHelper.SavedLogCollection col) {
        new Thread(()->{
            try {
                List<SysConsoleLog> logs=mDb.getLogsForCollection(col.id);
                if(logs.isEmpty()){mHandler.post(()->toast("Collection is empty"));return;}
                File pdf=writePdfFile(col.name,logs);
                mHandler.post(()->sharePdf(pdf,col.name));
            } catch(Exception e){mHandler.post(()->toast("Export failed: "+e.getMessage()));}
        }).start();
    }

    private void sharePdf(File f,String name) {
        try {
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".provider",f);
            Intent i=new Intent(Intent.ACTION_SEND); i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_STREAM,uri); i.putExtra(Intent.EXTRA_SUBJECT,"System Console — "+name);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
            Intent chooser=Intent.createChooser(i,"Share PDF"); chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(chooser);
        } catch(Exception e){toast("Share failed: "+e.getMessage());}
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error dialog
    // ══════════════════════════════════════════════════════════════════════════

    private void showErrorDialog() {
        List<SysConsoleLog> errLogs=new ArrayList<>();
        int errors=0,warns=0;
        for (SysConsoleLog l:mLogs) { if(SysConsoleLog.LEVEL_ERROR.equals(l.level)){errors++;errLogs.add(l);} else if(SysConsoleLog.LEVEL_WARN.equals(l.level)){warns++;errLogs.add(l);} }
        AlertDialog.Builder b=dialogBuilder(); b.setTitle("Errors & Warnings — E:"+errors+" W:"+warns);
        if(errLogs.isEmpty()){b.setMessage("No errors or warnings."); }
        else {
            StringBuilder sb=new StringBuilder();
            for(SysConsoleLog l:errLogs){sb.append("#").append(l.id).append(" [").append(l.level).append("] ").append(l.timestamp).append(" ").append(l.source).append("\n").append(l.message).append("\n"); if(l.metadata!=null) sb.append("  ").append(l.metadata).append("\n"); sb.append('\n');}
            final String text=sb.toString().trim(); TextView tv=new TextView(this); tv.setText(text); tv.setTextSize(11f); tv.setTypeface(Typeface.MONOSPACE); tv.setPadding(dp(16),dp(12),dp(16),dp(12));
            ScrollView sv=new ScrollView(this); sv.addView(tv); b.setView(sv);
            b.setNeutralButton("Copy All",(d,w)->{toClipboard(text);toast("Copied "+errLogs.size()+" entries");});
        }
        b.setNegativeButton("Close",null); show(b);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PDF export
    // ══════════════════════════════════════════════════════════════════════════

    private File writePdfFile(String title, List<SysConsoleLog> logs) throws IOException {
        final int PW=842,PH=1190,M=28,CW=PW-M*2;
        Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG),card=new Paint(Paint.ANTI_ALIAS_FLAG),badge=new Paint(Paint.ANTI_ALIAS_FLAG),border=new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE); border.setStrokeWidth(1f);
        Paint num=monoPaint(11f,Color.parseColor("#6B7280")),badgeText=new Paint(Paint.ANTI_ALIAS_FLAG),src=monoPaint(10f,Color.parseColor("#374151")),ts=monoPaint(10f,Color.parseColor("#9CA3AF"));
        badgeText.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)); badgeText.setTextSize(10f); badgeText.setColor(0xFFFFFFFF);
        Paint msg=new Paint(Paint.ANTI_ALIAS_FLAG); msg.setTypeface(Typeface.MONOSPACE); msg.setTextSize(11.5f);
        Paint meta=monoPaint(9.5f,Color.parseColor("#6B7280")),hBg=new Paint(Paint.ANTI_ALIAS_FLAG),hText=new Paint(Paint.ANTI_ALIAS_FLAG),hSub=monoPaint(10f,Color.parseColor("#94A3B8"));
        hBg.setColor(Color.parseColor("#0F172A")); hText.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD)); hText.setTextSize(13f); hText.setColor(0xFFFFFFFF);
        PdfDocument doc=new PdfDocument(); int pNum=0; PdfDocument.Page page=null; Canvas cv=null; float y=0;
        String dateStr=new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.US).format(new Date());
        for(int i=0;i<logs.size();i++){
            SysConsoleLog log=logs.get(i); String lvl=log.level!=null?log.level.toUpperCase(Locale.US):"LOG";
            int cBg,cBorder,bColor,mColor;
            switch(lvl){case"ERROR":cBg=Color.parseColor("#FFF5F5");cBorder=Color.parseColor("#FECACA");bColor=Color.parseColor("#EF4444");mColor=Color.parseColor("#7F1D1D");break;case"WARN":cBg=Color.parseColor("#FEFCE8");cBorder=Color.parseColor("#FEF08A");bColor=Color.parseColor("#EAB308");mColor=Color.parseColor("#78350F");break;case"INFO":cBg=Color.parseColor("#EFF6FF");cBorder=Color.parseColor("#BFDBFE");bColor=Color.parseColor("#3B82F6");mColor=Color.parseColor("#1E3A5F");break;default:cBg=Color.parseColor("#F9FAFB");cBorder=Color.parseColor("#E5E7EB");bColor=Color.parseColor("#6B7280");mColor=Color.parseColor("#374151");break;}
            msg.setColor(mColor);
            List<String> msgLines=wrapText(log.message,CW-20f,msg);
            boolean hasMeta=log.metadata!=null&&!log.metadata.isEmpty();
            List<String> metaLines=hasMeta?wrapText(log.metadata,CW-26f,meta):null;
            float bw=badgeText.measureText(lvl)+16f,sw=src.measureText(log.source!=null?log.source:"")+16f,rH=20f;
            float cardH=10f+rH+6f+msgLines.size()*14f+(hasMeta?metaLines.size()*12f+16f:0f)+10f;
            float hH=48f;
            if(page==null||y+cardH>PH-M){if(page!=null)doc.finishPage(page);pNum++;PdfDocument.PageInfo inf=new PdfDocument.PageInfo.Builder(PW,PH,pNum).create();page=doc.startPage(inf);cv=page.getCanvas();cv.drawRect(0,0,PW,hH,hBg);cv.drawText("\u25cf System Console \u2014 "+title,M,18f,hText);cv.drawText(dateStr+"  \u00b7  "+logs.size()+" entries  \u00b7  page "+pNum,M,36f,hSub);y=hH+M/2f;}
            float cx2=M,cy=y; card.setColor(cBg); border.setColor(cBorder);
            RectF r=new RectF(cx2,cy,cx2+CW,cy+cardH); cv.drawRoundRect(r,8f,8f,card); cv.drawRoundRect(r,8f,8f,border);
            float rx=cx2+10f,ry=cy+10f+rH*0.75f;
            cv.drawText("#"+(i+1),rx,ry,num); rx+=num.measureText("#"+(i+1))+6f;
            badge.setColor(bColor); cv.drawRoundRect(new RectF(rx,cy+10f,rx+bw,cy+10f+rH),5f,5f,badge); cv.drawText(lvl,rx+8f,ry,badgeText); rx+=bw+4f;
            badge.setColor(Color.parseColor("#E5E7EB")); cv.drawRoundRect(new RectF(rx,cy+10f,rx+sw,cy+10f+rH),5f,5f,badge); cv.drawText(log.source!=null?log.source:"",rx+8f,ry,src); rx+=sw+4f;
            cv.drawText(log.timestamp!=null?log.timestamp:"",rx,ry,ts);
            float my=cy+10f+rH+6f;
            for(String l:msgLines){cv.drawText(l,cx2+10f,my+11f,msg);my+=14f;}
            if(hasMeta){Paint mb=new Paint(Paint.ANTI_ALIAS_FLAG);mb.setColor(Color.parseColor("#F3F4F6"));float mx1=cx2+10f,my1=my+4f,mx2=cx2+CW-10f,my2=my1+metaLines.size()*12f+6f;cv.drawRoundRect(new RectF(mx1,my1,mx2,my2),4f,4f,mb);float ty=my1+11f;for(String l:metaLines){cv.drawText(l,mx1+6f,ty,meta);ty+=12f;}}
            y=cy+cardH+7f;
        }
        if(page!=null)doc.finishPage(page);
        String fname="sysconsole_"+title.replaceAll("[^a-zA-Z0-9]","_")+"_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".pdf";
        File dir=new File(getCacheDir(),"SysConsole"); if(!dir.exists())dir.mkdirs();
        File f=new File(dir,fname); FileOutputStream fos=new FileOutputStream(f); doc.writeTo(fos); fos.close(); doc.close(); return f;
    }

    private Paint monoPaint(float size, int color) {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setTypeface(Typeface.MONOSPACE); p.setTextSize(size); p.setColor(color); return p;
    }

    private List<String> wrapText(String text, float maxW, Paint paint) {
        List<String> r=new ArrayList<>();
        if(text==null||text.isEmpty()){r.add("");return r;}
        for(String para:text.split("\n",-1)){
            if(para.isEmpty()){r.add("");continue;}
            int start=0;
            while(start<para.length()){int n=paint.breakText(para,start,para.length(),true,maxW,null);if(n<=0)n=1;r.add(para.substring(start,start+n));start+=n;}
        }
        return r;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Adapter
    // ══════════════════════════════════════════════════════════════════════════

    private class LogAdapter extends BaseAdapter {
        public int     getCount()         { return mLogs.size(); }
        public Object  getItem(int pos)   { return mLogs.get(pos); }
        public long    getItemId(int pos) { return mLogs.get(pos).id; }
        public boolean hasStableIds()     { return true; }

        public View getView(int pos, View cv2, android.view.ViewGroup parent) {
            VH vh;
            if(cv2==null){
                cv2=LayoutInflater.from(SysConsoleService.this).inflate(R.layout.log_item,parent,false);
                vh=new VH(); vh.number=cv2.findViewById(R.id.log_number); vh.levelBadge=cv2.findViewById(R.id.log_level_badge);
                vh.sourceBadge=cv2.findViewById(R.id.log_source_badge); vh.timestamp=cv2.findViewById(R.id.log_timestamp);
                vh.selIndicator=cv2.findViewById(R.id.log_selected_indicator); vh.copySingle=cv2.findViewById(R.id.btn_copy_single);
                vh.message=cv2.findViewById(R.id.log_message); vh.metadata=cv2.findViewById(R.id.log_metadata);
                cv2.setTag(vh);
            } else vh=(VH)cv2.getTag();
            SysConsoleLog log=mLogs.get(pos); boolean sel=mSelectedIds.contains(log.id);
            vh.number.setText("#"+(mLogs.size()-pos));
            styleBadge(vh.levelBadge,log.level);
            vh.sourceBadge.setText(log.source); vh.timestamp.setText(log.timestamp); vh.message.setText(log.message);
            styleMsg(vh.message,log.level);
            if(log.metadata!=null&&!log.metadata.isEmpty()){vh.metadata.setVisibility(View.VISIBLE);vh.metadata.setText(log.metadata);}else{vh.metadata.setVisibility(View.GONE);}
            setItemBg(cv2,log.level,sel);
            if(mSelectionMode){vh.selIndicator.setVisibility(View.VISIBLE);vh.selIndicator.setText(sel?"\u2713":"");vh.selIndicator.setTextColor(0xFFFFFFFF);vh.selIndicator.setBackgroundResource(sel?R.drawable.dev_console_check_checked:R.drawable.dev_console_check_unchecked);}else{vh.selIndicator.setVisibility(View.GONE);}
            vh.copySingle.setVisibility(mSelectionMode?View.GONE:View.VISIBLE);
            vh.copySingle.setOnClickListener(v->copySpecificLog(log));
            return cv2;
        }

        private void styleBadge(TextView tv, String level) {
            GradientDrawable bg=new GradientDrawable(); bg.setCornerRadius(dp(4));
            switch(level.toUpperCase()){case"ERROR":bg.setColor(0xFFEF4444);break;case"WARN":bg.setColor(0xFFEAB308);break;case"INFO":bg.setColor(0xFF3B82F6);break;default:bg.setColor(0xFF6B7280);}
            tv.setBackground(bg); tv.setText(level.toUpperCase()); tv.setTextColor(0xFFFFFFFF);
        }
        private void styleMsg(TextView tv, String level) {
            switch(level.toUpperCase()){case"ERROR":tv.setTextColor(0xFFDC2626);break;case"WARN":tv.setTextColor(0xFFCA8A04);break;case"INFO":tv.setTextColor(0xFF2563EB);break;case"DEBUG":tv.setTextColor(0xFF4B5563);break;default:tv.setTextColor(0xFF374151);}
        }
        private void setItemBg(View v, String level, boolean sel) {
            GradientDrawable bg=new GradientDrawable(); bg.setCornerRadius(dp(6));
            if(sel){bg.setColor(0xFFEFF6FF);bg.setStroke(dp(2),0xFF3B82F6);}
            else{switch(level.toUpperCase()){case"ERROR":bg.setColor(0xFFFFF5F5);bg.setStroke(dp(1),0xFFFECACA);break;case"WARN":bg.setColor(0xFFFEFCE8);bg.setStroke(dp(1),0xFFFEF08A);break;case"INFO":bg.setColor(0xFFEFF6FF);bg.setStroke(dp(1),0xFFBFDBFE);break;default:bg.setColor(0xFFFFFFFF);bg.setStroke(dp(1),0xFFE5E7EB);}}
            v.setBackground(bg);
        }
    }

    private static class VH {
        TextView number,levelBadge,sourceBadge,timestamp,selIndicator,copySingle,message,metadata;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private AlertDialog.Builder dialogBuilder() {
        return new AlertDialog.Builder(new android.view.ContextThemeWrapper(this, android.R.style.Theme_Material_Light_Dialog_Alert));
    }
    private void show(AlertDialog.Builder b) {
        AlertDialog d=b.create();
        if(d.getWindow()!=null) d.getWindow().setType(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        d.show();
    }
    private Button smallBtn(String label) {
        Button btn=new Button(this); btn.setText(label); btn.setTextSize(11f); btn.setTextColor(0xFF374151);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xFFFFFFFF); bg.setCornerRadius(dp(4)); bg.setStroke(dp(1),0xFFD1D5DB); btn.setBackground(bg);
        btn.setPadding(dp(10),0,dp(10),0); btn.setAllCaps(false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,dp(32)); lp.rightMargin=dp(6); btn.setLayoutParams(lp);
        return btn;
    }
    private void toClipboard(String text) { ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE); if(cm!=null) cm.setPrimaryClip(ClipData.newPlainText("SysConsole",text)); }
    private void toast(String msg) { Toast.makeText(this,msg,Toast.LENGTH_SHORT).show(); }
    private int dp(int dp) { return Math.round(dp*getResources().getDisplayMetrics().density); }
}
