package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * Persists "pinned" state for Smart Clips (by serial number).
 * Smart clip pins are display-only — they never mutate serial numbers or content.
 * Normal clipboard clip pins live directly in ClipboardHistoryService.HistoryEntry.
 */
public final class PinStore {

    private static final String PREFS = "clip_pin_prefs";
    private static final String KEY   = "smart_pins";  // comma-separated serial ints

    /** Returns true if the smart clip with this serial is currently pinned. */
    public static boolean isSmartPinned(Context ctx, int serial) {
        return getSmartPins(ctx).contains(serial);
    }

    /** Toggle the pin state for a smart clip serial. */
    public static void toggleSmartPin(Context ctx, int serial) {
        Set<Integer> pins = getSmartPins(ctx);
        if (pins.contains(serial)) pins.remove(serial);
        else                        pins.add(serial);
        saveSmartPins(ctx, pins);
    }

    /** Returns all pinned smart clip serials. */
    public static Set<Integer> getSmartPins(Context ctx) {
        String raw = prefs(ctx).getString(KEY, "");
        Set<Integer> out = new HashSet<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String tok : raw.split(",")) {
            tok = tok.trim();
            if (!tok.isEmpty()) {
                try { out.add(Integer.parseInt(tok)); } catch (NumberFormatException ignored) {}
            }
        }
        return out;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static void saveSmartPins(Context ctx, Set<Integer> pins) {
        StringBuilder sb = new StringBuilder();
        for (Integer s : pins) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        prefs(ctx).edit().putString(KEY, sb.toString()).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private PinStore() {}
}
