package juloo.sysconsole;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "SysConsole-Boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Draw-over-apps permission not granted — skipping auto-start");
            return;
        }

        Log.i(TAG, "Boot completed — auto-starting System Console");

        Intent svc = new Intent(context, SysConsoleService.class);
        svc.setAction(SysConsoleService.ACTION_SHOW);
        svc.putExtra(SysConsoleService.EXTRA_FROM_BOOT, true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc);
        } else {
            context.startService(svc);
        }
    }
}
