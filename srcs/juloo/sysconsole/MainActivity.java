package juloo.sysconsole;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

/**
 * Main launcher for the standalone System Console app.
 *
 * Shows Shizuku + overlay permission status and launches the floating
 * SysConsoleService overlay once both permissions are granted.
 */
public class MainActivity extends Activity {

    private static final int REQUEST_PERMISSION_CODE = 1001;

    private View     mDotShizuku;
    private View     mDotOverlay;
    private TextView mTvShizukuStatus;
    private TextView mTvOverlayStatus;
    private Button   mBtnAuthorize;
    private Button   mBtnOpenShizuku;
    private Button   mBtnLaunch;
    private TextView mTvInfo;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Shizuku.OnBinderReceivedListener mBinderReceived = () ->
            mHandler.post(this::refreshStatus);

    private final Shizuku.OnBinderDeadListener mBinderDead = () ->
            mHandler.post(this::refreshStatus);

    private final Shizuku.OnRequestPermissionResultListener mPermResult =
            (requestCode, grantResult) -> {
                if (requestCode == REQUEST_PERMISSION_CODE) {
                    mHandler.post(this::refreshStatus);
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        mHandler.postDelayed(this::launchConsole, 300);
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        Shizuku.addBinderReceivedListenerSticky(mBinderReceived);
        Shizuku.addBinderDeadListener(mBinderDead);
        Shizuku.addRequestPermissionResultListener(mPermResult);
        refreshStatus();
        autoRequestIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeBinderReceivedListener(mBinderReceived);
        Shizuku.removeBinderDeadListener(mBinderDead);
        Shizuku.removeRequestPermissionResultListener(mPermResult);
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFFFFFFF);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        sv.addView(root);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("🖥  System Console");
        tvTitle.setTextSize(22f);
        tvTitle.setTextColor(0xFF0F172A);
        tvTitle.setTypeface(tvTitle.getTypeface(), android.graphics.Typeface.BOLD);
        tvTitle.setPadding(0, 0, 0, dp(4));
        root.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText("Device-wide logcat viewer — powered by Shizuku");
        tvSub.setTextSize(13f);
        tvSub.setTextColor(0xFF64748B);
        tvSub.setPadding(0, 0, 0, dp(24));
        root.addView(tvSub);

        // Status card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFFF8FAFC);
        cardBg.setCornerRadius(dp(10));
        cardBg.setStroke(dp(1), 0xFFE2E8F0);
        card.setBackground(cardBg);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(16);
        root.addView(card, cardLp);

        TextView tvCardTitle = new TextView(this);
        tvCardTitle.setText("Permission Status");
        tvCardTitle.setTextSize(13f);
        tvCardTitle.setTextColor(0xFF334155);
        tvCardTitle.setTypeface(tvCardTitle.getTypeface(), android.graphics.Typeface.BOLD);
        tvCardTitle.setPadding(0, 0, 0, dp(10));
        card.addView(tvCardTitle);

        LinearLayout rowShizuku = statusRow();
        mDotShizuku      = rowShizuku.getChildAt(0);
        mTvShizukuStatus = (TextView) rowShizuku.getChildAt(1);
        card.addView(rowShizuku);

        LinearLayout.LayoutParams rowLp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp2.topMargin = dp(8);
        LinearLayout rowOverlay = statusRow();
        mDotOverlay      = rowOverlay.getChildAt(0);
        mTvOverlayStatus = (TextView) rowOverlay.getChildAt(1);
        card.addView(rowOverlay, rowLp2);

        // Info text
        mTvInfo = new TextView(this);
        mTvInfo.setTextSize(12f);
        mTvInfo.setTextColor(0xFF6B7280);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        infoLp.bottomMargin = dp(20);
        root.addView(mTvInfo, infoLp);

        // Open Shizuku button
        mBtnOpenShizuku = makeButton("Open Shizuku App", 0xFF374151, 0xFFFFFFFF, 0xFFCBD5E1);
        LinearLayout.LayoutParams btnLp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        btnLp1.bottomMargin = dp(8);
        root.addView(mBtnOpenShizuku, btnLp1);
        mBtnOpenShizuku.setOnClickListener(v -> openShizukuApp());

        // Authorize button
        mBtnAuthorize = makeButton("Authorize via Shizuku", 0xFFFFFFFF, 0xFF0F172A, 0xFF0F172A);
        LinearLayout.LayoutParams btnLp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        btnLp2.bottomMargin = dp(8);
        root.addView(mBtnAuthorize, btnLp2);
        mBtnAuthorize.setOnClickListener(v -> requestShizukuPermission());

        // Launch button
        mBtnLaunch = makeButton("Open System Console  →", 0xFFFFFFFF, 0xFF16A34A, 0xFF16A34A);
        root.addView(mBtnLaunch, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        mBtnLaunch.setOnClickListener(v -> launchConsole());

        setContentView(sv);
    }

    private Button makeButton(String label, int textColor, int fillColor, int strokeColor) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(13f);
        btn.setTextColor(textColor);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fillColor);
        bg.setCornerRadius(dp(8));
        if (strokeColor != fillColor) bg.setStroke(dp(1), strokeColor);
        btn.setBackground(bg);
        btn.setAllCaps(false);
        return btn;
    }

    private LinearLayout statusRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(0xFF9CA3AF);
        View dotView = new View(this);
        dotView.setBackground(dot);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotLp.rightMargin = dp(10);
        row.addView(dotView, dotLp);

        TextView tv = new TextView(this);
        tv.setTextSize(13f);
        tv.setTextColor(0xFF374151);
        row.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    // ── Status refresh ────────────────────────────────────────────────────────

    private void refreshStatus() {
        boolean binderAlive = false;
        boolean permGranted = false;
        boolean overlayOk   = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this);

        try {
            binderAlive = Shizuku.pingBinder();
            if (binderAlive) {
                permGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            }
        } catch (Exception ignored) {}

        setDotColor(mDotShizuku, binderAlive && permGranted ? 0xFF22C55E
                : binderAlive ? 0xFFF59E0B : 0xFFEF4444);
        if (!binderAlive) {
            mTvShizukuStatus.setText("Shizuku: Not running — please start Shizuku first");
        } else if (!permGranted) {
            mTvShizukuStatus.setText("Shizuku: Running — permission not yet granted");
        } else {
            mTvShizukuStatus.setText("Shizuku: Authorized ✓");
        }

        setDotColor(mDotOverlay, overlayOk ? 0xFF22C55E : 0xFFEF4444);
        mTvOverlayStatus.setText(overlayOk
                ? "Draw Over Other Apps: Granted ✓"
                : "Draw Over Other Apps: Not granted — tap below");

        if (!binderAlive) {
            mTvInfo.setText("Install Shizuku from Play Store or F-Droid, then activate it via wireless debugging or root.");
        } else if (!permGranted) {
            mTvInfo.setText("Tap \"Authorize via Shizuku\" — the permission dialog will appear.");
        } else if (!overlayOk) {
            mTvInfo.setText("Grant \"Draw Over Other Apps\" permission, then tap Open System Console.");
        } else {
            mTvInfo.setText("All permissions granted. Tap Open System Console to start.");
        }

        mBtnOpenShizuku.setVisibility(!binderAlive ? View.VISIBLE : View.GONE);
        mBtnAuthorize.setVisibility(binderAlive && !permGranted ? View.VISIBLE : View.GONE);
        mBtnLaunch.setVisibility(binderAlive && permGranted ? View.VISIBLE : View.GONE);
    }

    private void setDotColor(View dot, int color) {
        if (dot.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) dot.getBackground()).setColor(color);
        }
    }

    // ── Permission ────────────────────────────────────────────────────────────

    private void autoRequestIfNeeded() {
        try {
            if (Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                requestShizukuPermission();
            }
        } catch (Exception ignored) {}
    }

    private void requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                mTvInfo.setText("Shizuku is not running. Please start Shizuku first.");
                return;
            }
            Shizuku.requestPermission(REQUEST_PERMISSION_CODE);
        } catch (Exception e) {
            mTvInfo.setText("Could not request permission: " + e.getMessage());
        }
    }

    private void openShizukuApp() {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (i != null) { startActivity(i); return; }
        } catch (Exception ignored) {}
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=moe.shizuku.privileged.api")));
        } catch (Exception ignored) {}
    }

    // ── Launch ────────────────────────────────────────────────────────────────

    private void launchConsole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            mTvInfo.setText("Grant Draw Over Other Apps, then return and tap Open System Console.");
            return;
        }
        Intent svc = new Intent(this, SysConsoleService.class);
        svc.setAction(SysConsoleService.ACTION_SHOW);
        startService(svc);
        finish();
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
