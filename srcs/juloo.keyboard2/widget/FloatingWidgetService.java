package juloo.keyboard2.widget;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.R;
import juloo.keyboard2.ClipboardHistoryService;
import juloo.keyboard2.SmartClipsService;

public class FloatingWidgetService extends Service
        implements SmartClipsService.OnSmartClipsChangeListener,
                   ClipboardHistoryService.OnClipboardHistoryChange {

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private View collapsedView;
    private View expandedView;
    private boolean showSmartClips = false;
    private SmartClipsService smartClipsService;
    private TextView tvTitle;
    private Button btnModeHistory;
    private Button btnModeSmart;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        smartClipsService = SmartClipsService.getInstance(this);
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null);

        collapsedView = floatingView.findViewById(R.id.collapse_view);
        expandedView  = floatingView.findViewById(R.id.expanded_container);
        tvTitle       = floatingView.findViewById(R.id.tv_widget_title);
        btnModeHistory = floatingView.findViewById(R.id.btn_mode_history);
        btnModeSmart   = floatingView.findViewById(R.id.btn_mode_smart);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                500,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        params.x = 0;
        params.y = 0;

        windowManager.addView(floatingView, params);

        setupModeButtons();
        setupListeners();
        updateList();

        ClipboardHistoryService clipService = ClipboardHistoryService.get_service(this);
        if (clipService != null) clipService.set_on_clipboard_history_change(this);
        smartClipsService.addListener(this);
    }

    private void setupModeButtons() {
        if (btnModeHistory == null || btnModeSmart == null) return;
        btnModeHistory.setOnClickListener(v -> {
            if (showSmartClips) {
                showSmartClips = false;
                refreshModeUI();
                updateList();
            }
        });
        btnModeSmart.setOnClickListener(v -> {
            if (!showSmartClips) {
                if (smartClipsService.isLockEnabled() && !smartClipsService.isUnlocked()) {
                    if (smartClipsService.isPinSetup()) {
                        showPinDialogInWidget();
                        return;
                    }
                }
                showSmartClips = true;
                refreshModeUI();
                updateList();
            }
        });
    }

    private void refreshModeUI() {
        if (tvTitle != null)
            tvTitle.setText(showSmartClips ? "Smart Clips" : "Clipboard");
        if (btnModeHistory != null) {
            btnModeHistory.setBackgroundColor(showSmartClips ? 0xFFE3F2FD : 0xFF1565C0);
            btnModeHistory.setTextColor(showSmartClips ? 0xFF1565C0 : 0xFFFFFFFF);
        }
        if (btnModeSmart != null) {
            btnModeSmart.setBackgroundColor(showSmartClips ? 0xFF1565C0 : 0xFFE3F2FD);
            btnModeSmart.setTextColor(showSmartClips ? 0xFFFFFFFF : 0xFF1565C0);
        }
    }

    private void showPinDialogInWidget() {
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        windowManager.updateViewLayout(floatingView, params);

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        EditText pinInput = new EditText(this);
        pinInput.setHint("Enter PIN");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setPadding(48, 24, 48, 24);
        builder.setTitle("Smart Clips - Enter PIN")
                .setView(pinInput)
                .setPositiveButton("Unlock", (d, w) -> {
                    if (smartClipsService.verifyPin(pinInput.getText().toString().trim())) {
                        smartClipsService.unlock10Min();
                        showSmartClips = true;
                        refreshModeUI();
                        updateList();
                    } else {
                        Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
                    }
                    restoreWidgetFlags();
                })
                .setNegativeButton("Cancel", (d, w) -> restoreWidgetFlags());
        android.app.AlertDialog dlg = builder.create();
        dlg.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE);
        dlg.show();
    }

    private void restoreWidgetFlags() {
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (floatingView != null) windowManager.updateViewLayout(floatingView, params);
    }

    private void setupListeners() {
        Button closeBtn = floatingView.findViewById(R.id.btn_close);
        if (closeBtn != null) closeBtn.setOnClickListener(v -> stopSelf());

        ImageView collapseBtn = floatingView.findViewById(R.id.iv_collapse);
        if (collapseBtn != null) {
            collapseBtn.setOnClickListener(v -> {
                expandedView.setVisibility(View.GONE);
                collapsedView.setVisibility(View.VISIBLE);
                params.width = WindowManager.LayoutParams.WRAP_CONTENT;
                params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                windowManager.updateViewLayout(floatingView, params);
            });
        }

        collapsedView.setOnClickListener(v -> {
            collapsedView.setVisibility(View.GONE);
            expandedView.setVisibility(View.VISIBLE);
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = 500;
            windowManager.updateViewLayout(floatingView, params);
        });

        View resizeHandle = floatingView.findViewById(R.id.iv_resize);
        if (resizeHandle != null) {
            resizeHandle.setOnTouchListener(new View.OnTouchListener() {
                private int initialWidth, initialHeight;
                private float initialTouchX, initialTouchY;
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialWidth = params.width == WindowManager.LayoutParams.MATCH_PARENT
                                    ? floatingView.getWidth() : params.width;
                            initialHeight = params.height;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.width  = Math.max(300, initialWidth  + (int)(event.getRawX() - initialTouchX));
                            params.height = Math.max(200, initialHeight + (int)(event.getRawY() - initialTouchY));
                            windowManager.updateViewLayout(floatingView, params);
                            return true;
                    }
                    return false;
                }
            });
        }

        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int)(event.getRawX() - initialTouchX);
                        params.y = initialY + (int)(event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });
    }

    private void updateList() {
        if (floatingView == null) return;
        floatingView.post(() -> {
            ListView listView = floatingView.findViewById(R.id.clip_list);
            if (listView == null) return;

            if (showSmartClips) {
                List<SmartClipsService.SmartClip> clips = smartClipsService.getClipsForWidget();
                SmartClipAdapter adapter = new SmartClipAdapter(this, clips);
                listView.setAdapter(adapter);
            } else {
                List<String> clips = ClipboardHistoryService.getRecentClips(this, 50);
                int[] colors = {0xFFE3F2FD, 0xFFF1F8E9, 0xFFFFF3E0, 0xFFF3E5F5, 0xFFE0F2F1};
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.clipboard_widget_item, R.id.clip_text, clips) {
                    @Override
                    public View getView(int position, View convertView, android.view.ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        view.setBackgroundColor(colors[position % colors.length]);
                        TextView text = view.findViewById(R.id.clip_text);
                        String item = getItem(position);
                        text.setText(item);
                        text.setMaxLines(2);
                        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        view.findViewById(R.id.btn_copy).setOnClickListener(v ->
                                ClipboardHistoryService.copyToClipboard(getContext(), item));
                        return view;
                    }
                };
                listView.setAdapter(adapter);
                listView.setOnItemClickListener((parent, view, position, id) ->
                        ClipboardHistoryService.copyToClipboard(this, clips.get(position)));
            }
        });
    }

    @Override
    public void on_clipboard_history_change() {
        if (!showSmartClips && floatingView != null) updateList();
    }

    @Override
    public void onSmartClipsChanged() {
        if (showSmartClips && floatingView != null) updateList();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        smartClipsService.removeListener(this);
        if (floatingView != null) windowManager.removeView(floatingView);
    }

    static class SmartClipAdapter extends ArrayAdapter<SmartClipsService.SmartClip> {
        private final Context ctx;
        SmartClipAdapter(Context ctx, List<SmartClipsService.SmartClip> clips) {
            super(ctx, 0, clips);
            this.ctx = ctx;
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            SmartClipsService.SmartClip clip = getItem(position);
            LinearLayout layout = new LinearLayout(ctx);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setPadding(16, 12, 16, 12);
            int[] colors = {0xFFE3F2FD, 0xFFF1F8E9, 0xFFFFF3E0, 0xFFF3E5F5, 0xFFE0F2F1};
            layout.setBackgroundColor(colors[position % colors.length]);

            TextView serial = new TextView(ctx);
            serial.setText("#" + clip.serial + " ");
            serial.setTextSize(12);
            serial.setTextColor(0xFF1565C0);
            serial.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            layout.addView(serial);

            TextView text = new TextView(ctx);
            String display = clip.locked ? "●●●● Locked" : clip.content;
            text.setText(display);
            text.setTextColor(clip.locked ? 0xFFAAAAAA : 0xFF212121);
            text.setTextSize(13);
            text.setMaxLines(2);
            text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            layout.addView(text, lp);

            Button copyBtn = new Button(ctx);
            copyBtn.setText("Copy");
            copyBtn.setTextSize(11);
            copyBtn.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            copyBtn.setTextColor(0xFF1565C0);
            copyBtn.setPadding(12, 4, 12, 4);
            copyBtn.setOnClickListener(v -> {
                if (clip.locked) {
                    Toast.makeText(ctx, "Clip is locked. Open app to view.", Toast.LENGTH_SHORT).show();
                } else {
                    ClipboardHistoryService.copyToClipboard(ctx, clip.content);
                }
            });
            layout.addView(copyBtn);
            return layout;
        }
    }
}
