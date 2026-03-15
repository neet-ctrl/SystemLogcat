package juloo.keyboard2;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.Build;
import android.graphics.drawable.Animatable;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.devconsole.DevConsoleHelper;
import juloo.keyboard2.devconsole.ShizukuPermissionActivity;
import rikka.shizuku.Shizuku;

public class LauncherActivity extends Activity implements Handler.Callback
{
  TextView _tryhere_text;
  EditText _tryhere_area;
  List<Animatable> _animations;
  Handler _handler;

  // Shizuku status views
  private View     _shizukuDot;
  private TextView _shizukuStatusText;

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.launcher_activity);
    _tryhere_text = (TextView)findViewById(R.id.launcher_tryhere_text);
    _tryhere_area = (EditText)findViewById(R.id.launcher_tryhere_area);
    if (VERSION.SDK_INT >= 28)
      _tryhere_area.addOnUnhandledKeyEventListener(
          this.new Tryhere_OnUnhandledKeyEventListener());
    _handler = new Handler(getMainLooper(), this);

    // Shizuku status indicator views
    _shizukuDot        = findViewById(R.id.shizuku_launcher_dot);
    _shizukuStatusText = (TextView) findViewById(R.id.shizuku_status_text);

    View btnClipboard = findViewById(R.id.btn_clipboard_history);
    if (btnClipboard != null) {
        btnClipboard.setOnClickListener(v -> {
            Intent intent = new Intent(this, ClipboardHistoryActivity.class);
            startActivity(intent);
        });
    }
    View btnTypingMaster = findViewById(R.id.btn_typing_master);
    if (btnTypingMaster != null) {
        btnTypingMaster.setOnClickListener(v -> {
            Intent intent = new Intent(this, TypingMasterActivity.class);
            startActivity(intent);
        });
    }
    View btnDevConsole = findViewById(R.id.btn_dev_console);
    if (btnDevConsole != null) {
        btnDevConsole.setOnClickListener(v -> DevConsoleHelper.show(this));
    }

    // 4th option: System Console (Shizuku-backed device-wide logcat)
    View btnSystemConsole = findViewById(R.id.btn_system_console);
    if (btnSystemConsole != null) {
        btnSystemConsole.setOnClickListener(v -> openSystemConsole());
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }
  }

  @Override
  public void onStart()
  {
    super.onStart();
    _animations = new ArrayList<Animatable>();
    _animations.add(find_anim(R.id.launcher_anim_swipe));
    _animations.add(find_anim(R.id.launcher_anim_round_trip));
    _animations.add(find_anim(R.id.launcher_anim_circle));
    _handler.removeMessages(0);
    _handler.sendEmptyMessageDelayed(0, 500);
  }

  @Override
  public void onResume()
  {
    super.onResume();
    refreshShizukuStatus();
  }

  @Override
  public boolean handleMessage(Message _msg)
  {
    for (Animatable anim : _animations)
      anim.start();
    _handler.sendEmptyMessageDelayed(0, 3000);
    return true;
  }

  @Override
  public final boolean onCreateOptionsMenu(Menu menu)
  {
    getMenuInflater().inflate(R.menu.launcher_menu, menu);
    return true;
  }

  @Override
  public final boolean onOptionsItemSelected(MenuItem item)
  {
    if (item.getItemId() == R.id.btnLaunchSettingsActivity)
    {
      Intent intent = new Intent(LauncherActivity.this, SettingsActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
    }
    return super.onOptionsItemSelected(item);
  }

  public void launch_imesettings(View _btn)
  {
    startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
  }

  public void launch_imepicker(View v)
  {
    InputMethodManager imm =
      (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
    imm.showInputMethodPicker();
  }

  // ── System Console ──────────────────────────────────────────────────────────

  private void openSystemConsole() {
    Intent intent = new Intent(this, ShizukuPermissionActivity.class);
    startActivity(intent);
  }

  // ── Shizuku status ──────────────────────────────────────────────────────────

  private void refreshShizukuStatus() {
    if (_shizukuDot == null || _shizukuStatusText == null) return;
    boolean binderAlive = false;
    boolean permGranted = false;
    try {
        binderAlive = Shizuku.pingBinder();
        if (binderAlive) {
            permGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        }
    } catch (Exception ignored) {}

    int dotColor;
    String statusMsg;
    if (binderAlive && permGranted) {
        dotColor  = 0xFF22C55E; // green
        statusMsg = "Shizuku: Authorized ✓ — System Console ready";
    } else if (binderAlive) {
        dotColor  = 0xFFF59E0B; // amber
        statusMsg = "Shizuku: Running — tap System Console to authorize";
    } else {
        dotColor  = 0xFFEF4444; // red
        statusMsg = "Shizuku: Not running — install & start Shizuku for System Console";
    }

    if (_shizukuDot.getBackground() instanceof GradientDrawable) {
        ((GradientDrawable) _shizukuDot.getBackground()).setColor(dotColor);
    }
    _shizukuStatusText.setTextColor(binderAlive && permGranted ? 0xFF16A34A
            : binderAlive ? 0xFFD97706 : 0xFF6B7280);
    _shizukuStatusText.setText(statusMsg);
  }

  // ── Animations ──────────────────────────────────────────────────────────────

  Animatable find_anim(int id)
  {
    ImageView img = (ImageView)findViewById(id);
    return (Animatable)img.getDrawable();
  }

  final class Tryhere_OnUnhandledKeyEventListener implements View.OnUnhandledKeyEventListener
  {
    public boolean onUnhandledKeyEvent(View v, KeyEvent ev)
    {
      if (ev.getKeyCode() == KeyEvent.KEYCODE_BACK)
        return false;
      if (KeyEvent.isModifierKey(ev.getKeyCode()))
        return false;
      StringBuilder s = new StringBuilder();
      if (ev.isAltPressed()) s.append("Alt+");
      if (ev.isShiftPressed()) s.append("Shift+");
      if (ev.isCtrlPressed()) s.append("Ctrl+");
      if (ev.isMetaPressed()) s.append("Meta+");
      String kc = KeyEvent.keyCodeToString(ev.getKeyCode());
      s.append(kc.replaceFirst("^KEYCODE_", ""));
      _tryhere_text.setText(s.toString());
      return false;
    }
  }
}
