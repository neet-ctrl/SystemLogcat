package juloo.keyboard2.widget;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

import juloo.keyboard2.ClipboardHistoryService;
import juloo.keyboard2.R;

public class FloatingWidgetService extends Service {

    private WindowManager              windowManager;
    private View                       floatingView;
    private WindowManager.LayoutParams params;
    private View                       collapsedView;   // the single circle icon
    private View                       expandedView;    // the full panel

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null);

        collapsedView = floatingView.findViewById(R.id.collapse_view);
        expandedView  = floatingView.findViewById(R.id.expanded_container);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);

        // Start expanded showing the full clipboard panel
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                500,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        params.horizontalMargin = 0.05f;
        params.x = 0;
        params.y = 0;

        windowManager.addView(floatingView, params);

        setupClipboardList();
        setupButtons();
        setupDrag();
        setupResize();
    }

    // ── Buttons ──────────────────────────────────────────────────────────────

    private void setupButtons() {
        // Collapsed icon → expand the clipboard panel
        if (collapsedView != null) {
            collapsedView.setOnClickListener(v -> expand());
        }

        // Expanded header: iv_collapse → shrink to circle icon
        ImageView ivCollapse = floatingView.findViewById(R.id.iv_collapse);
        if (ivCollapse != null) {
            ivCollapse.setOnClickListener(v -> collapse());
        }

        // Expanded header: close button → dismiss widget entirely
        Button btnClose = floatingView.findViewById(R.id.btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> stopSelf());
        }

        // Expanded header: Dev Console button → launch DevConsoleService
        View btnConsole = floatingView.findViewById(R.id.btn_open_console);
        if (btnConsole != null) {
            btnConsole.setOnClickListener(v -> launchDevConsole());
        }
    }

    private void expand() {
        collapsedView.setVisibility(View.GONE);
        expandedView.setVisibility(View.VISIBLE);
        params.width  = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = 500;
        windowManager.updateViewLayout(floatingView, params);
    }

    private void collapse() {
        expandedView.setVisibility(View.GONE);
        collapsedView.setVisibility(View.VISIBLE);
        params.width  = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        windowManager.updateViewLayout(floatingView, params);
    }

    private void launchDevConsole() {
        juloo.keyboard2.devconsole.DevConsoleHelper.show(this);
    }

    // ── Clipboard list ───────────────────────────────────────────────────────

    private void setupClipboardList() {
        ListView listView = floatingView.findViewById(R.id.clip_list);
        if (listView == null) return;
        updateFloatingList();
        ClipboardHistoryService svc = ClipboardHistoryService.get_service(this);
        if (svc != null) {
            svc.set_on_clipboard_history_change(() -> {
                if (floatingView != null) floatingView.post(this::updateFloatingList);
            });
        }
    }

    private void updateFloatingList() {
        ListView listView = floatingView.findViewById(R.id.clip_list);
        if (listView == null) return;
        List<String> clips = ClipboardHistoryService.getRecentClips(this, 50);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, R.layout.clipboard_widget_item, R.id.clip_text, clips) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                String item = getItem(position);
                int[] colors = {0xFFE3F2FD, 0xFFF1F8E9, 0xFFFFF3E0, 0xFFF3E5F5, 0xFFE0F2F1};
                view.setBackgroundResource(R.drawable.bg_clip_item);
                view.getBackground().setTint(colors[position % colors.length]);
                TextView text = view.findViewById(R.id.clip_text);
                text.setText(item);
                text.setMaxLines(2);
                text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                view.findViewById(R.id.btn_copy).setOnClickListener(vv ->
                        ClipboardHistoryService.copyToClipboard(getContext(), item));
                return view;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                ClipboardHistoryService.copyToClipboard(this, clips.get(position)));
    }

    // ── Drag ─────────────────────────────────────────────────────────────────

    private void setupDrag() {
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int   ix, iy;
            private float tx, ty;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ix = params.x; iy = params.y;
                        tx = event.getRawX(); ty = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = ix + (int)(event.getRawX() - tx);
                        params.y = iy + (int)(event.getRawY() - ty);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });
    }

    // ── Resize ───────────────────────────────────────────────────────────────

    private void setupResize() {
        View resizeHandle = floatingView.findViewById(R.id.iv_resize);
        if (resizeHandle == null) return;
        resizeHandle.setOnTouchListener(new View.OnTouchListener() {
            private int   iw, ih;
            private float tx, ty;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        iw = (params.width == WindowManager.LayoutParams.MATCH_PARENT)
                                ? floatingView.getWidth() : params.width;
                        ih = params.height;
                        tx = event.getRawX(); ty = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
                        windowManager.getDefaultDisplay().getMetrics(dm);
                        params.width  = Math.max(300, Math.min(
                                iw + (int)(event.getRawX() - tx), dm.widthPixels));
                        params.height = Math.max(200,
                                ih + (int)(event.getRawY() - ty));
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
    }
}
