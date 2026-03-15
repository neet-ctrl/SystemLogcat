package juloo.sysconsole;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class CopyReceiver extends BroadcastReceiver {

    public static final String ACTION_COPY_CRASH = "juloo.sysconsole.COPY_CRASH";
    public static final String EXTRA_CRASH_TEXT  = "crash_text";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_COPY_CRASH.equals(intent.getAction())) return;

        String text = intent.getStringExtra(EXTRA_CRASH_TEXT);
        if (text == null || text.isEmpty()) return;

        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Crash Log", text));
            Toast.makeText(context, "Crash log copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }
}
