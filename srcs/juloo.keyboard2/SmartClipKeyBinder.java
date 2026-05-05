package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores user-defined mappings of smart clips to physical key corners.
 *
 * Format (SharedPreferences, JSON):
 *   { "a": { "1": 3, "4": 7 }, "q": { "2": 1 } }
 *   outer key = key label (key0.getString()), inner key = slot (1-8), value = smart clip serial
 *
 * Slots:  1=NW  7=N   2=NE
 *         5=W   (key)  6=E
 *         3=SW  8=S   4=SE
 *
 * When the user swipes to an assigned corner the keyboard injects a magic
 * Kind.String value:  "#<serial>\uE001<content>"
 * KeyEventHandler intercepts the U+E001 marker and sends only the content.
 * Keyboard2View shows only the first 3 chars ("#N") as the corner sub-label.
 */
public final class SmartClipKeyBinder {

    private static final String PREFS = "smart_clip_key_bindings";
    private static final String KEY   = "bindings_v1";

    // ── Public API ────────────────────────────────────────────────────────────

    /** Full map: keyName → (slot → serial). */
    public static Map<String, Map<Integer, Integer>> asMap(Context ctx) {
        String json = prefs(ctx).getString(KEY, "{}");
        Map<String, Map<Integer, Integer>> result = new LinkedHashMap<>();
        try {
            JSONObject outer = new JSONObject(json);
            Iterator<String> keys = outer.keys();
            while (keys.hasNext()) {
                String keyName = keys.next();
                JSONObject inner = outer.getJSONObject(keyName);
                Map<Integer, Integer> slots = new LinkedHashMap<>();
                Iterator<String> slotKeys = inner.keys();
                while (slotKeys.hasNext()) {
                    String slotStr = slotKeys.next();
                    slots.put(Integer.parseInt(slotStr), inner.getInt(slotStr));
                }
                result.put(keyName, slots);
            }
        } catch (Exception ignored) {}
        return result;
    }

    /** Serial assigned to the given key + slot, or -1 if none. */
    public static int getSerial(Context ctx, String keyName, int slot) {
        Map<String, Map<Integer, Integer>> map = asMap(ctx);
        Map<Integer, Integer> slots = map.get(keyName);
        if (slots == null) return -1;
        Integer s = slots.get(slot);
        return s != null ? s : -1;
    }

    /** Assign clip [serial] to [keyName] swipe slot (1-8). */
    public static void assign(Context ctx, String keyName, int slot, int serial) {
        Map<String, Map<Integer, Integer>> map = asMap(ctx);
        if (!map.containsKey(keyName)) map.put(keyName, new LinkedHashMap<Integer, Integer>());
        map.get(keyName).put(slot, serial);
        saveMap(ctx, map);
    }

    /** Remove the assignment at [keyName] slot [slot]. */
    public static void remove(Context ctx, String keyName, int slot) {
        Map<String, Map<Integer, Integer>> map = asMap(ctx);
        Map<Integer, Integer> slots = map.get(keyName);
        if (slots == null) return;
        slots.remove(slot);
        if (slots.isEmpty()) map.remove(keyName);
        saveMap(ctx, map);
    }

    /** Remove every corner that was pointing to [serial] (e.g. when clip deleted). */
    public static void removeAllForSerial(Context ctx, int serial) {
        Map<String, Map<Integer, Integer>> map = asMap(ctx);
        boolean changed = false;
        for (Map.Entry<String, Map<Integer, Integer>> e :
                new ArrayList<Map.Entry<String, Map<Integer, Integer>>>(map.entrySet())) {
            Iterator<Map.Entry<Integer, Integer>> it = e.getValue().entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue() == serial) { it.remove(); changed = true; }
            }
            if (e.getValue().isEmpty()) map.remove(e.getKey());
        }
        if (changed) saveMap(ctx, map);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void saveMap(Context ctx, Map<String, Map<Integer, Integer>> map) {
        try {
            JSONObject outer = new JSONObject();
            for (Map.Entry<String, Map<Integer, Integer>> e : map.entrySet()) {
                JSONObject inner = new JSONObject();
                for (Map.Entry<Integer, Integer> s : e.getValue().entrySet())
                    inner.put(String.valueOf(s.getKey()), s.getValue());
                outer.put(e.getKey(), inner);
            }
            prefs(ctx).edit().putString(KEY, outer.toString()).apply();
        } catch (JSONException ignored) {}
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
