package juloo.sysconsole;

import android.content.Context;

public class SecurityHub {
    private static SecurityScanManager sInstance;

    public static SecurityScanManager getManager(Context ctx) {
        if (sInstance == null) {
            sInstance = new SecurityScanManager(ctx.getApplicationContext());
        }
        return sInstance;
    }

    public static void reset() {
        sInstance = null;
    }
}
