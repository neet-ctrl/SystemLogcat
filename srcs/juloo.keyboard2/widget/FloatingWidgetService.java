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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import java.util.List;
import juloo.keyboard2.R;
import juloo.keyboard2.ClipboardHistoryService;

public class FloatingWidgetService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private View collapsedView;
    private View expandedView;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null);

        // Initialize views
        collapsedView = floatingView.findViewById(R.id.collapse_view);
        expandedView = floatingView.findViewById(R.id.expanded_container);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                500, // Initial height
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.CENTER;
        params.horizontalMargin = 0.05f; // Add some margin from edges
        params.x = 0;
        params.y = 0;

        windowManager.addView(floatingView, params);

        setupClipboardList();

        Button closeBtn = floatingView.findViewById(R.id.btn_close);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> stopSelf());
        }

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
                            initialWidth = params.width;
                            if (initialWidth == WindowManager.LayoutParams.MATCH_PARENT) {
                                initialWidth = floatingView.getWidth();
                            }
                            initialHeight = params.height;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.width = initialWidth + (int) (event.getRawX() - initialTouchX);
                            params.height = initialHeight + (int) (event.getRawY() - initialTouchY);
                            
                            // Set minimum constraints
                            if (params.width < 300) params.width = 300;
                            if (params.height < 200) params.height = 200;
                            
                            // Ensure it doesn't exceed screen width
                            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
                            windowManager.getDefaultDisplay().getMetrics(dm);
                            if (params.width > dm.widthPixels) params.width = dm.widthPixels;
                            
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
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });
    }

    private void setupClipboardList() {
        ListView listView = floatingView.findViewById(R.id.clip_list);
        if (listView != null) {
            updateFloatingList();
            ClipboardHistoryService service = ClipboardHistoryService.get_service(this);
            if (service != null) {
                service.set_on_clipboard_history_change(() -> {
                    if (floatingView != null) {
                        floatingView.post(() -> updateFloatingList());
                    }
                });
            }
        }
    }

    private void updateFloatingList() {
        ListView listView = floatingView.findViewById(R.id.clip_list);
        if (listView == null) return;
        
        List<String> clips = ClipboardHistoryService.getRecentClips(this, 50);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.clipboard_widget_item, R.id.clip_text, clips) {
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
                
                view.findViewById(R.id.btn_copy).setOnClickListener(v -> {
                    ClipboardHistoryService.copyToClipboard(getContext(), item);
                });
                return view;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selected = clips.get(position);
            ClipboardHistoryService.copyToClipboard(this, selected);
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
    }
}
