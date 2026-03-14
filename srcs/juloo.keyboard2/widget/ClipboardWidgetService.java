package juloo.keyboard2.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import juloo.keyboard2.R;
import juloo.keyboard2.ClipboardHistoryService;
import java.util.ArrayList;
import java.util.List;

public class ClipboardWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new ClipboardRemoteViewsFactory(this.getApplicationContext());
    }
}

class ClipboardRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private Context mContext;
    private List<String> mClips = new ArrayList<>();
    private final String[] mColors = {"#E3F2FD", "#F1F8E9", "#FFF3E0", "#F3E5F5", "#E0F2F1"};

    public ClipboardRemoteViewsFactory(Context context) {
        mContext = context;
    }

    @Override
    public void onCreate() {}

    @Override
    public void onDataSetChanged() {
        mClips = ClipboardHistoryService.getRecentClips(mContext, 15);
    }

    @Override
    public void onDestroy() {
        mClips.clear();
    }

    @Override
    public int getCount() {
        return mClips.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        RemoteViews rv = new RemoteViews(mContext.getPackageName(), R.layout.clipboard_widget_item);
        ClipboardHistoryService.HistoryEntry entry = null;
        ClipboardHistoryService service = ClipboardHistoryService.get_service(mContext);
        if (service != null) {
            List<ClipboardHistoryService.HistoryEntry> entries = service.get_history_entries();
            if (position < entries.size()) {
                entry = entries.get(position);
            }
        }

        String displayContent = "";
        String fullContent = "";
        if (entry != null) {
            fullContent = entry.content;
            if (entry.description != null && !entry.description.isEmpty()) {
                displayContent = entry.description;
            } else {
                // Get first two lines or a reasonable snippet
                String[] lines = fullContent.split("\n", 3);
                if (lines.length > 2) {
                    displayContent = lines[0] + "\n" + lines[1];
                } else {
                    displayContent = fullContent;
                }
            }
        }

        rv.setTextViewText(R.id.clip_text, displayContent);
        
        int colorIndex = position % mColors.length;
        rv.setInt(R.id.clip_container, "setBackgroundColor", Color.parseColor(mColors[colorIndex]));

        Bundle extras = new Bundle();
        extras.putString(ClipboardWidgetProvider.EXTRA_ITEM_TEXT, fullContent);
        Intent fillInIntent = new Intent();
        fillInIntent.putExtras(extras);
        rv.setOnClickFillInIntent(R.id.btn_copy, fillInIntent);

        return rv;
    }

    @Override
    public RemoteViews getLoadingView() { return null; }

    @Override
    public int getViewTypeCount() { return 1; }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public boolean hasStableIds() { return true; }
}