package juloo.keyboard2.widget;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import juloo.keyboard2.R;
import juloo.keyboard2.ClipboardHistoryService;
import juloo.keyboard2.PinStore;
import juloo.keyboard2.SmartClipsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ClipboardWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        boolean smartMode = intent.getBooleanExtra("smart_mode", false);
        return new ClipboardRemoteViewsFactory(this.getApplicationContext(), smartMode);
    }
}

class ClipboardRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    private Context mContext;
    // Normal clipboard entries — sorted pinned-first
    private List<ClipboardHistoryService.HistoryEntry> mEntries  = new ArrayList<>();
    // Smart clip entries — sorted pinned-first
    private List<SmartClipsService.SmartClip>          mSmartClips = new ArrayList<>();
    private boolean mSmartMode = false;

    // Dark glass palette — alternating per row
    private static final int[] ITEM_DRAWABLES = {
        R.drawable.widget_item_a,
        R.drawable.widget_item_b
    };

    public ClipboardRemoteViewsFactory(Context context, boolean smartMode) {
        mContext   = context;
        mSmartMode = smartMode;
    }

    @Override public void onCreate() {}

    @Override
    public void onDataSetChanged() {
        SharedPreferences prefs =
                mContext.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
        mSmartMode = prefs.getBoolean("smart_mode", false);

        if (mSmartMode) {
            // Load smart clips then sort pinned-first (serials never change)
            mSmartClips = SmartClipsService.getInstance(mContext).getClipsForWidget();
            Set<Integer> pins = PinStore.getSmartPins(mContext);
            mSmartClips.sort((a, b) -> {
                boolean pa = pins.contains(a.serial), pb = pins.contains(b.serial);
                if (pa != pb) return pa ? -1 : 1;
                return Integer.compare(a.serial, b.serial);
            });
        } else {
            // Load history entries and sort pinned-first
            ClipboardHistoryService svc = ClipboardHistoryService.get_service(mContext);
            mEntries.clear();
            if (svc != null) {
                mEntries = svc.get_history_entries();
                mEntries.sort((a, b) -> {
                    if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
                    return b.timestamp.compareTo(a.timestamp);
                });
                if (mEntries.size() > 20) mEntries = new ArrayList<>(mEntries.subList(0, 20));
            }
        }
    }

    @Override public void onDestroy() { mEntries.clear(); mSmartClips.clear(); }

    @Override
    public int getCount() {
        return mSmartMode ? mSmartClips.size() : mEntries.size();
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

        // 📌/📍 Pin icon — reflects current pin state
        boolean pinned = PinStore.isSmartPinned(mContext, clip.serial);
        rv.setTextViewText(R.id.btn_pin, pinned ? "📌" : "📍");

        // Pin tap fill-in intent
        Bundle pinExtras = new Bundle();
        pinExtras.putString(ClipboardWidgetProvider.EXTRA_ACTION_TYPE, "pin_smart");
        pinExtras.putString(ClipboardWidgetProvider.EXTRA_ITEM_TEXT,
                clip.locked ? "" : clip.content);
        pinExtras.putInt(ClipboardWidgetProvider.EXTRA_SERIAL, clip.serial);
        Intent pinFill = new Intent();
        pinFill.putExtras(pinExtras);
        rv.setOnClickFillInIntent(R.id.btn_pin, pinFill);

        // Copy tap fill-in intent
        Bundle copyExtras = new Bundle();
        copyExtras.putString(ClipboardWidgetProvider.EXTRA_ACTION_TYPE, "copy");
        copyExtras.putString(ClipboardWidgetProvider.EXTRA_ITEM_TEXT,
                clip.locked ? "" : clip.content);
        Intent copyFill = new Intent();
        copyFill.putExtras(copyExtras);
        rv.setOnClickFillInIntent(R.id.btn_copy, copyFill);

        return rv;
    }

    // ── Clipboard History row ─────────────────────────────────────────────────

    private RemoteViews getClipboardView(int position) {
        RemoteViews rv = new RemoteViews(
                mContext.getPackageName(), R.layout.clipboard_widget_item);

        if (position >= mEntries.size()) return rv;
        ClipboardHistoryService.HistoryEntry entry = mEntries.get(position);

        String fullContent    = entry.content;
        String displayContent;
        if (entry.description != null && !entry.description.isEmpty()) {
            displayContent = entry.description;
        } else {
            String[] lines = fullContent.split("\n", 3);
            displayContent = lines.length > 2 ? lines[0] + "\n" + lines[1] : fullContent;
        }

        rv.setTextViewText(R.id.clip_text, displayContent);
        rv.setTextColor(R.id.clip_text, 0xFFE2E8F0);

        // Alternating glass card background
        rv.setInt(R.id.clip_container, "setBackgroundResource",
                ITEM_DRAWABLES[position % ITEM_DRAWABLES.length]);

        // 📌/📍 Pin icon — reflects current pin state
        rv.setTextViewText(R.id.btn_pin, entry.pinned ? "📌" : "📍");

        // Pin tap fill-in intent
        Bundle pinExtras = new Bundle();
        pinExtras.putString(ClipboardWidgetProvider.EXTRA_ACTION_TYPE, "pin_clip");
        pinExtras.putString(ClipboardWidgetProvider.EXTRA_ITEM_TEXT, fullContent);
        Intent pinFill = new Intent();
        pinFill.putExtras(pinExtras);
        rv.setOnClickFillInIntent(R.id.btn_pin, pinFill);

        // Copy tap fill-in intent
        Bundle copyExtras = new Bundle();
        copyExtras.putString(ClipboardWidgetProvider.EXTRA_ACTION_TYPE, "copy");
        copyExtras.putString(ClipboardWidgetProvider.EXTRA_ITEM_TEXT, fullContent);
        Intent copyFill = new Intent();
        copyFill.putExtras(copyExtras);
        rv.setOnClickFillInIntent(R.id.btn_copy, copyFill);

        return rv;
    }

    @Override public RemoteViews getLoadingView()  { return null; }
    @Override public int         getViewTypeCount() { return 2; }
    @Override public long        getItemId(int p)   { return p; }
    @Override public boolean     hasStableIds()     { return true; }
}
