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
import juloo.keyboard2.SmartClipsService;

public class ClipboardWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_COPY       = "juloo.keyboard2.widget.ACTION_COPY";
    public static final String ACTION_TOGGLE_MODE = "juloo.keyboard2.widget.ACTION_TOGGLE_MODE";
    public static final String EXTRA_ITEM_TEXT   = "juloo.keyboard2.widget.EXTRA_ITEM_TEXT";

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

        Intent copyIntent = new Intent(context, ClipboardWidgetProvider.class);
        copyIntent.setAction(ACTION_COPY);
        PendingIntent copyPendingIntent = PendingIntent.getBroadcast(context, 0, copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.clip_list, copyPendingIntent);

        Intent refreshIntent = new Intent(context, ClipboardWidgetProvider.class);
        refreshIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId,
                refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent);

        Intent floatingIntent = new Intent(context, FloatingWidgetService.class);
        PendingIntent floatingPendingIntent = PendingIntent.getService(context, 0, floatingIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_floating, floatingPendingIntent);

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
        if (ACTION_COPY.equals(action)) {
            String text = intent.getStringExtra(EXTRA_ITEM_TEXT);
            if (text != null && !text.isEmpty()) {
                ClipboardHistoryService.copyToClipboard(context, text);
            }
        } else if (ACTION_TOGGLE_MODE.equals(action)) {
            SharedPreferences prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
            boolean current = prefs.getBoolean("smart_mode", false);
            boolean newMode = !current;

            if (newMode) {
                SmartClipsService svc = SmartClipsService.getInstance(context);
                if (svc.isLockEnabled() && !svc.isUnlocked() && svc.isPinSetup()) {
                    android.widget.Toast.makeText(context,
                            "Smart Clips is locked. Open app to unlock.", android.widget.Toast.LENGTH_SHORT).show();
                    super.onReceive(context, intent);
                    return;
                }
            }

            prefs.edit().putBoolean("smart_mode", newMode).apply();

            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            ComponentName widget = new ComponentName(context, ClipboardWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(widget);
            for (int id : ids) {
                updateWidget(context, mgr, id);
            }
        }
        super.onReceive(context, intent);
    }
}
