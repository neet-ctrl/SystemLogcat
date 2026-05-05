package juloo.keyboard2;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
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

  private View     _shizukuDot;
  private TextView _shizukuStatusText;
  private String   _createdWithSig;
  private float    _dp;

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    ThemeManager.applyActivityTheme(this);
    super.onCreate(savedInstanceState);
    _createdWithSig = ThemeManager.signature(this);
    _dp = getResources().getDisplayMetrics().density;

    setContentView(R.layout.launcher_activity);

    // Apply theme colours to the whole screen
    applyTheme();

    _tryhere_text = (TextView)findViewById(R.id.launcher_tryhere_text);
    _tryhere_area = (EditText)findViewById(R.id.launcher_tryhere_area);
    if (VERSION.SDK_INT >= 28)
      _tryhere_area.addOnUnhandledKeyEventListener(this.new TryhereKeyListener());
    _handler = new Handler(getMainLooper(), this);

    _shizukuDot        = findViewById(R.id.shizuku_launcher_dot);
    _shizukuStatusText = (TextView) findViewById(R.id.shizuku_status_text);

    // ── Feature card buttons ───────────────────────────────────────
    View btnClipboard = findViewById(R.id.btn_clipboard_history);
    if (btnClipboard != null)
      btnClipboard.setOnClickListener(v ->
          startActivity(new Intent(this, ClipboardHistoryActivity.class)));

    View btnSmartClips = findViewById(R.id.btn_smart_clips);
    if (btnSmartClips != null)
      btnSmartClips.setOnClickListener(v ->
          startActivity(new Intent(this, SmartClipsActivity.class)));

    View btnTypingMaster = findViewById(R.id.btn_typing_master);
    if (btnTypingMaster != null)
      btnTypingMaster.setOnClickListener(v ->
          startActivity(new Intent(this, TypingMasterActivity.class)));

    View btnDevConsole = findViewById(R.id.btn_dev_console);
    if (btnDevConsole != null)
      btnDevConsole.setOnClickListener(v -> DevConsoleHelper.show(this));

    View btnSystemConsole = findViewById(R.id.btn_system_console);
    if (btnSystemConsole != null)
      btnSystemConsole.setOnClickListener(v -> openSystemConsole());

    // ── Settings icon buttons (in header) ─────────────────────────
    View btnKbSettings = findViewById(R.id.btnLaunchSettingsActivity);
    if (btnKbSettings != null)
      btnKbSettings.setOnClickListener(v -> {
        Intent i = new Intent(this, SettingsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
      });

    View btnAppSettings = findViewById(R.id.btn_app_settings);
    if (btnAppSettings != null)
      btnAppSettings.setOnClickListener(v ->
          startActivity(new Intent(this, AppSettingsActivity.class)));

    View btnTutorial = findViewById(R.id.btn_tutorial);
    if (btnTutorial != null)
      btnTutorial.setOnClickListener(v ->
          startActivity(new Intent(this, TutorialActivity.class)));

    // First-run: show tutorial automatically
    TutorialActivity.showIfFirstLaunch(this);

    // Overlay permission
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (!Settings.canDrawOverlays(this)) {
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName())));
      }
    }
  }

  // ── Theme application ──────────────────────────────────────────────────────

  private void applyTheme() {
    ThemeManager.ThemeColors C = ThemeManager.colors(this);
    float D = _dp;
    boolean matrix = ThemeManager.isMatrixMode(this);

    // Root background
    View root = findViewById(R.id.launcher_root);
    if (root != null) root.setBackgroundColor(C.background);

    // Header accent colour
    View header = findViewById(R.id.launcher_header);
    if (header != null) header.setBackgroundColor(C.headerBg);

    // Setup card
    applyCardBg(R.id.card_setup, C, D);
    // Try-here card
    applyCardBg(R.id.card_tryhere, C, D);

    // Feature grid cards: style each one
    int[] featureCards = {
        R.id.btn_clipboard_history, R.id.btn_smart_clips,
        R.id.btn_typing_master,     R.id.btn_dev_console,
        R.id.btn_system_console
    };
    for (int id : featureCards) {
      applyCardBg(id, C, D);
      // Colour inner TextViews (non-emoji)
      View card = findViewById(id);
      if (card instanceof ViewGroup) {
        tintTextViews((ViewGroup) card, C.textPrimary, C.textSecondary, matrix);
      }
    }

    // Setup title
    TextView setupTitle = findViewById(R.id.tv_setup_title);
    if (setupTitle != null) setupTitle.setTextColor(C.primary);
    // Tryhere title
    if (_tryhere_text != null) _tryhere_text.setTextColor(C.primary);

    // Matrix overlay
    ThemeManager.attachMatrixOverlay(this);
  }

  private void applyCardBg(int id, ThemeManager.ThemeColors C, float D) {
    View v = findViewById(id);
    if (v == null) return;
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(C.surface);
    bg.setCornerRadius(14 * D);
    v.setBackground(bg);
    if (Build.VERSION.SDK_INT >= 21) v.setElevation(3 * D);
  }

  private void tintTextViews(ViewGroup group, int primary, int secondary, boolean matrix) {
    for (int i = 0; i < group.getChildCount(); i++) {
      View child = group.getChildAt(i);
      if (child instanceof TextView) {
        TextView tv = (TextView) child;
        String text = tv.getText().toString();
        // Emoji-only views: skip
        if (text.isEmpty() || isEmojiOnly(text)) continue;
        // Larger bold → primary colour; smaller → secondary
        float size = tv.getTextSize();
        tv.setTextColor(size >= 36 ? primary : (size >= 28 ? primary : secondary));
        if (matrix) tv.setTypeface(android.graphics.Typeface.MONOSPACE);
      } else if (child instanceof ViewGroup) {
        tintTextViews((ViewGroup) child, primary, secondary, matrix);
      }
    }
  }

  private boolean isEmojiOnly(String s) {
    String t = s.trim();
    if (t.isEmpty()) return true;
    for (int i = 0; i < t.length(); ) {
      int cp = t.codePointAt(i);
      if (cp < 0x1F000 && Character.getType(cp) != Character.OTHER_SYMBOL) return false;
      i += Character.charCount(cp);
    }
    return true;
  }

  // ── Animation ─────────────────────────────────────────────────────────────

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
    if (!ThemeManager.signature(this).equals(_createdWithSig)) {
      recreate();
      return;
    }
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

  // Keep menu methods (no-op with NoActionBar, harmless to keep)
  @Override
  public final boolean onCreateOptionsMenu(Menu menu)
  {
    getMenuInflater().inflate(R.menu.launcher_menu, menu);
    return true;
  }

  @Override
  public final boolean onOptionsItemSelected(MenuItem item)
  {
    if (item.getItemId() == R.id.menu_keyboard_settings) {
      Intent intent = new Intent(LauncherActivity.this, SettingsActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
    } else if (item.getItemId() == R.id.menu_app_settings) {
      startActivity(new Intent(LauncherActivity.this, AppSettingsActivity.class));
    }
    return super.onOptionsItemSelected(item);
  }

  // ── Public onClick handlers (referenced in XML) ────────────────────────────

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

  // ── Private helpers ────────────────────────────────────────────────────────

  private void openSystemConsole() {
    startActivity(new Intent(this, ShizukuPermissionActivity.class));
  }

  private void refreshShizukuStatus() {
    if (_shizukuDot == null || _shizukuStatusText == null) return;
    boolean binderAlive = false;
    boolean permGranted  = false;
    try {
      binderAlive = Shizuku.pingBinder();
      if (binderAlive)
        permGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    } catch (Exception ignored) {}

    int dotColor;
    String msg;
    if (binderAlive && permGranted) {
      dotColor = 0xFF22C55E;
      msg      = "✓";
    } else if (binderAlive) {
      dotColor = 0xFFF59E0B;
      msg      = "!";
    } else {
      dotColor = 0xFFEF4444;
      msg      = "✕";
    }

    if (_shizukuDot.getBackground() instanceof GradientDrawable)
      ((GradientDrawable) _shizukuDot.getBackground()).setColor(dotColor);
    _shizukuStatusText.setTextColor(
        binderAlive && permGranted ? 0xFF16A34A : binderAlive ? 0xFFD97706 : 0xFF6B7280);
    _shizukuStatusText.setText(msg);
  }

  Animatable find_anim(int id)
  {
    ImageView img = (ImageView) findViewById(id);
    return (Animatable) img.getDrawable();
  }

  final class TryhereKeyListener implements View.OnUnhandledKeyEventListener
  {
    public boolean onUnhandledKeyEvent(View v, KeyEvent ev)
    {
      if (ev.getKeyCode() == KeyEvent.KEYCODE_BACK) return false;
      if (KeyEvent.isModifierKey(ev.getKeyCode())) return false;
      StringBuilder s = new StringBuilder();
      if (ev.isAltPressed())   s.append("Alt+");
      if (ev.isShiftPressed()) s.append("Shift+");
      if (ev.isCtrlPressed())  s.append("Ctrl+");
      if (ev.isMetaPressed())  s.append("Meta+");
      String kc = KeyEvent.keyCodeToString(ev.getKeyCode());
      s.append(kc.replaceFirst("^KEYCODE_", ""));
      _tryhere_text.setText(s.toString());
      return false;
    }
  }
}
