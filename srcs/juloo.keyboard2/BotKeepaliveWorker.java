package juloo.keyboard2;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class BotKeepaliveWorker extends Worker {

    public BotKeepaliveWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        if (!TelegramBotService.isRunning()) {
            TelegramBotService.startIfEnabled(ctx);
        }
        BotWatchdogReceiver.schedule(ctx);
        return Result.success();
    }
}
