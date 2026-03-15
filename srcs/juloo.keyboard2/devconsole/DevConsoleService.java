package juloo.keyboard2.devconsole;

import android.app.AlertDialog;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import juloo.keyboard2.R;

/**
 * Floating developer console service.
 *
 * Matches the React console component UI & functionality exactly:
 * - Header: connection dot, "Enhanced Console", Server/Logcat toggle, status info, action buttons
 * - Selection mode bar (Select All, Clear, Copy Selected, Exit Selection)
 * - Log list (entry#, level badge, source badge, timestamp, message, metadata, copy button)
 * - Scroll-to-top / scroll-to-bottom buttons inside log area
 * - Resize handle (bottom-right)
 * - Dialogs: Save, Range Copy, Saved Logs (Load/Export/Delete per item), Error/Warn viewer
 */
public class DevConsoleService extends Service {

    public static final String ACTION_SHOW = "juloo.keyboard2.devconsole.SHOW";
    public static final String ACTION_HIDE = "juloo.keyboard2.devconsole.HIDE";

    // ── Window ───────────────────────────────────────────────────────────────
    private WindowManager              mWM;
    private View                       mRoot;
    private WindowManager.LayoutParams mParams;
    private int                        mSavedW, mSavedH;
    private boolean                    mIsMaximized;

    // ── Header views ─────────────────────────────────────────────────────────
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
    private TextView mBtnClose;

    // ── Selection bar views ──────────────────────────────────────────────────
    private View   mSelectionBar;
    private Button mBtnSelectAll;
    private Button mBtnClearSelection;
    private Button mBtnCopySelected;
    private Button mBtnExitSelection;

    // ── Log area views ───────────────────────────────────────────────────────
    private ListView mLogList;
    private View     mEmptyState;
    private TextView mBtnScrollTop;
    private TextView mBtnScrollBottom;

    // ── State ────────────────────────────────────────────────────────────────
    private final List<DevConsoleLog> mLogs        = new ArrayList<>();
    private final Set<Integer>        mSelectedIds = new HashSet<>();
    private LogAdapter                mAdapter;
    private boolean                   mSelectionMode = false;
    private boolean                   mShowingLogcat = false;
    private boolean                   mIsConnected   = false;

    // ── Core ─────────────────────────────────────────────────────────────────
    private final Handler            mHandler = new Handler(Looper.getMainLooper());
    private DevConsoleManager        mManager;
    private DevConsoleDatabaseHelper mDb;

    private final DevConsoleManager.OnNewLogListener mLogListener =
            new DevConsoleManager.OnNewLogListener() {
                @Override
                public void onNewLog(DevConsoleLog log, boolean isLogcat) {
                    if (isLogcat != mShowingLogcat) return;
                    mHandler.post(() -> {
                        mLogs.add(0, log);
                        if (mLogs.size() > 1000) mLogs.remove(mLogs.size() - 1);
                        refreshAdapter();
                    });
                }

                @Override
                public void onLogsCleared() {
                    mHandler.post(() -> { mLogs.clear(); refreshAdapter(); });
                }
            };

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        mManager = DevConsoleManager.getInstance();
        mDb      = DevConsoleDatabaseHelper.getInstance(this);
        mManager.startLogcatReader();
        mManager.addListener(mLogListener);
        mIsConnected = true;
        createWindow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_HIDE.equals(intent.getAction())) stopSelf();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mManager.removeListener(mLogListener);
        if (mRoot != null) { mWM.removeView(mRoot); mRoot = null; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Window creation
    // ══════════════════════════════════════════════════════════════════════════

    private void createWindow() {
        mWM = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        mWM.getDefaultDisplay().getMetrics(dm);

        mRoot = LayoutInflater.from(this).inflate(R.layout.dev_console_overlay, null);

        int layer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int w = (int) (dm.widthPixels  * 0.93f);
        int h = (int) (dm.heightPixels * 0.55f);
        mSavedW = w; mSavedH = h;

        mParams = new WindowManager.LayoutParams(w, h, layer,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = (dm.widthPixels  - w) / 2;
        mParams.y = (int) (dm.heightPixels * 0.18f);

        mWM.addView(mRoot, mParams);
        bindViews();
        loadCurrentSource();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Bind views
    // ══════════════════════════════════════════════════════════════════════════

    private void bindViews() {
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
        mBtnClose         = mRoot.findViewById(R.id.btn_close);

        mSelectionBar      = mRoot.findViewById(R.id.selection_bar);
        mBtnSelectAll      = mRoot.findViewById(R.id.btn_select_all);
        mBtnClearSelection = mRoot.findViewById(R.id.btn_clear_selection);
        mBtnCopySelected   = mRoot.findViewById(R.id.btn_copy_selected);
        mBtnExitSelection  = mRoot.findViewById(R.id.btn_exit_selection);

        mLogList        = mRoot.findViewById(R.id.console_log_list);
        mEmptyState     = mRoot.findViewById(R.id.empty_state);
        mBtnScrollTop   = mRoot.findViewById(R.id.btn_scroll_top);
        mBtnScrollBottom = mRoot.findViewById(R.id.btn_scroll_bottom);

        mAdapter = new LogAdapter();
        mLogList.setAdapter(mAdapter);

        // Log list click = copy in normal mode, toggle-select in selection mode (matches React)
        mLogList.setOnItemClickListener((parent, view, position, id) -> {
            DevConsoleLog log = mLogs.get(position);
            if (mSelectionMode) toggleLogSelection(log.id);
            else copySpecificLog(log);
        });
        // Long press = enter selection mode (matches React)
        mLogList.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!mSelectionMode) enterSelectionMode();
            toggleLogSelection(mLogs.get(position).id);
            return true;
        });

        mBtnPause.setOnClickListener(v -> handlePauseResume());
        mBtnRefresh.setOnClickListener(v -> handleRefresh());
        mBtnErrors.setOnClickListener(v -> showErrorDialog());
        mBtnCopy.setOnClickListener(v -> showCopyMenu());
        mBtnSave.setOnClickListener(v -> showSaveDialog());
        mBtnSaved.setOnClickListener(v -> showSavedLogsDialog());
        mBtnMaximize.setOnClickListener(v -> toggleMaximize());
        mBtnClose.setOnClickListener(v -> stopSelf());
        mBtnToggleSource.setOnClickListener(v -> toggleSource());

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
        updateConnectionDot();
        updateStatusInfo();
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
                    case MotionEvent.ACTION_DOWN: ix=mParams.x; iy=mParams.y; tx=e.getRawX(); ty=e.getRawY(); return true;
                    case MotionEvent.ACTION_MOVE:
                        mParams.x = ix + (int)(e.getRawX()-tx);
                        mParams.y = iy + (int)(e.getRawY()-ty);
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
                    case MotionEvent.ACTION_DOWN: iw=mParams.width; ih=mParams.height; tx=e.getRawX(); ty=e.getRawY(); return true;
                    case MotionEvent.ACTION_MOVE:
                        DisplayMetrics dm = new DisplayMetrics();
                        mWM.getDefaultDisplay().getMetrics(dm);
                        mParams.width  = Math.max(320, Math.min(iw+(int)(e.getRawX()-tx), dm.widthPixels));
                        mParams.height = Math.max(200, Math.min(ih+(int)(e.getRawY()-ty), dm.heightPixels));
                        mWM.updateViewLayout(mRoot, mParams); return true;
                }
                return false;
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Pause / Resume  (React: handlePauseResume)
    // ══════════════════════════════════════════════════════════════════════════

    private void handlePauseResume() {
        boolean nowPaused = !mManager.isPaused();
        mManager.setPaused(nowPaused);
        mBtnPause.setText(nowPaused ? "▶" : "⏸");
        mBtnPause.setTextColor(nowPaused ? 0xFFEAB308 : 0xFF374151);
        updateConnectionDot();
        updateStatusInfo();
        toast(nowPaused ? "Live logs paused" : "Resuming live logs");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Refresh  (React: handleLastLog / fetchLogs; disabled for browser/logcat mode)
    // ══════════════════════════════════════════════════════════════════════════

    private void handleRefresh() {
        if (mShowingLogcat) {
            toast("Refresh not available for Logcat logs");
            return;
        }
        loadCurrentSource();
        toast("Refreshed");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Server / Logcat toggle  (React: Server ↔ Browser)
    // ══════════════════════════════════════════════════════════════════════════

    private void toggleSource() {
        mShowingLogcat = !mShowingLogcat;
        if (mShowingLogcat) {
            mBtnToggleSource.setText("💻 Logcat");
            mBtnRefresh.setAlpha(0.4f);
            mIsConnected = false;
        } else {
            mBtnToggleSource.setText("⬛ Server");
            mBtnRefresh.setAlpha(1.0f);
            mIsConnected = true;
        }
        updateConnectionDot();
        loadCurrentSource();
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
    // Data loading
    // ══════════════════════════════════════════════════════════════════════════

    private void loadCurrentSource() {
        mLogs.clear();
        List<DevConsoleLog> src = mShowingLogcat
                ? mManager.getLogcatLogs()
                : mManager.getAppLogs();
        mLogs.addAll(src);
        refreshAdapter();
    }

    private void refreshAdapter() {
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
        String state;
        if (mManager.isPaused())    state = "Paused";
        else if (mShowingLogcat)    state = "Capturing";
        else if (mIsConnected)      state = "Live";
        else                        state = "Disconnected";
        mTvStatusInfo.setText(mLogs.size() + " logs \u2022 " + state);
    }

    private void updateConnectionDot() {
        boolean active = mIsConnected && !mManager.isPaused();
        if (mConnectionDot.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) mConnectionDot.getBackground())
                    .setColor(active ? 0xFF22C55E : 0xFFEF4444);
        }
    }

    private void updateErrorBadge() {
        int errors = 0, warns = 0;
        for (DevConsoleLog l : mLogs) {
            if      (DevConsoleLog.LEVEL_ERROR.equals(l.level)) errors++;
            else if (DevConsoleLog.LEVEL_WARN.equals(l.level))  warns++;
        }
        int total = errors + warns;
        mBtnErrors.setText(total > 0 ? "\u26A0 " + total : "\u26A0");
        mBtnErrors.setTextColor(total > 0 ? 0xFFEF4444 : 0xFF9CA3AF);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Selection mode  (React: isSelectionMode, selectedLogIds)
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
    // Copy functions  (React: copyAllLogs, copySelectedLogs, copyRangeLogs, copySpecificLog)
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
                case 1:
                    if (!mSelectionMode) enterSelectionMode();
                    else toast("Tap log entries to select");
                    break;
                case 2: showRangeDialog(); break;
                case 3: copySelectedLogs(); break;
            }
        });
        show(b);
    }

    private void copyAllLogs() {
        StringBuilder sb = new StringBuilder();
        List<DevConsoleLog> reversed = new ArrayList<>(mLogs);
        Collections.reverse(reversed);
        for (DevConsoleLog l : reversed) sb.append(l.toLogLine()).append('\n');
        toClipboard(sb.toString());
        toast("All Logs Copied \u2014 " + mLogs.size() + " log entries copied to clipboard");
    }

    private void copySelectedLogs() {
        List<DevConsoleLog> reversed = new ArrayList<>(mLogs);
        Collections.reverse(reversed);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (DevConsoleLog l : reversed) {
            if (mSelectedIds.contains(l.id)) { sb.append(l.toLogLine()).append('\n'); count++; }
        }
        toClipboard(sb.toString());
        toast("Selected Logs Copied \u2014 " + count + " selected log entries copied");
        exitSelectionMode();
    }

    private void copySpecificLog(DevConsoleLog log) {
        toClipboard(log.toLogLine());
        toast("Log Entry Copied \u2014 Single log entry copied to clipboard");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Range copy dialog  (React: showRangeDialog + copyRangeLogs)
    // ══════════════════════════════════════════════════════════════════════════

    private void showRangeDialog() {
        int total = mLogs.size();

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(24), dp(8), dp(24), 0);

        TextView desc = new TextView(this);
        desc.setText("Select a range of log entries to copy to your clipboard.");
        desc.setTextSize(12f); desc.setTextColor(0xFF6B7280);
        outer.addView(desc);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(12);
        row.setLayoutParams(rp);

        // Start column
        LinearLayout colS = new LinearLayout(this); colS.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cs = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        cs.rightMargin = dp(8); colS.setLayoutParams(cs);
        TextView lS = new TextView(this); lS.setText("Start Entry #"); lS.setTextSize(12f); lS.setTextColor(0xFF374151);
        EditText etS = new EditText(this); etS.setHint("1"); etS.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        colS.addView(lS); colS.addView(etS);

        // End column
        LinearLayout colE = new LinearLayout(this); colE.setOrientation(LinearLayout.VERTICAL);
        colE.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView lE = new TextView(this); lE.setText("End Entry #"); lE.setTextSize(12f); lE.setTextColor(0xFF374151);
        EditText etE = new EditText(this); etE.setHint(String.valueOf(total)); etE.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        colE.addView(lE); colE.addView(etE);

        row.addView(colS); row.addView(colE);
        outer.addView(row);

        TextView info = new TextView(this);
        info.setText("Total entries available: " + total + ". Entries are numbered oldest (1) to newest (" + total + ").");
        info.setTextSize(11f); info.setTextColor(0xFF6B7280);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ip.topMargin = dp(8);
        outer.addView(info, ip);

        AlertDialog.Builder b = dialogBuilder();
        b.setTitle("Copy Log Range");
        b.setView(outer);
        b.setPositiveButton("Copy Range", (d, w) -> {
            try {
                int start = Integer.parseInt(etS.getText().toString().trim());
                int end   = Integer.parseInt(etE.getText().toString().trim());
                copyRange(start, end);
            } catch (NumberFormatException ex) {
                toast("Invalid Range \u2014 Please specify both start and end entry numbers");
            }
        });
        b.setNegativeButton("Cancel", null);
        show(b);
    }

    private void copyRange(int start, int end) {
        List<DevConsoleLog> sorted = new ArrayList<>(mLogs);
        Collections.reverse(sorted);
        start = Math.max(1, start);
        end   = Math.min(sorted.size(), end);
        if (start > end) { toast("Invalid Range \u2014 start must be \u2264 end"); return; }
        StringBuilder sb = new StringBuilder();
        for (int i = start - 1; i < end; i++) sb.append(sorted.get(i).toLogLine()).append('\n');
        toClipboard(sb.toString());
        toast("Range Copied \u2014 Log entries " + start + "-" + end + " (" + (end-start+1) + " entries) copied");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Save logs dialog  (React: showSaveDialog + saveCurrentLogs)
    // ══════════════════════════════════════════════════════════════════════════

    private void showSaveDialog() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(24), dp(8), dp(24), 0);

        TextView lbl = new TextView(this);
        lbl.setText("Collection Name");
        lbl.setTextSize(12f); lbl.setTextColor(0xFF374151);
        outer.addView(lbl);

        EditText etName = new EditText(this);
        String def = "Logs_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        etName.setText(def);
        etName.setHint("Enter a name for this log collection");
        outer.addView(etName);

        TextView info = new TextView(this);
        info.setText("This will save " + mLogs.size() + " log entries persistently, surviving app restarts.");
        info.setTextSize(11f); info.setTextColor(0xFF6B7280);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ip.topMargin = dp(8);
        outer.addView(info, ip);

        AlertDialog.Builder b = dialogBuilder();
        b.setTitle("Save Current Logs");
        b.setView(outer);
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
                mHandler.post(() -> toast("Logs Saved Permanently \u2014 \"" + finalName + "\" saved with " + snap.size() + " entries"));
            }).start();
        });
        b.setNegativeButton("Cancel", null);
        show(b);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Saved logs dialog  (React: showSavedLogsDialog; Load/Export/Delete per item)
    // ══════════════════════════════════════════════════════════════════════════

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
            b.setMessage("No saved log collections yet.\nSave current logs to create persistent backups.");
            b.setNegativeButton("Close", null);
            show(b);
            return;
        }

        ScrollView sv = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(8), dp(8), dp(8), dp(8));
        sv.addView(container);

        for (DevConsoleDatabaseHelper.SavedLogCollection col : cols) {
            container.addView(buildCollectionRow(col));
        }

        b.setView(sv);
        b.setNegativeButton("Close", null);
        show(b);
    }

    private View buildCollectionRow(DevConsoleDatabaseHelper.SavedLogCollection col) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setBackgroundResource(R.drawable.dev_console_log_item_bg);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(8);
        row.setLayoutParams(rp);

        // Name (bold)
        TextView tvName = new TextView(this);
        tvName.setText(col.name);
        tvName.setTextSize(14f);
        tvName.setTextColor(0xFF111827);
        tvName.setTypeface(tvName.getTypeface(), Typeface.BOLD);
        row.addView(tvName);

        // Subtitle: "N entries • Saved DATE"
        TextView tvSub = new TextView(this);
        tvSub.setText(col.totalEntries + " entries \u2022 Saved " + col.savedAt);
        tvSub.setTextSize(11f);
        tvSub.setTextColor(0xFF6B7280);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(2);
        row.addView(tvSub, sp);

        // Buttons: [Load] [⬇ Export] [🗑 Delete]
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(8);
        btns.setLayoutParams(bp);

        Button btnLoad = smallOutlineButton("Load");
        btnLoad.setOnClickListener(v -> loadSavedLogs(col));
        btns.addView(btnLoad);

        btns.addView(spacerView(dp(6)));

        Button btnExp = smallOutlineButton("\u2b07 Export");
        btnExp.setOnClickListener(v -> exportSavedLogs(col));
        btns.addView(btnExp);

        btns.addView(spacerView(dp(6)));

        Button btnDel = smallOutlineButton("\uD83D\uDDD1 Delete");
        btnDel.setTextColor(0xFFEF4444);
        btnDel.setOnClickListener(v -> {
            new Thread(() -> {
                mDb.deleteCollection(col.id);
                mHandler.post(() -> {
                    toast("Collection Deleted \u2014 \"" + col.name + "\" permanently deleted");
                    showSavedLogsDialog();
                });
            }).start();
        });
        btns.addView(btnDel);

        row.addView(btns);
        return row;
    }

    /** Matches React loadSavedLogs — auto-pauses and displays collection */
    private void loadSavedLogs(DevConsoleDatabaseHelper.SavedLogCollection col) {
        new Thread(() -> {
            List<DevConsoleLog> logs = mDb.getLogsForCollection(col.id);
            mHandler.post(() -> {
                mManager.setPaused(true);
                mBtnPause.setText("▶");
                mBtnPause.setTextColor(0xFFEAB308);
                mIsConnected = false;
                updateConnectionDot();

                mLogs.clear();
                mLogs.addAll(logs);
                Collections.reverse(mLogs);
                refreshAdapter();
                toast("Logs Loaded (Paused) \u2014 Loaded \"" + col.name + "\" with " + logs.size() + " entries. Tap \u25B6 to resume.");
            });
        }).start();
    }

    /** Matches React exportSavedLogs — exports a specific collection as styled HTML */
    private void exportSavedLogs(DevConsoleDatabaseHelper.SavedLogCollection col) {
        new Thread(() -> {
            try {
                List<DevConsoleLog> logs = mDb.getLogsForCollection(col.id);
                if (logs.isEmpty()) {
                    mHandler.post(() -> toast("Export Failed \u2014 Collection has no logs to export"));
                    return;
                }
                writeHtmlFile(col.name, logs);
            } catch (Exception e) {
                mHandler.post(() -> toast("Export Failed \u2014 " + e.getMessage()));
            }
        }).start();
    }

    private void writeHtmlFile(String title, List<DevConsoleLog> logs) throws IOException {
        String html = buildHtml(title, logs);
        String fname = "console_" + title.replaceAll("[^a-zA-Z0-9]", "_")
                + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".html";
        File dir = getExternalFilesDir("DevConsole");
        if (dir == null) dir = getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, fname);
        FileWriter fw = new FileWriter(f);
        fw.write(html); fw.close();
        final String path = f.getAbsolutePath();
        mHandler.post(() -> toast("Exported \u2014 Saved to: " + path));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error / Warning dialog  (React: showErrorDialog)
    // ══════════════════════════════════════════════════════════════════════════

    private void showErrorDialog() {
        List<DevConsoleLog> errLogs = new ArrayList<>();
        int errors = 0, warns = 0;
        for (DevConsoleLog l : mLogs) {
            if      (DevConsoleLog.LEVEL_ERROR.equals(l.level)) { errors++; errLogs.add(l); }
            else if (DevConsoleLog.LEVEL_WARN.equals(l.level))  { warns++;  errLogs.add(l); }
        }

        AlertDialog.Builder b = dialogBuilder();
        b.setTitle("Errors & Warnings  \u2014  E:" + errors + "  W:" + warns);

        if (errLogs.isEmpty()) {
            b.setMessage("No errors or warnings in current logs.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (DevConsoleLog l : errLogs) {
                sb.append("#").append(l.id).append("  [").append(l.level).append("]  ")
                  .append(l.timestamp).append("  ").append(l.source).append("\n")
                  .append(l.message).append("\n\n");
            }
            final String text = sb.toString().trim();
            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextSize(11f);
            tv.setTextColor(0xFF111827);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setPadding(dp(16), dp(12), dp(16), dp(12));
            ScrollView sv = new ScrollView(this);
            sv.addView(tv);
            b.setView(sv);
            b.setNeutralButton("Copy All", (d, w) -> {
                toClipboard(text);
                toast("Copied \u2014 " + errLogs.size() + " error/warn entries copied to clipboard");
            });
        }
        b.setNegativeButton("Close", null);
        show(b);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HTML export  (matches React exportSavedLogs HTML template closely)
    // ══════════════════════════════════════════════════════════════════════════

    private String buildHtml(String title, List<DevConsoleLog> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>").append(he(title)).append("</title>")
          .append("<style>")
          .append("*{box-sizing:border-box;margin:0;padding:0}")
          .append("body{background:#fff;color:#111827;font-family:ui-monospace,monospace;font-size:13px;padding:16px}")
          .append("h2{color:#111827;margin-bottom:16px;font-size:16px}")
          .append(".e{border:1px solid #E5E7EB;border-radius:6px;padding:10px;margin-bottom:8px}")
          .append(".e.ERROR{background:#FFF5F5;border-color:#FECACA}")
          .append(".e.WARN{background:#FEFCE8;border-color:#FEF08A}")
          .append(".e.INFO{background:#EFF6FF;border-color:#BFDBFE}")
          .append(".e.DEBUG,.e.LOG{background:#F9FAFB;border-color:#E5E7EB}")
          .append(".h{display:flex;align-items:center;gap:6px;margin-bottom:6px;flex-wrap:wrap}")
          .append(".n{font-size:10px;color:#6B7280;border:1px solid #D1D5DB;border-radius:4px;padding:1px 5px}")
          .append(".b{font-size:10px;color:#fff;border-radius:4px;padding:1px 6px;font-weight:600}")
          .append(".b.ERROR{background:#EF4444}.b.WARN{background:#EAB308}.b.INFO{background:#3B82F6}.b.DEBUG,.b.LOG{background:#6B7280}")
          .append(".src{font-size:10px;color:#374151;border:1px solid #D1D5DB;border-radius:4px;padding:1px 5px}")
          .append(".ts{font-size:10px;color:#9CA3AF}")
          .append(".msg{font-size:12px;line-height:1.5;word-break:break-all}")
          .append(".meta{margin-top:6px;background:#F3F4F6;padding:6px;border-radius:4px;font-size:10px;color:#6B7280}")
          .append("</style></head><body>")
          .append("<h2>").append(he(title)).append(" &nbsp;&mdash;&nbsp; ")
          .append(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(new Date()))
          .append(" &nbsp;(").append(logs.size()).append(" entries)</h2>");

        for (int i = 0; i < logs.size(); i++) {
            DevConsoleLog l = logs.get(i);
            String lvl = l.level.toUpperCase();
            sb.append("<div class='e ").append(lvl).append("'>")
              .append("<div class='h'>")
              .append("<span class='n'>#").append(i + 1).append("</span>")
              .append("<span class='b ").append(lvl).append("'>").append(lvl).append("</span>")
              .append("<span class='src'>").append(he(l.source)).append("</span>")
              .append("<span class='ts'>").append(he(l.timestamp)).append("</span>")
              .append("</div>")
              .append("<div class='msg'>").append(he(l.message)).append("</div>");
            if (l.metadata != null && !l.metadata.isEmpty())
                sb.append("<div class='meta'>").append(he(l.metadata)).append("</div>");
            sb.append("</div>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Log List Adapter
    // ══════════════════════════════════════════════════════════════════════════

    private class LogAdapter extends BaseAdapter {
        @Override public int     getCount()          { return mLogs.size(); }
        @Override public Object  getItem(int pos)    { return mLogs.get(pos); }
        @Override public long    getItemId(int pos)  { return mLogs.get(pos).id; }
        @Override public boolean hasStableIds()      { return true; }

        @Override
        public View getView(int pos, View cv, android.view.ViewGroup parent) {
            VH vh;
            if (cv == null) {
                cv = LayoutInflater.from(DevConsoleService.this)
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
            boolean selected  = mSelectedIds.contains(log.id);

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

            // Item background (matches React getLogLevelColor + selection ring)
            setItemBg(cv, log.level, selected);

            // Selection indicator (matches React selection checkmark box)
            if (mSelectionMode) {
                vh.selIndicator.setVisibility(View.VISIBLE);
                vh.selIndicator.setText(selected ? "\u2713" : "");
                vh.selIndicator.setTextColor(0xFFFFFFFF);
                vh.selIndicator.setBackgroundResource(
                        selected ? R.drawable.dev_console_check_checked : R.drawable.dev_console_check_unchecked);
            } else {
                vh.selIndicator.setVisibility(View.GONE);
            }

            // Per-entry copy button (always visible on mobile, matches React)
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
            tv.setBackground(bg);
            tv.setText(level.toUpperCase());
            tv.setTextColor(0xFFFFFFFF);
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

        /** Matches React getLogLevelColor background + selection ring */
        private void setItemBg(View v, String level, boolean selected) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(6));
            if (selected) {
                bg.setColor(0xFFEFF6FF);
                bg.setStroke(dp(2), 0xFF3B82F6);
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
                new android.view.ContextThemeWrapper(this, android.R.style.Theme_Material_Light_Dialog_Alert));
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
        btn.setText(label);
        btn.setTextSize(11f);
        btn.setTextColor(0xFF374151);
        btn.setBackgroundResource(R.drawable.dev_console_outline_btn);
        btn.setPadding(dp(10), 0, dp(10), 0);
        btn.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)));
        return btn;
    }

    private View spacerView(int widthPx) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(widthPx, 1));
        return v;
    }

    private void toClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("DevConsole", text));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static String he(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
