package juloo.keyboard2.devconsole;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/**
 * Helper class for launching the DevConsole and logging from anywhere in the app.
 *
 * Usage:
 *   // From any Activity or Service context:
 *   DevConsoleHelper.show(context);
 *
 *   // Log messages (visible in console APP mode):
 *   DevConsoleHelper.d("MyTag", "debug message");
 *   DevConsoleHelper.i("MyTag", "info message");
 *   DevConsoleHelper.w("MyTag", "warning message");
 *   DevConsoleHelper.e("MyTag", "error message");
 */
public final class DevConsoleHelper {

    private DevConsoleHelper() {}

    public static void show(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(context)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        }
        Intent intent = new Intent(context, DevConsoleService.class);
        intent.setAction(DevConsoleService.ACTION_SHOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void hide(Context context) {
        Intent intent = new Intent(context, DevConsoleService.class);
        intent.setAction(DevConsoleService.ACTION_HIDE);
        context.startService(intent);
    }

    public static void d(String tag, String message) {
        DevConsoleManager.getInstance().d(tag, message);
    }

    public static void i(String tag, String message) {
        DevConsoleManager.getInstance().i(tag, message);
    }

    public static void w(String tag, String message) {
        DevConsoleManager.getInstance().w(tag, message);
    }

    public static void e(String tag, String message) {
        DevConsoleManager.getInstance().e(tag, message);
    }
}
