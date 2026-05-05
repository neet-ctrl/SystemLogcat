package juloo.keyboard2.widget;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.graphics.PixelFormat;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.R;
import juloo.keyboard2.ClipboardHistoryService;
import juloo.keyboard2.SmartClipsService;

public class FloatingWidgetService extends Service
        implements SmartClipsService.OnSmartClipsChangeListener,
                   ClipboardHistoryService.OnClipboardHistoryChange {

    // ── Window manager ────────────────────────────────────────────────────────
    private WindowManager windowManager;
    private View          floatingView;
    private WindowManager.LayoutParams params;

    // ── Sub-views ─────────────────────────────────────────────────────────────
    private View     collapsedView;
    private View     expandedView;
    private TextView tvTitle;
    private Button   btnModeHistory;
    private Button   btnModeSmart;
    private ListView listView;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean showSmartClips = false;
    private boolean _isPinned      = false;
    private SmartClipsService smartClipsService;

    // ── Glass palette ─────────────────────────────────────────────────────────
    private static final int COL_BG        = 0xF00A0E1A;
    private static final int COL_SURFACE   = 0xCC1E2040;
    private static final int COL_SURFACE_B = 0xCC151832;
    private static final int COL_PRIMARY   = 0xFF4F46E5;
    private static final int COL_PRIMARY_L = 0xFF818CF8;
    private static final int COL_TXT       = 0xFFE2E8F0;
    private static final int COL_TXT_SEC   = 0xFF94A3B8;
    private static final int COL_TXT_HINT  = 0xFF475569;
    private static final int COL_GREEN     = 0xFF059669;
    private static final int COL_RED       = 0xFFFF6B6B;
    private static final int COL_INACTIVE  = 0xAA252947;
    private static final int COL_BORDER    = 0x553A3F6E;

    @Override public IBinder onBind(Intent intent) { return null; }

    // ══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void onCreate() {
        super.onCreate();
        smartClipsService = SmartClipsService.getInstance(this);

        // Inflate the redesigned glass layout
        floatingView  = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null);
        collapsedView = floatingView.findViewById(R.id.collapse_view);
        expandedView  = floatingView.findViewById(R.id.expanded_container);
        tvTitle       = floatingView.findViewById(R.id.tv_widget_title);
        btnModeHistory = floatingView.findViewById(R.id.btn_mode_history);
        btnModeSmart   = floatingView.findViewById(R.id.btn_mode_smart);
        listView       = floatingView.findViewById(R.id.clip_list);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                520,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;

        windowManager.addView(floatingView, params);

        // Apply programmatic glass styling
        applyGlassStyling();

        setupModeButtons();
        setupListeners();
        updateList();

        ClipboardHistoryService clipService = ClipboardHistoryService.get_service(this);
        if (clipService != null) clipService.set_on_clipboard_history_change(this);
        smartClipsService.addListener(this);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Glass styling (applied once after inflate)
    // ══════════════════════════════════════════════════════════════════════════

    private void applyGlassStyling() {
        float dp = getResources().getDisplayMetrics().density;

        // Collapsed bubble — always apply thin 1dp ring
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setShape(GradientDrawable.OVAL);
        bubbleBg.setColor(0xFF1E1B4B);
        bubbleBg.setStroke((int)(1 * dp), COL_PRIMARY);
        collapsedView.setBackground(bubbleBg);
        if (Build.VERSION.SDK_INT >= 21) collapsedView.setElevation(14 * dp);

        // Expanded glass panel
        if (Build.VERSION.SDK_INT >= 21) expandedView.setElevation(16 * dp);

        // iv_collapse is now a Button in the new XML — already styled via drawable
        // Style the collapse button text colour if needed
        View collapseBtn = floatingView.findViewById(R.id.iv_collapse);
        if (collapseBtn instanceof Button) {
            ((Button) collapseBtn).setTextColor(COL_TXT);
        }

        // Resize grip text colour
        View resizeView = floatingView.findViewById(R.id.iv_resize);
        if (resizeView instanceof TextView) {
            ((TextView) resizeView).setTextColor(COL_PRIMARY_L & 0x88FFFFFF);
        }

        // Title text colour
        if (tvTitle != null) tvTitle.setTextColor(COL_TXT);

        // ListView background transparent
        if (listView != null) {
            listView.setBackgroundColor(0x00000000);
            listView.setDivider(null);
            listView.setDividerHeight((int)(6 * dp));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Mode buttons
    // ══════════════════════════════════════════════════════════════════════════

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

        refreshModeUI();
    }

    private void refreshModeUI() {
        float dp = getResources().getDisplayMetrics().density;

        if (tvTitle != null) {
            tvTitle.setText(showSmartClips ? "🔐 Smart Clips" : "📋 Clipboard");
        }

        // Active tab: filled indigo pill
        GradientDrawable activeBg = new GradientDrawable();
        activeBg.setColor(COL_PRIMARY);
        activeBg.setCornerRadius(20 * dp);

        // Inactive tab: dark outlined pill
        GradientDrawable inactiveBg = new GradientDrawable();
        inactiveBg.setColor(COL_INACTIVE);
        inactiveBg.setCornerRadius(20 * dp);
        inactiveBg.setStroke((int)(1 * dp), COL_BORDER);

        if (btnModeHistory != null) {
            btnModeHistory.setBackground(showSmartClips ? inactiveBg : activeBg);
            btnModeHistory.setTextColor(showSmartClips ? COL_TXT_SEC : 0xFFFFFFFF);
        }
        if (btnModeSmart != null) {
            // Need separate drawable instances
            GradientDrawable smBg = new GradientDrawable();
            smBg.setColor(showSmartClips ? COL_PRIMARY : COL_INACTIVE);
            smBg.setCornerRadius(20 * dp);
            if (!showSmartClips) smBg.setStroke((int)(1 * dp), COL_BORDER);
            btnModeSmart.setBackground(smBg);
            btnModeSmart.setTextColor(showSmartClips ? 0xFFFFFFFF : COL_TXT_SEC);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Touch / gesture listeners
    // ══════════════════════════════════════════════════════════════════════════

    private void setupListeners() {
        // Close button
        View closeBtn = floatingView.findViewById(R.id.btn_close);
        if (closeBtn != null) closeBtn.setOnClickListener(v -> stopSelf());

        // Collapse to bubble
        View collapseBtn = floatingView.findViewById(R.id.iv_collapse);
        if (collapseBtn != null) {
            collapseBtn.setOnClickListener(v -> {
                expandedView.setVisibility(View.GONE);
                collapsedView.setVisibility(View.VISIBLE);
                params.width  = WindowManager.LayoutParams.WRAP_CONTENT;
                params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                windowManager.updateViewLayout(floatingView, params);
            });
        }

        // Tap collapsed bubble → expand  (uses smart touch that separates tap vs drag)
        collapsedView.setOnTouchListener(makeBubbleTouchListener());

        // Settings button → open AppSettingsActivity
        View settingsBtn = floatingView.findViewById(R.id.btn_widget_settings);
        if (settingsBtn != null) {
            settingsBtn.setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(
                        this, juloo.keyboard2.AppSettingsActivity.class);
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            });
        }

        // Pin-to-top toggle
        Button pinBtn = floatingView.findViewById(R.id.btn_pin_top);
        if (pinBtn != null) {
            pinBtn.setOnClickListener(v -> {
                _isPinned = !_isPinned;
                applyPinState(pinBtn);
            });
        }

        // Entire header bar is the drag area (drag_handle pill is just visual)
        View headerBar = floatingView.findViewById(R.id.header_bar);
        if (headerBar != null) {
            headerBar.setOnTouchListener(makeDragListener());
        }

        // Resize grip (bottom-right ⤡) → resize window
        View resizeGrip = floatingView.findViewById(R.id.iv_resize);
        if (resizeGrip != null) {
            resizeGrip.setOnTouchListener(makeResizeListener());
        }
    }

    /** Pin / unpin the widget so it stays above the soft keyboard. */
    private void applyPinState(Button pinBtn) {
        if (_isPinned) {
            // Pinned: snap to top, FLAG_LAYOUT_IN_SCREEN so keyboard can't push it
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.y       = 0;
            params.flags   = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            pinBtn.setTextColor(COL_PRIMARY_L);
            GradientDrawable pinBg = new GradientDrawable();
            float dp = getResources().getDisplayMetrics().density;
            pinBg.setColor(COL_PRIMARY & 0x55FFFFFF);
            pinBg.setCornerRadius(8 * dp);
            pinBtn.setBackground(pinBg);
        } else {
            // Unpinned: restore free-floating mode
            params.gravity = Gravity.CENTER;
            params.flags   = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            pinBtn.setTextColor(0x88CDD5FF);
            pinBtn.setBackgroundResource(R.drawable.widget_btn_inactive);
        }
        if (floatingView != null) windowManager.updateViewLayout(floatingView, params);
    }

    /**
     * Smart touch listener for the collapsed bubble.
     * - Small movement (< 8dp) on ACTION_UP → treated as a tap → expands the widget.
     * - Larger movement → treated as a drag → moves the window.
     */
    private View.OnTouchListener makeBubbleTouchListener() {
        return new View.OnTouchListener() {
            private int   initialX, initialY;
            private float touchX, touchY;
            private boolean dragging;
            private final float TAP_SLOP = 8 * getResources().getDisplayMetrics().density;

            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        touchX   = e.getRawX(); touchY = e.getRawY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - touchX;
                        float dy = e.getRawY() - touchY;
                        if (!dragging && (Math.abs(dx) > TAP_SLOP || Math.abs(dy) > TAP_SLOP)) {
                            dragging = true;
                        }
                        if (dragging) {
                            params.x = initialX + (int) dx;
                            params.y = initialY + (int) dy;
                            windowManager.updateViewLayout(floatingView, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) {
                            // It was a tap — expand the widget
                            collapsedView.setVisibility(View.GONE);
                            expandedView.setVisibility(View.VISIBLE);
                            params.width  = WindowManager.LayoutParams.MATCH_PARENT;
                            params.height = 520;
                            windowManager.updateViewLayout(floatingView, params);
                        }
                        return true;
                }
                return false;
            }
        };
    }

    private View.OnTouchListener makeDragListener() {
        return new View.OnTouchListener() {
            private int   initialX, initialY;
            private float touchX, touchY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        touchX = e.getRawX(); touchY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int)(e.getRawX() - touchX);
                        params.y = initialY + (int)(e.getRawY() - touchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        };
    }

    private View.OnTouchListener makeResizeListener() {
        return new View.OnTouchListener() {
            private int   initW, initH;
            private float touchX, touchY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initW = params.width == WindowManager.LayoutParams.MATCH_PARENT
                                ? floatingView.getWidth() : params.width;
                        initH  = params.height;
                        touchX = e.getRawX(); touchY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.width  = Math.max(320, initW  + (int)(e.getRawX() - touchX));
                        params.height = Math.max(220, initH + (int)(e.getRawY() - touchY));
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // List update
    // ══════════════════════════════════════════════════════════════════════════

    private void updateList() {
        if (floatingView == null || listView == null) return;
        floatingView.post(() -> {
            if (showSmartClips) {
                List<SmartClipsService.SmartClip> clips =
                        smartClipsService.getClipsForWidget();
                // Sort pinned smart clips to the top — serial numbers never change
                java.util.Set<Integer> pins = juloo.keyboard2.PinStore.getSmartPins(this);
                clips.sort((a, b) -> {
                    boolean pa = pins.contains(a.serial), pb = pins.contains(b.serial);
                    if (pa != pb) return pa ? -1 : 1;
                    return Integer.compare(a.serial, b.serial);
                });
                listView.setAdapter(new GlassSmartClipAdapter(this, clips, this::updateList));
                // Tap card → paste directly; locked clips show a toast instead
                listView.setOnItemClickListener((p, v, pos, id) -> {
                    SmartClipsService.SmartClip clip = clips.get(pos);
                    if (clip.locked) {
                        Toast.makeText(this, "Clip locked — open app to view",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        // Smart-clip pastes go via IME (send_text), so they
                        // never touch the system clipboard — no suppress needed.
                        boolean pasted = ClipboardHistoryService.pasteOrCopy(this, clip.content);
                        Toast.makeText(this,
                                pasted ? "Pasted!" : "Copied! (open a text field to paste directly)",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                // Sort clipboard clips pinned-first, then load into adapter
                ClipboardHistoryService svc = ClipboardHistoryService.get_service(this);
                List<ClipboardHistoryService.HistoryEntry> entries =
                        svc != null ? svc.get_history_entries() : new java.util.ArrayList<>();
                entries.sort((a, b) -> {
                    if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
                    return 0;
                });
                List<String> clips = new java.util.ArrayList<>();
                for (ClipboardHistoryService.HistoryEntry e : entries) clips.add(e.content);
                if (clips.size() > 50) clips = clips.subList(0, 50);
                final List<String> finalClips = clips;
                listView.setAdapter(new GlassClipboardAdapter(this, finalClips));
                // Tap card → paste directly into the active text field.
                // Falls back to clipboard copy when no keyboard connection is active.
                listView.setOnItemClickListener((p, v, pos, id) -> {
                    boolean pasted = ClipboardHistoryService.pasteOrCopy(this, finalClips.get(pos));
                    Toast.makeText(this,
                            pasted ? "Pasted!" : "Copied! (open a text field to paste directly)",
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PIN dialog (overlay)
    // ══════════════════════════════════════════════════════════════════════════

    private void showPinDialogInWidget() {
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        windowManager.updateViewLayout(floatingView, params);

        EditText pinInput = new EditText(this);
        pinInput.setHint("Enter PIN");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setPadding(48, 24, 48, 24);

        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setTitle("🔐 Smart Clips — Enter PIN")
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
                .setNegativeButton("Cancel", (d, w) -> restoreWidgetFlags())
                .create();

        dlg.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE);
        dlg.show();
    }

    private void restoreWidgetFlags() {
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (floatingView != null) windowManager.updateViewLayout(floatingView, params);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Listener callbacks
    // ══════════════════════════════════════════════════════════════════════════

    @Override public void on_clipboard_history_change() {
        if (!showSmartClips && floatingView != null) updateList();
    }

    @Override public void onSmartClipsChanged() {
        if (showSmartClips && floatingView != null) updateList();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        smartClipsService.removeListener(this);
        if (floatingView != null) windowManager.removeView(floatingView);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Glass Clipboard adapter (programmatic)
    // ══════════════════════════════════════════════════════════════════════════

    static class GlassClipboardAdapter extends ArrayAdapter<String> {
        private final Context ctx;
        private final float   dp;

        GlassClipboardAdapter(Context ctx, List<String> items) {
            super(ctx, 0, items);
            this.ctx = ctx;
            this.dp  = ctx.getResources().getDisplayMetrics().density;
        }

        @Override
        public View getView(int pos, View convertView, android.view.ViewGroup parent) {
            String text = getItem(pos);

            // Card
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(10), dp(8), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(pos % 2 == 0 ? COL_SURFACE : COL_SURFACE_B);
            bg.setCornerRadius(dp(12));
            card.setBackground(bg);
            if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));

            // Content
            TextView tv = new TextView(ctx);
            tv.setText(text);
            tv.setTextColor(COL_TXT);
            tv.setTextSize(12);
            tv.setMaxLines(2);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setLineSpacing(0, 1.2f);
            card.addView(tv, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            // ⌨ Paste button — injects text directly into the active field via IME
            Button paste = makePasteBtn(ctx, dp);
            paste.setOnClickListener(v -> {
                boolean pasted = ClipboardHistoryService.pasteOrCopy(ctx, text);
                android.widget.Toast.makeText(ctx,
                        pasted ? "Pasted!" : "Copied! (focus a text field first)",
                        android.widget.Toast.LENGTH_SHORT).show();
            });
            card.addView(paste);

            // 📋 Copy button (circular green)
            Button copy = makeCopyBtn(ctx, dp);
            copy.setOnClickListener(v ->
                    ClipboardHistoryService.copyToClipboard(ctx, text));
            card.addView(copy);

            // 📌/📍 Pin button — toggles pinned-to-top for this clip
            boolean pinned = ClipboardHistoryService.isPinned(text);
            Button pinBtn = makePinBtn(ctx, dp, pinned);
            pinBtn.setOnClickListener(v -> ClipboardHistoryService.togglePin(text));
            card.addView(pinBtn);

            return card;
        }

        private int dp(int v) { return (int)(v * dp); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Glass Smart Clip adapter (programmatic)
    // ══════════════════════════════════════════════════════════════════════════

    static class GlassSmartClipAdapter extends ArrayAdapter<SmartClipsService.SmartClip> {
        private final Context  ctx;
        private final float    dp;
        private final Runnable refresh;

        GlassSmartClipAdapter(Context ctx, List<SmartClipsService.SmartClip> clips,
                              Runnable refresh) {
            super(ctx, 0, clips);
            this.ctx     = ctx;
            this.dp      = ctx.getResources().getDisplayMetrics().density;
            this.refresh = refresh;
        }

        @Override
        public View getView(int pos, View convertView, android.view.ViewGroup parent) {
            SmartClipsService.SmartClip clip = getItem(pos);

            // Card
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(10), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(pos % 2 == 0 ? COL_SURFACE : COL_SURFACE_B);
            bg.setCornerRadius(dp(12));
            card.setBackground(bg);
            if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));

            // Top row: serial chip + content + copy button
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // #N serial chip
            TextView serial = new TextView(ctx);
            serial.setText("#" + clip.serial);
            serial.setTextSize(9);
            serial.setTextColor(COL_PRIMARY_L);
            serial.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable sBg = new GradientDrawable();
            sBg.setColor(COL_INACTIVE);
            sBg.setCornerRadius(dp(12));
            sBg.setStroke((int)(0.8f * dp), COL_BORDER);
            serial.setBackground(sBg);
            serial.setPadding(dp(6), dp(2), dp(6), dp(2));
            row.addView(serial);

            // Content text
            String display = clip.locked ? "⬛  ⬛  ⬛   locked"
                    : (clip.description.isEmpty() ? clip.content : clip.description);
            TextView tv = new TextView(ctx);
            tv.setText(display);
            tv.setTextColor(clip.locked ? COL_TXT_HINT : COL_TXT);
            tv.setTextSize(12);
            tv.setMaxLines(2);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvLp.setMargins(dp(8), 0, 0, 0);
            row.addView(tv, tvLp);

            // ⌨ Paste button — only shown for unlocked clips; injects via IME
            if (!clip.locked) {
                Button paste = makePasteBtn(ctx, dp);
                paste.setOnClickListener(v -> {
                    boolean pasted = ClipboardHistoryService.pasteOrCopy(ctx, clip.content);
                    Toast.makeText(ctx,
                            pasted ? "Pasted!" : "Copied! (focus a text field first)",
                            Toast.LENGTH_SHORT).show();
                });
                row.addView(paste);
            }

            // 📋 Copy button
            Button copy = makeCopyBtn(ctx, dp);
            copy.setOnClickListener(v -> {
                if (clip.locked) {
                    Toast.makeText(ctx, "Clip locked — open app to view",
                            Toast.LENGTH_SHORT).show();
                } else {
                    ClipboardHistoryService.suppressNextClip();
                    ClipboardHistoryService.copyToClipboard(ctx, clip.content);
                    Toast.makeText(ctx, "Copied!", Toast.LENGTH_SHORT).show();
                }
            });
            row.addView(copy);

            // 📌/📍 Pin button — display-only; serial/content unchanged
            boolean pinned = juloo.keyboard2.PinStore.isSmartPinned(ctx, clip.serial);
            Button pinBtn = makePinBtn(ctx, dp, pinned);
            pinBtn.setOnClickListener(v -> {
                if (clip.locked) {
                    Toast.makeText(ctx, "Clip locked — open app to view",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                juloo.keyboard2.PinStore.toggleSmartPin(ctx, clip.serial);
                if (refresh != null) refresh.run();
            });
            row.addView(pinBtn);
            card.addView(row);

            // Keyword pill (optional row)
            if (!clip.keyword.isEmpty()) {
                TextView kw = new TextView(ctx);
                kw.setText("{" + clip.keyword + "}");
                kw.setTextSize(9);
                kw.setTextColor(0xFF818CF8);
                LinearLayout.LayoutParams kwLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                kwLp.setMargins(0, dp(4), 0, 0);
                kw.setLayoutParams(kwLp);
                card.addView(kw);
            }

            return card;
        }

        private int dp(int v) { return (int)(v * dp); }
    }

    // ── Shared factory ────────────────────────────────────────────────────────

    private static Button makeCopyBtn(Context ctx, float dp) {
        Button b = new Button(ctx);
        b.setText("📋");
        b.setTextSize(14);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0); b.setMinHeight(0);
        int sz = (int)(34 * dp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
        lp.setMargins((int)(8 * dp), 0, 0, 0);
        b.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(COL_GREEN);
        b.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(2 * dp);
        return b;
    }

    /** Circular paste button — injects text directly via the IME connection. */
    private static Button makePasteBtn(Context ctx, float dp) {
        Button b = new Button(ctx);
        b.setText("⌨");
        b.setTextSize(13);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0); b.setMinHeight(0);
        int sz = (int)(34 * dp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
        lp.setMargins((int)(5 * dp), 0, 0, 0);
        b.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x44818CF8);  // soft indigo — distinct from green copy and indigo pin
        b.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(2 * dp);
        return b;
    }

    /** Circular pin toggle button — filled 📌 when pinned, outline 📍 when not. */
    private static Button makePinBtn(Context ctx, float dp, boolean pinned) {
        Button b = new Button(ctx);
        b.setText(pinned ? "📌" : "📍");
        b.setTextSize(13);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0); b.setMinHeight(0);
        int sz = (int)(34 * dp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
        lp.setMargins((int)(5 * dp), 0, 0, 0);
        b.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(pinned ? 0x554F46E5 : 0x22818CF8);  // indigo tint — bright when pinned
        b.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(2 * dp);
        return b;
    }
}
