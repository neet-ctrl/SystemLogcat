package juloo.sysconsole;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.List;

/**
 * Receives the AlarmManager broadcast for a scheduled deep scan.
 * Runs the scan in background and posts a notification with the result.
 */
public class ScheduledScanReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID   = "sec_scan";
    private static final int    NOTIF_ID     = 7001;

    @Override
    public void onReceive(Context context, Intent intent) {
        createChannel(context);
        postNotification(context, "🔍 Running Scheduled Scan…",
                "Deep security scan is in progress.", false);

        SecurityScanManager mgr = SecurityHub.getManager(context);
        mgr.scanAsync(new SecurityScanManager.ScanCallback() {
            @Override
            public void onProgress(int current, int total, String phase) {}

            @Override
            public void onComplete(List<AppSecurityInfo> apps, List<SecurityAlert> alerts) {
                int high    = SecurityScanManager.countWithPerm(apps, "high");
                int spyware = SecurityScanManager.countWithPerm(apps, "spyware");
                String title, body;
                if (spyware > 0) {
                    title = "⚠ CRITICAL: " + spyware + " spyware threat(s) detected!";
                    body  = "Tap to review immediately. " + apps.size() + " apps scanned.";
                } else if (high > 0) {
                    title = "⚠ HIGH RISK: " + high + " high-risk app(s) found";
                    body  = apps.size() + " apps scanned. Tap to review.";
                } else {
                    title = "✓ Scheduled Scan Complete";
                    body  = apps.size() + " apps scanned — no major threats detected.";
                }
                postNotification(context, title, body, true);
            }
        });
    }

    private void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Security Scan Results",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Notifications from scheduled security scans");
            ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(ch);
        }
    }

    private void postNotification(Context ctx, String title, String body, boolean tapable) {
        Intent tap = new Intent(ctx, SecurityDashboardActivity.class);
        tap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, tap, flags);

        android.app.Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nb = new android.app.Notification.Builder(ctx, CHANNEL_ID);
        } else {
            nb = new android.app.Notification.Builder(ctx);
        }
        nb.setSmallIcon(android.R.drawable.ic_lock_lock)
          .setContentTitle(title)
          .setContentText(body)
          .setAutoCancel(true)
          .setStyle(new android.app.Notification.BigTextStyle().bigText(body));
        if (tapable) nb.setContentIntent(pi);

        ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(NOTIF_ID, nb.build());
    }
}
