package juloo.keyboard2.widget;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import juloo.keyboard2.R;
import juloo.keyboard2.ClipboardHistoryService;
import juloo.keyboard2.SmartClipsService;
import java.util.ArrayList;
import java.util.List;

public class ClipboardWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        boolean smartMode = intent.getBooleanExtra("smart_mode", false);
        return new ClipboardRemoteViewsFactory(this.getApplicationContext(), smartMode);
    }
}

class ClipboardRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    private Context mContext;
    private List<String> mClips = new ArrayList<>();
    private List<SmartClipsService.SmartClip> mSmartClips = new ArrayList<>();
    private boolean mSmartMode = false;

    // Dark glass palette — alternating per row
    private static final int[] ITEM_DRAWABLES = {
        R.drawable.widget_item_a,
        R.drawable.widget_item_b
    };

    public ClipboardRemoteViewsFactory(Context context, boolean smartMode) {
        mContext  = context;
        mSmartMode = smartMode;
    }

    @Override public void onCreate() {}

    @Override
    public void onDataSetChanged() {
        SharedPreferences prefs =
                mContext.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
        mSmartMode = prefs.getBoolean("smart_mode", false);
        if (mSmartMode) {
            mSmartClips = SmartClipsService.getInstance(mContext).getClipsForWidget();
        } else {
            mClips = ClipboardHistoryService.getRecentClips(mContext, 20);
        }
    }

    @Override public void onDestroy() { mClips.clear(); mSmartClips.clear(); }

    @Override
    public int getCount() {
        return mSmartMode ? mSmartClips.size() : mClips.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        return mSmartMode ? getSmartClipView(position) : getClipboardView(position);
    }

    // ── Smart Clip row ────────────────────────────────────────────────────────

    private RemoteViews getSmartClipView(int position) {
        RemoteViews rv = new RemoteViews(
                mContext.getPackageName(), R.layout.smart_clip_widget_item);
        if (position >= mSmartClips.size()) return rv;
        SmartClipsService.SmartClip clip = mSmartClips.get(position);

        // Serial chip
        rv.setTextViewText(R.id.tv_serial, "#" + clip.serial);

        // Content — masked if locked
        String display;
        if (clip.locked) {
            display = "⬛  ⬛  ⬛   locked";
        } else if (clip.description != null && !clip.description.isEmpty()) {
            display = clip.description;
        } else {
            String[] lines = clip.content.split("\n", 3);
            display = lines.length > 2 ? lines[0] + "\n" + lines[1] : clip.content;
        }
        rv.setTextViewText(R.id.clip_text, display);

        // Text colour — dim when locked
        rv.setTextColor(R.id.clip_text, clip.locked ? 0xFF475569 : 0xFFE2E8F0);

        // Alternating glass card background
        rv.setInt(R.id.smart_clip_container, "setBackgroundResource",
                ITEM_DRAWABLES[position % ITEM_DRAWABLES.length]);

        // Copy tap → fill-in intent
        Bundle extras = new Bundle();
        extras.putString(ClipboardWidgetProvider.EXTRA_ITEM_TEXT,
                clip.locked ? "" : clip.content);
        Intent fillIn = new Intent();
        fillIn.putExtras(extras);
        rv.setOnClickFillInIntent(R.id.btn_copy, fillIn);

        return rv;
    }

    // ── Clipboard History row ─────────────────────────────────────────────────

    private RemoteViews getClipboardView(int position) {
        RemoteViews rv = new RemoteViews(
                mContext.getPackageName(), R.layout.clipboard_widget_item);

        ClipboardHistoryService.HistoryEntry entry = null;
        ClipboardHistoryService service = ClipboardHistoryService.get_service(mContext);
        if (service != null) {
            List<ClipboardHistoryService.HistoryEntry> entries = service.get_history_entries();
            if (position < entries.size()) entry = entries.get(position);
        }

        String displayContent = "";
        String fullContent    = "";
        if (entry != null) {
            fullContent = entry.content;
            if (entry.description != null && !entry.description.isEmpty()) {
                displayContent = entry.description;
            } else {
                String[] lines = fullContent.split("\n", 3);
                displayContent = lines.length > 2 ? lines[0] + "\n" + lines[1] : fullContent;
            }
        }

        rv.setTextViewText(R.id.clip_text, displayContent);
        rv.setTextColor(R.id.clip_text, 0xFFE2E8F0);

        // Alternating glass card background
        rv.setInt(R.id.clip_container, "setBackgroundResource",
                ITEM_DRAWABLES[position % ITEM_DRAWABLES.length]);

        // Copy tap → fill-in intent
        Bundle extras = new Bundle();
        extras.putString(ClipboardWidgetProvider.EXTRA_ITEM_TEXT, fullContent);
        Intent fillIn = new Intent();
        fillIn.putExtras(extras);
        rv.setOnClickFillInIntent(R.id.btn_copy, fillIn);

        return rv;
    }

    @Override public RemoteViews getLoadingView()  { return null; }
    @Override public int         getViewTypeCount() { return 2; }
    @Override public long        getItemId(int p)   { return p; }
    @Override public boolean     hasStableIds()     { return true; }
}
