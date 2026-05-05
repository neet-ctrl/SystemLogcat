package juloo.keyboard2;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

public final class ThemeManager {

    public static final String PREFS            = "app_ui_prefs";
    public static final String KEY_THEME        = "theme";          // system | light | dark
    public static final String KEY_MATRIX       = "matrix_mode";
    public static final String KEY_MATRIX_SPEED = "matrix_speed";   // slow | normal | fast
    public static final String KEY_MATRIX_DENSITY = "matrix_density"; // low | medium | high
    public static final String KEY_HAPTIC       = "haptic_feedback";
    public static final String KEY_AUTO_LOCK    = "auto_lock_mins"; // 5 | 10 | 30 | -1=never
    public static final String KEY_CLIP_LIMIT   = "clip_widget_limit"; // 10 | 20 | 50
    public static final String KEY_SHOW_SERIAL  = "show_serial_widget";
    public static final String KEY_CB_LIMIT     = "clipboard_history_limit"; // 50 | 100 | 200 | 500

    private ThemeManager() {}

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getTheme(Context ctx) {
        return prefs(ctx).getString(KEY_THEME, "system");
    }

    public static boolean isMatrixMode(Context ctx) {
        return prefs(ctx).getBoolean(KEY_MATRIX, false);
    }

    public static String getMatrixSpeed(Context ctx) {
        return prefs(ctx).getString(KEY_MATRIX_SPEED, "normal");
    }

    public static String getMatrixDensity(Context ctx) {
        return prefs(ctx).getString(KEY_MATRIX_DENSITY, "medium");
    }

    public static boolean isHaptic(Context ctx) {
        return prefs(ctx).getBoolean(KEY_HAPTIC, true);
    }

    public static int getAutoLockMins(Context ctx) {
        return prefs(ctx).getInt(KEY_AUTO_LOCK, 10);
    }

    public static int getClipWidgetLimit(Context ctx) {
        return prefs(ctx).getInt(KEY_CLIP_LIMIT, 20);
    }

    public static boolean isShowSerial(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SHOW_SERIAL, true);
    }

    public static int getClipboardLimit(Context ctx) {
        return prefs(ctx).getInt(KEY_CB_LIMIT, 100);
    }

    public static boolean isDarkMode(Context ctx) {
        String t = getTheme(ctx);
        if ("dark".equals(t)) return true;
        if ("light".equals(t)) return false;
        int mask = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    public static ThemeColors colors(Context ctx) {
        if (isMatrixMode(ctx)) return MATRIX;
        return isDarkMode(ctx) ? DARK : LIGHT;
    }

    public static String signature(Context ctx) {
        return getTheme(ctx) + "_" + isMatrixMode(ctx);
    }

    public static void applyActivityTheme(Activity a) {
        if (isMatrixMode(a) || isDarkMode(a)) {
            a.setTheme(android.R.style.Theme_Material_NoActionBar);
        } else {
            a.setTheme(android.R.style.Theme_Material_Light_NoActionBar);
        }
    }

    public static void attachMatrixOverlay(Activity a) {
        if (!isMatrixMode(a)) return;
        MatrixRainView rain = new MatrixRainView(a);
        rain.setSpeed(getMatrixSpeed(a));
        rain.setDensity(getMatrixDensity(a));
        rain.setAlpha(0.22f);
        rain.setClickable(false);
        rain.setFocusable(false);
        a.addContentView(rain, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ─── Colour palettes ──────────────────────────────────────────────────────

    public static final ThemeColors LIGHT = new ThemeColors(
        0xFFF0F4FF,  // background
        0xFFFFFFFF,  // surface
        0xFFE8EEFF,  // surface variant
        0xFF4F46E5,  // primary  (indigo-600)
        0xFF7C3AED,  // secondary (violet-600)
        0xFF1E1B4B,  // textPrimary
        0xFF6366F1,  // textSecondary
        0xFF94A3B8,  // textHint
        0xFFFFFFFF,  // headerText
        0xFF4F46E5,  // headerBg
        0xFFDDE3FF,  // divider
        0xFF059669,  // green
        0xFF4F46E5,  // blue
        0xFFD97706,  // orange
        0xFF7C3AED   // purple
    );

    public static final ThemeColors DARK = new ThemeColors(
        0xFF0A0E1A,  // background (deep navy)
        0xFF151B2E,  // surface
        0xFF1E2743,  // surface variant
        0xFF818CF8,  // primary  (soft indigo)
        0xFFA78BFA,  // secondary (soft violet)
        0xFFE2E8F0,  // textPrimary
        0xFF94A3B8,  // textSecondary
        0xFF475569,  // textHint
        0xFFFFFFFF,  // headerText
        0xFF1E1B4B,  // headerBg (deep indigo)
        0xFF1E2743,  // divider
        0xFF34D399,  // green
        0xFF818CF8,  // blue
        0xFFFB923C,  // orange
        0xFFA78BFA   // purple
    );

    public static final ThemeColors MATRIX = new ThemeColors(
        0xFF000000,  // background
        0xFF050F05,  // surface
        0xFF0A1A0A,  // surface variant
        0xFF00FF41,  // primary (Matrix green)
        0xFF00CC33,  // secondary
        0xFF00FF41,  // textPrimary
        0xFF00BB30,  // textSecondary
        0xFF006015,  // textHint
        0xFF000000,  // headerText (black on green)
        0xFF00FF41,  // headerBg
        0xFF003300,  // divider
        0xFF00FF41,  // green
        0xFF00FFCC,  // cyan
        0xFF88FF00,  // lime
        0xFF00FFFF   // cyan-lock
    );

    public static final class ThemeColors {
        public final int background, surface, surfaceVariant;
        public final int primary, secondary;
        public final int textPrimary, textSecondary, textHint;
        public final int headerText, headerBg;
        public final int divider;
        public final int green, blue, orange, purple;

        ThemeColors(int bg, int surf, int surfVar, int pri, int sec,
                    int tp, int ts, int th, int ht, int hb, int div,
                    int g, int b, int o, int p) {
            background = bg; surface = surf; surfaceVariant = surfVar;
            primary = pri; secondary = sec;
            textPrimary = tp; textSecondary = ts; textHint = th;
            headerText = ht; headerBg = hb; divider = div;
            green = g; blue = b; orange = o; purple = p;
        }
    }
}
