package juloo.keyboard2.devconsole;

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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.net.Uri;

import rikka.shizuku.Shizuku;

/**
 * Activity that:
 *  1. Shows Shizuku connectivity status (binder alive + permission granted)
 *  2. Auto-requests Shizuku permission the moment it opens
 *  3. Launches SystemConsoleService once authorized
 *  4. Shows SYSTEM_ALERT_WINDOW permission check
 */
public class ShizukuPermissionActivity extends Activity {

    private static final int REQUEST_PERMISSION_CODE = 1001;

    private View   mDotShizuku;
    private View   mDotOverlay;
    private TextView mTvShizukuStatus;
    private TextView mTvOverlayStatus;
    private Button mBtnAuthorize;
    private Button mBtnOpenShizuku;
    private Button mBtnLaunch;
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

    // ── Build programmatic UI ──────────────────────────────────────────────────

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);
        root.setPadding(dp(24), dp(20), dp(24), dp(20));

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("🖥  System Console");
        tvTitle.setTextSize(20f);
        tvTitle.setTextColor(0xFF0F172A);
        tvTitle.setTypeface(tvTitle.getTypeface(), android.graphics.Typeface.BOLD);
        tvTitle.setPadding(0, 0, 0, dp(4));
        root.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText("Device-wide logcat requires Shizuku authorization");
        tvSub.setTextSize(12f);
        tvSub.setTextColor(0xFF64748B);
        tvSub.setPadding(0, 0, 0, dp(20));
        root.addView(tvSub);

        // ── Status card ────────────────────────────────────────────────────────
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

        // Shizuku status row
        LinearLayout rowShizuku = statusRow();
        mDotShizuku     = rowShizuku.getChildAt(0);
        mTvShizukuStatus = (TextView) rowShizuku.getChildAt(1);
        card.addView(rowShizuku);

        // Overlay permission row
        LinearLayout.LayoutParams rowLp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp2.topMargin = dp(8);
        LinearLayout rowOverlay = statusRow();
        mDotOverlay     = rowOverlay.getChildAt(0);
        mTvOverlayStatus = (TextView) rowOverlay.getChildAt(1);
        card.addView(rowOverlay, rowLp2);

        // ── Info text ──────────────────────────────────────────────────────────
        mTvInfo = new TextView(this);
        mTvInfo.setTextSize(12f);
        mTvInfo.setTextColor(0xFF6B7280);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        infoLp.bottomMargin = dp(20);
        root.addView(mTvInfo, infoLp);

        // ── Buttons ────────────────────────────────────────────────────────────
        // Open Shizuku app
        mBtnOpenShizuku = new Button(this);
        mBtnOpenShizuku.setText("Open Shizuku App");
        mBtnOpenShizuku.setTextSize(13f);
        mBtnOpenShizuku.setTextColor(0xFF374151);
        GradientDrawable outlineBg = new GradientDrawable();
        outlineBg.setColor(0xFFFFFFFF);
        outlineBg.setCornerRadius(dp(8));
        outlineBg.setStroke(dp(1), 0xFFCBD5E1);
        mBtnOpenShizuku.setBackground(outlineBg);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        btnLp.bottomMargin = dp(8);
        root.addView(mBtnOpenShizuku, btnLp);
        mBtnOpenShizuku.setOnClickListener(v -> openShizukuApp());

        // Authorize button
        mBtnAuthorize = new Button(this);
        mBtnAuthorize.setText("Authorize via Shizuku");
        mBtnAuthorize.setTextSize(13f);
        mBtnAuthorize.setTextColor(0xFFFFFFFF);
        GradientDrawable authBg = new GradientDrawable();
        authBg.setColor(0xFF0F172A);
        authBg.setCornerRadius(dp(8));
        mBtnAuthorize.setBackground(authBg);
        LinearLayout.LayoutParams authLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        authLp.bottomMargin = dp(8);
        root.addView(mBtnAuthorize, authLp);
        mBtnAuthorize.setOnClickListener(v -> requestShizukuPermission());

        // Launch button
        mBtnLaunch = new Button(this);
        mBtnLaunch.setText("Open System Console  →");
        mBtnLaunch.setTextSize(13f);
        mBtnLaunch.setTextColor(0xFFFFFFFF);
        GradientDrawable launchBg = new GradientDrawable();
        launchBg.setColor(0xFF16A34A);
        launchBg.setCornerRadius(dp(8));
        mBtnLaunch.setBackground(launchBg);
        LinearLayout.LayoutParams launchLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        root.addView(mBtnLaunch, launchLp);
        mBtnLaunch.setOnClickListener(v -> launchConsole());

        setContentView(root);
    }

    private LinearLayout statusRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(lp);

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

    // ── Status refresh ─────────────────────────────────────────────────────────

    private void refreshStatus() {
        boolean binderAlive = false;
        boolean permGranted  = false;
        boolean overlayOk    = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this);

        try {
            binderAlive = Shizuku.pingBinder();
            if (binderAlive) {
                permGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            }
        } catch (Exception ignored) {}

        // Shizuku dot + text
        setDotColor(mDotShizuku, binderAlive && permGranted ? 0xFF22C55E
                : binderAlive ? 0xFFF59E0B : 0xFFEF4444);
        if (!binderAlive) {
            mTvShizukuStatus.setText("Shizuku: Not running — please start Shizuku first");
        } else if (!permGranted) {
            mTvShizukuStatus.setText("Shizuku: Running — permission not yet granted");
        } else {
            mTvShizukuStatus.setText("Shizuku: Authorized ✓");
        }

        // Overlay dot + text
        setDotColor(mDotOverlay, overlayOk ? 0xFF22C55E : 0xFFEF4444);
        mTvOverlayStatus.setText(overlayOk
                ? "Draw Over Other Apps: Granted ✓"
                : "Draw Over Other Apps: Not granted — tap below");

        // Info text
        if (!binderAlive) {
            mTvInfo.setText("Install Shizuku from Play Store or F-Droid, then activate it via wireless debugging or root.");
        } else if (!permGranted) {
            mTvInfo.setText("Tap \"Authorize via Shizuku\" — the Shizuku permission dialog will appear.");
        } else if (!overlayOk) {
            mTvInfo.setText("Grant \"Draw Over Other Apps\" permission, then tap Open System Console.");
        } else {
            mTvInfo.setText("All permissions granted. System Console is ready to launch.");
        }

        // Button visibility
        mBtnOpenShizuku.setVisibility(!binderAlive ? View.VISIBLE : View.GONE);
        mBtnAuthorize.setVisibility(binderAlive && !permGranted ? View.VISIBLE : View.GONE);
        mBtnLaunch.setVisibility(binderAlive && permGranted ? View.VISIBLE : View.GONE);
    }

    private void setDotColor(View dot, int color) {
        if (dot.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) dot.getBackground()).setColor(color);
        }
    }

    // ── Permission request ─────────────────────────────────────────────────────

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
            mTvInfo.setText("Could not request Shizuku permission: " + e.getMessage());
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

    // ── Launch ─────────────────────────────────────────────────────────────────

    private void launchConsole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            mTvInfo.setText("Please grant Draw Over Other Apps, then return and tap Open System Console.");
            return;
        }
        Intent intent = new Intent(this, SystemConsoleService.class);
        intent.setAction(SystemConsoleService.ACTION_SHOW);
        startService(intent);
        finish();
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
