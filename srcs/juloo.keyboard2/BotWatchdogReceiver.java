package juloo.keyboard2;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BotWatchdogReceiver extends BroadcastReceiver {

    public static final String ACTION   = "juloo.keyboard2.BOT_WATCHDOG";
    private static final int   PI_ID    = 9877;
    private static final long  INTERVAL = 30_000L;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!TelegramBotService.isRunning()) {
            TelegramBotService.startIfEnabled(ctx.getApplicationContext());
        }
        schedule(ctx.getApplicationContext());
    }

    public static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = makePi(ctx);
        long trigger = System.currentTimeMillis() + INTERVAL;
        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }

    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(makePi(ctx));
    }

    static PendingIntent makePi(Context ctx) {
        Intent i = new Intent(ACTION);
        i.setClass(ctx, BotWatchdogReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(ctx, PI_ID, i, flags);
    }
}
