package juloo.keyboard2.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import juloo.keyboard2.R;
import juloo.keyboard2.ClipboardHistoryService;

public class ClipboardWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_COPY = "juloo.keyboard2.widget.ACTION_COPY";
    public static final String EXTRA_ITEM_TEXT = "juloo.keyboard2.widget.EXTRA_ITEM_TEXT";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            Intent intent = new Intent(context, ClipboardWidgetService.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.clipboard_widget);
            views.setRemoteAdapter(R.id.clip_list, intent);
            views.setEmptyView(R.id.clip_list, android.R.id.empty);

            Intent copyIntent = new Intent(context, ClipboardWidgetProvider.class);
            copyIntent.setAction(ACTION_COPY);
            PendingIntent copyPendingIntent = PendingIntent.getBroadcast(context, 0, copyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            views.setPendingIntentTemplate(R.id.clip_list, copyPendingIntent);

            Intent refreshIntent = new Intent(context, ClipboardWidgetProvider.class);
            refreshIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent);

            Intent floatingIntent = new Intent(context, FloatingWidgetService.class);
            PendingIntent floatingPendingIntent = PendingIntent.getService(context, 0, floatingIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.btn_floating, floatingPendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_COPY.equals(intent.getAction())) {
            String text = intent.getStringExtra(EXTRA_ITEM_TEXT);
            if (text != null) {
                ClipboardHistoryService.copyToClipboard(context, text);
            }
        }
        super.onReceive(context, intent);
    }
}