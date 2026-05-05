package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class SmartClipsService {

    private static SmartClipsService _instance = null;
    private static final String PREFS_CLIPS = "smart_clips_data";
    private static final String PREFS_PIN   = "smart_clips_pin";
    private static final String KEY_CLIPS   = "clips_json";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_LOCK_ENABLED = "lock_enabled";
    private static final String KEY_UNLOCK_EXPIRY = "unlock_expiry";
    private static final String KEY_NEXT_SERIAL = "next_serial";
    private static final long UNLOCK_DURATION_MS = 10 * 60 * 1000L;

    private final Context _ctx;
    private List<SmartClip> _clips;
    private List<OnSmartClipsChangeListener> _listeners = new ArrayList<>();

    public interface OnSmartClipsChangeListener {
        void onSmartClipsChanged();
    }

    public static SmartClipsService getInstance(Context ctx) {
        if (_instance == null) {
            _instance = new SmartClipsService(ctx.getApplicationContext());
        }
        return _instance;
    }

    private SmartClipsService(Context ctx) {
        _ctx = ctx;
        _clips = new ArrayList<>();
        loadFromPrefs();
    }

    public void addListener(OnSmartClipsChangeListener l) {
        if (!_listeners.contains(l)) _listeners.add(l);
    }

    public void removeListener(OnSmartClipsChangeListener l) {
        _listeners.remove(l);
    }

    private void notifyListeners() {
        for (OnSmartClipsChangeListener l : new ArrayList<>(_listeners)) {
            l.onSmartClipsChanged();
        }
    }

    public List<SmartClip> getClips() {
        return new ArrayList<>(_clips);
    }

    public List<SmartClip> getClipsForWidget() {
        List<SmartClip> result = new ArrayList<>();
        for (SmartClip c : _clips) {
            if (!c.hidden) result.add(c);
        }
        return result;
    }

    public SmartClip getBySerial(int serial) {
        for (SmartClip c : _clips) {
            if (c.serial == serial) return c;
        }
        return null;
    }

    public SmartClip getByKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) return null;
        for (SmartClip c : _clips) {
            if (keyword.equalsIgnoreCase(c.keyword)) return c;
        }
        return null;
    }

    public SmartClip resolveFormula(String token) {
        try {
            int serial = Integer.parseInt(token.trim());
            return getBySerial(serial);
        } catch (NumberFormatException e) {
            return getByKeyword(token.trim());
        }
    }

    /**
     * Bulk-import clips from a backup restore.
     * Skips any clip whose serial already exists in the current list.
     * Preserves the locked flag on each restored clip as-is; the OLD pin
     * hash is intentionally never restored — locked clips will be controlled
     * by whatever pin is currently set up on this device.
     * Updates the next-serial counter to be at least max(existing, hint).
     */
    public void importClipsFromBackup(List<SmartClip> toImport, int nextSerialHint) {
        java.util.Set<Integer> existing = new java.util.HashSet<>();
        int maxSerial = 0;
        for (SmartClip c : _clips) {
            existing.add(c.serial);
            if (c.serial > maxSerial) maxSerial = c.serial;
        }
        for (SmartClip c : toImport) {
            if (!existing.contains(c.serial)) {
                _clips.add(c);
                existing.add(c.serial);
                if (c.serial > maxSerial) maxSerial = c.serial;
            }
        }
        // Ensure next-serial won't collide with any restored serial
        SharedPreferences prefs = _ctx.getSharedPreferences(PREFS_CLIPS, Context.MODE_PRIVATE);
        int currentNext = prefs.getInt(KEY_NEXT_SERIAL, 1);
        int newNext = Math.max(Math.max(currentNext, nextSerialHint), maxSerial + 1);
        prefs.edit().putInt(KEY_NEXT_SERIAL, newNext).apply();
        saveToPrefs();
        notifyListeners();
        notifyWidget();
    }

    public void addClip(String content, String description, String keyword) {
        SharedPreferences prefs = _ctx.getSharedPreferences(PREFS_CLIPS, Context.MODE_PRIVATE);
        int nextSerial = prefs.getInt(KEY_NEXT_SERIAL, 1);
        String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()).format(new java.util.Date());
        SmartClip clip = new SmartClip(nextSerial, content, description,
                keyword == null ? "" : keyword, false, false, ts);
        _clips.add(0, clip);
        prefs.edit().putInt(KEY_NEXT_SERIAL, nextSerial + 1).apply();
        saveToPrefs();
        notifyListeners();
        notifyWidget();
    }

    public void updateClip(SmartClip updated) {
        for (int i = 0; i < _clips.size(); i++) {
            if (_clips.get(i).serial == updated.serial) {
                _clips.set(i, updated);
                break;
            }
        }
        saveToPrefs();
        notifyListeners();
        notifyWidget();
    }

    public void deleteClip(int serial) {
        for (int i = 0; i < _clips.size(); i++) {
            if (_clips.get(i).serial == serial) {
                _clips.remove(i);
                break;
            }
        }
        saveToPrefs();
        notifyListeners();
        notifyWidget();
    }

    private void saveToPrefs() {
        try {
            JSONArray arr = new JSONArray();
            for (SmartClip c : _clips) {
                JSONObject obj = new JSONObject();
                obj.put("serial", c.serial);
                obj.put("content", c.content);
                obj.put("description", c.description);
                obj.put("keyword", c.keyword);
                obj.put("hidden", c.hidden);
                obj.put("locked", c.locked);
                obj.put("timestamp", c.timestamp);
                arr.put(obj);
            }
            _ctx.getSharedPreferences(PREFS_CLIPS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_CLIPS, arr.toString()).apply();
        } catch (Exception e) {
            android.util.Log.e("SmartClipsService", "Save failed", e);
        }
    }

    private void loadFromPrefs() {
        try {
            SharedPreferences prefs = _ctx.getSharedPreferences(PREFS_CLIPS, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_CLIPS, null);
            if (json == null) return;
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                _clips.add(new SmartClip(
                        obj.getInt("serial"),
                        obj.getString("content"),
                        obj.optString("description", ""),
                        obj.optString("keyword", ""),
                        obj.optBoolean("hidden", false),
                        obj.optBoolean("locked", false),
                        obj.optString("timestamp", "")
                ));
            }
        } catch (Exception e) {
            android.util.Log.e("SmartClipsService", "Load failed", e);
        }
    }

    private void notifyWidget() {
        try {
            android.content.Intent intent = new android.content.Intent(
                    android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            android.content.ComponentName widget = new android.content.ComponentName(
                    _ctx, juloo.keyboard2.widget.ClipboardWidgetProvider.class);
            int[] ids = android.appwidget.AppWidgetManager.getInstance(_ctx).getAppWidgetIds(widget);
            intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            _ctx.sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    public boolean isPinSetup() {
        return _ctx.getSharedPreferences(PREFS_PIN, Context.MODE_PRIVATE)
                .contains(KEY_PIN_HASH);
    }

    public boolean isLockEnabled() {
        return _ctx.getSharedPreferences(PREFS_PIN, Context.MODE_PRIVATE)
                .getBoolean(KEY_LOCK_ENABLED, false);
    }

    public void setLockEnabled(boolean enabled) {
        _ctx.getSharedPreferences(PREFS_PIN, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply();
    }

    public void setupPin(String pin) {
        String hash = hashPin(pin);
        _ctx.getSharedPreferences(PREFS_PIN, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PIN_HASH, hash)
                .putBoolean(KEY_LOCK_ENABLED, true)
                .apply();
    }

    public boolean verifyPin(String pin) {
        String stored = _ctx.getSharedPreferences(PREFS_PIN, Context.MODE_PRIVATE)
                .getString(KEY_PIN_HASH, null);
        if (stored == null) return false;
        return stored.equals(hashPin(pin));
    }

    public boolean isUnlocked() {
        long expiry = _ctx.getSharedPreferences(PREFS_PIN, Context.MODE_PRIVATE)
                .getLong(KEY_UNLOCK_EXPIRY, 0L);
        return System.currentTimeMillis() < expiry;
    }

    public void unlock10Min() {
        long expiry = System.currentTimeMillis() + UNLOCK_DURATION_MS;
        _ctx.getSharedPreferences(PREFS_PIN, Context.MODE_PRIVATE)
                .edit().putLong(KEY_UNLOCK_EXPIRY, expiry).apply();
    }

    public void lock() {
        _ctx.getSharedPreferences(PREFS_PIN, Context.MODE_PRIVATE)
                .edit().putLong(KEY_UNLOCK_EXPIRY, 0L).apply();
    }

    public long getUnlockRemainingMs() {
        long expiry = _ctx.getSharedPreferences(PREFS_PIN, Context.MODE_PRIVATE)
                .getLong(KEY_UNLOCK_EXPIRY, 0L);
        long remaining = expiry - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    private String hashPin(String pin) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(("scpin:" + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return pin;
        }
    }

    public static final class SmartClip {
        public final int serial;
        public final String content;
        public final String description;
        public final String keyword;
        public final boolean hidden;
        public final boolean locked;
        public final String timestamp;

        public SmartClip(int serial, String content, String description,
                         String keyword, boolean hidden, boolean locked, String timestamp) {
            this.serial = serial;
            this.content = content;
            this.description = description;
            this.keyword = keyword;
            this.hidden = hidden;
            this.locked = locked;
            this.timestamp = timestamp;
        }

        public SmartClip withHidden(boolean h) {
            return new SmartClip(serial, content, description, keyword, h, locked, timestamp);
        }

        public SmartClip withLocked(boolean l) {
            return new SmartClip(serial, content, description, keyword, hidden, l, timestamp);
        }

        public SmartClip withContent(String c, String d, String k) {
            return new SmartClip(serial, c, d, k, hidden, locked, timestamp);
        }
    }
}
