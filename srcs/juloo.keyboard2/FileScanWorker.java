package juloo.keyboard2;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;

public class FileScanWorker extends Worker {

    private static final String WORK_NAME = "file_scan_periodic";

    public FileScanWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        FileBackupService.scanAllDirs(ctx);
        FileBackupService.startIfEnabled(ctx);
        BotWatchdogReceiver.schedule(ctx);
        return Result.success();
    }

    public static void enqueue(Context ctx) {
        try {
            PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                    FileScanWorker.class, 15, TimeUnit.MINUTES)
                    .build();
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    req);
        } catch (Exception ignored) {}
    }
}
