package juloo.keyboard2;

import android.app.Application;

/**
 * Custom Application class — the very first code that runs when the process starts,
 * before any Activity, Service, or BroadcastReceiver.
 * Installs CrashReporter here so crashes at any point in the app's lifetime
 * are caught and sent to Telegram.
 */
public class Keyboard2App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
    }
}
