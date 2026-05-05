package juloo.keyboard2.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.RemoteViews;
import juloo.keyboard2.R;
import juloo.keyboard2.ClipboardHistoryService;
import juloo.keyboard2.PinStore;
import juloo.keyboard2.SmartClipsService;

public class ClipboardWidgetProvider extends AppWidgetProvider {

    // ── Action constants ──────────────────────────────────────────────────────
    /** Unified item-click action; EXTRA_ACTION_TYPE distinguishes copy vs pin. */
    public static final String ACTION_ITEM_CLICK  = "juloo.keyboard2.widget.ACTION_ITEM_CLICK";
    public static final String ACTION_TOGGLE_MODE = "juloo.keyboard2.widget.ACTION_TOGGLE_MODE";

    // ── Extra keys ────────────────────────────────────────────────────────────
    /** "copy" | "pin_clip" | "pin_smart" */
    public static final String EXTRA_ACTION_TYPE = "juloo.keyboard2.widget.EXTRA_ACTION_TYPE";
    public static final String EXTRA_ITEM_TEXT   = "juloo.keyboard2.widget.EXTRA_ITEM_TEXT";
    /** Serial number for smart clip pin/unpin actions. */
    public static final String EXTRA_SERIAL      = "juloo.keyboard2.widget.EXTRA_SERIAL";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    public static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
        boolean smartMode = prefs.getBoolean("smart_mode", false);

        SmartClipsService svc = SmartClipsService.getInstance(context);
        boolean locked = svc.isLockEnabled() && !svc.isUnlocked() && svc.isPinSetup() && smartMode;

        Intent intent = new Intent(context, ClipboardWidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.putExtra("smart_mode", smartMode && !locked);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.clipboard_widget);
        views.setRemoteAdapter(R.id.clip_list, intent);
        views.setEmptyView(R.id.clip_list, android.R.id.empty);

        String title = (smartMode && !locked) ? "Smart Clips" : "Clipboard History";
        views.setTextViewText(R.id.tv_widget_title, title);

        // Mode toggle button state
        if (smartMode && !locked) {
            views.setTextViewText(R.id.btn_mode_smart, "✓ Smart");
            views.setTextColor(R.id.btn_mode_smart, 0xFFFFFFFF);
            views.setInt(R.id.btn_mode_smart, "setBackgroundResource", R.drawable.widget_btn_active);
        } else {
            views.setTextViewText(R.id.btn_mode_smart, "🔐 Smart");
            views.setTextColor(R.id.btn_mode_smart, 0xFFCDD5FF);
            views.setInt(R.id.btn_mode_smart, "setBackgroundResource", R.drawable.widget_btn_inactive);
        }

        // ── Unified item-click template (copy + pin share the same broadcast) ──
        Intent clickIntent = new Intent(context, ClipboardWidgetProvider.class);
        clickIntent.setAction(ACTION_ITEM_CLICK);
        PendingIntent clickTemplate = PendingIntent.getBroadcast(context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.clip_list, clickTemplate);

        // ── Refresh ───────────────────────────────────────────────────────────
        Intent refreshIntent = new Intent(context, ClipboardWidgetProvider.class);
        refreshIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId,
                refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent);

        // ── Floating widget launcher ──────────────────────────────────────────
        Intent floatingIntent = new Intent(context, FloatingWidgetService.class);
        PendingIntent floatingPendingIntent = PendingIntent.getService(context, 0, floatingIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_floating, floatingPendingIntent);

        // ── Mode toggle ───────────────────────────────────────────────────────
        Intent toggleIntent = new Intent(context, ClipboardWidgetProvider.class);
        toggleIntent.setAction(ACTION_TOGGLE_MODE);
        toggleIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent togglePendingIntent = PendingIntent.getBroadcast(context, appWidgetId + 1000,
                toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setOnClickPendingIntent(R.id.btn_mode_smart, togglePendingIntent);

        manager.updateAppWidget(appWidgetId, views);
        manager.notifyAppWidgetViewDataChanged(new int[]{appWidgetId}, R.id.clip_list);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (ACTION_ITEM_CLICK.equals(action)) {
            String type = intent.getStringExtra(EXTRA_ACTION_TYPE);
            String text = intent.getStringExtra(EXTRA_ITEM_TEXT);

            if ("copy".equals(type)) {
                // Standard clipboard copy
                if (text != null && !text.isEmpty()) {
                    SharedPreferences prefs =
                            context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
                    if (prefs.getBoolean("smart_mode", false)) {
                        ClipboardHistoryService.suppressNextClip();
                    }
                    ClipboardHistoryService.copyToClipboard(context, text);
                }

            } else if ("pin_clip".equals(type)) {
                // Toggle pin on a normal clipboard entry
                if (text != null && !text.isEmpty()) {
                    ClipboardHistoryService.togglePin(text);
                    refreshAllWidgets(context);
                }

            } else if ("pin_smart".equals(type)) {
                // Toggle pin on a smart clip (serial only — content/serial unchanged)
                int serial = intent.getIntExtra(EXTRA_SERIAL, -1);
                if (serial >= 0) {
                    PinStore.toggleSmartPin(context, serial);
                    refreshAllWidgets(context);
                }
            }

        } else if (ACTION_TOGGLE_MODE.equals(action)) {
            SharedPreferences prefs =
                    context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
            boolean current = prefs.getBoolean("smart_mode", false);
            boolean newMode = !current;

            if (newMode) {
                SmartClipsService smartSvc = SmartClipsService.getInstance(context);
                if (smartSvc.isLockEnabled() && !smartSvc.isUnlocked() && smartSvc.isPinSetup()) {
                    android.widget.Toast.makeText(context,
                            "Smart Clips is locked. Open app to unlock.",
                            android.widget.Toast.LENGTH_SHORT).show();
                    super.onReceive(context, intent);
                    return;
                }
            }

            prefs.edit().putBoolean("smart_mode", newMode).apply();
            refreshAllWidgets(context);
        }

        super.onReceive(context, intent);
    }

    // ── Helper: refresh all widget instances ──────────────────────────────────

    private static void refreshAllWidgets(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, ClipboardWidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(widget);
        for (int id : ids) {
            updateWidget(context, mgr, id);
        }
    }
}
