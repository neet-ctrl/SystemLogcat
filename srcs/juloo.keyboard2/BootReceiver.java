package juloo.keyboard2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public class BootReceiver extends BroadcastReceiver {

    private static final int MAX_RETRIES = 10;
    private static final long RETRY_DELAY_MS = 5000;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)
                || action.equals(Intent.ACTION_MY_PACKAGE_REPLACED)
                || action.equals("android.intent.action.PACKAGE_REPLACED")
                || action.equals("android.intent.action.PACKAGE_ADDED")) {
            tryStartBot(context.getApplicationContext(), 0);
        }
    }

    private static void tryStartBot(Context ctx, int attempt) {
        if (attempt >= MAX_RETRIES) return;
        if (TelegramBotService.isRunning()) return;
        try {
            TelegramBotService.startIfEnabled(ctx);
        } catch (Exception ignored) {}
        if (!TelegramBotService.isRunning() && attempt < MAX_RETRIES - 1) {
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> tryStartBot(ctx, attempt + 1),
                    RETRY_DELAY_MS);
        }
    }

    public static void retryStartBot(Context ctx) {
        tryStartBot(ctx.getApplicationContext(), 0);
    }
}
