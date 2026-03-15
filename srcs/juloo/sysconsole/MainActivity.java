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
 * Below the console section, the Family Security Auditor is accessible.
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

        // ── EXISTING: System Console section ─────────────────────────────────

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

        // ── SECURITY AUDITOR DIVIDER ──────────────────────────────────────────
        root.addView(buildSecurityAuditorSection());

        setContentView(sv);
    }

    private View buildSecurityAuditorSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = dp(32);
        section.setLayoutParams(sLp);

        // Divider line
        View divider = new View(this);
        divider.setBackgroundColor(0xFFE2E8F0);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        divLp.bottomMargin = dp(24);
        section.addView(divider, divLp);

        // Header row: icon + title
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, dp(6));

        TextView tvIcon = new TextView(this);
        tvIcon.setText("🛡");
        tvIcon.setTextSize(26f);
        tvIcon.setPadding(0, 0, dp(12), 0);
        headerRow.addView(tvIcon);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView tvTitle = new TextView(this);
        tvTitle.setText("Family Security Auditor");
        tvTitle.setTextSize(18f);
        tvTitle.setTextColor(0xFF0F172A);
        tvTitle.setTypeface(tvTitle.getTypeface(), android.graphics.Typeface.BOLD);
        titleCol.addView(tvTitle);
        TextView tvTagline = new TextView(this);
        tvTagline.setText("Complete visibility, total peace of mind.");
        tvTagline.setTextSize(12f);
        tvTagline.setTextColor(0xFF64748B);
        tvTagline.setPadding(0, dp(2), 0, 0);
        titleCol.addView(tvTagline);
        headerRow.addView(titleCol);
        section.addView(headerRow);

        // Feature highlights card (dark)
        LinearLayout featCard = new LinearLayout(this);
        featCard.setOrientation(LinearLayout.VERTICAL);
        featCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable featBg = new GradientDrawable();
        featBg.setColor(0xFF1A2A3A);
        featBg.setCornerRadius(dp(14));
        featCard.setBackground(featBg);
        LinearLayout.LayoutParams fcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        fcLp.topMargin    = dp(14);
        fcLp.bottomMargin = dp(14);
        featCard.setLayoutParams(fcLp);

        String[][] features = {
            {"📊", "Risk scoring for every installed app"},
            {"📷", "Real-time camera & microphone monitoring"},
            {"📍", "Location access tracking per app"},
            {"📡", "Live feed of active sensor usage"},
            {"⚠", "Automatic security alerts & notifications"},
            {"🔍", "Deep AppOps access via Shizuku"},
        };
        for (String[] f : features) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(10);
            row.setLayoutParams(rlp);

            TextView icon = new TextView(this);
            icon.setText(f[0]);
            icon.setTextSize(15f);
            icon.setPadding(0, 0, dp(12), 0);
            row.addView(icon);

            TextView label = new TextView(this);
            label.setText(f[1]);
            label.setTextSize(13f);
            label.setTextColor(0xFFCBD5E1);
            row.addView(label);
            featCard.addView(row);
        }

        // Shizuku status chip inside the card
        boolean binderAlive = false;
        try { binderAlive = Shizuku.pingBinder(); } catch (Exception ignored) {}

        LinearLayout statusChip = new LinearLayout(this);
        statusChip.setOrientation(LinearLayout.HORIZONTAL);
        statusChip.setGravity(Gravity.CENTER_VERTICAL);
        statusChip.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(binderAlive ? 0x2200A896 : 0x22E63946);
        chipBg.setCornerRadius(dp(20));
        statusChip.setBackground(chipBg);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.topMargin = dp(6);
        statusChip.setLayoutParams(chipLp);

        View chipDot = new View(this);
        GradientDrawable dotD = new GradientDrawable();
        dotD.setShape(GradientDrawable.OVAL);
        dotD.setColor(binderAlive ? 0xFF00A896 : 0xFFE63946);
        chipDot.setBackground(dotD);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.rightMargin = dp(6);
        dotLp.gravity = Gravity.CENTER_VERTICAL;
        statusChip.addView(chipDot, dotLp);

        TextView chipText = new TextView(this);
        chipText.setText(binderAlive ? "Shizuku: Connected ✓" : "Shizuku: Not connected");
        chipText.setTextSize(11f);
        chipText.setTextColor(binderAlive ? 0xFF00A896 : 0xFFE63946);
        chipText.setTypeface(chipText.getTypeface(), android.graphics.Typeface.BOLD);
        statusChip.addView(chipText);
        featCard.addView(statusChip);

        section.addView(featCard);

        // Launch button
        Button launchBtn = new Button(this);
        launchBtn.setText("Open Security Auditor  →");
        launchBtn.setTextSize(14f);
        launchBtn.setTextColor(0xFFFFFFFF);
        launchBtn.setTypeface(launchBtn.getTypeface(), android.graphics.Typeface.BOLD);
        launchBtn.setAllCaps(false);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xFF00A896);
        btnBg.setCornerRadius(dp(12));
        launchBtn.setBackground(btnBg);
        LinearLayout.LayoutParams launchLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        launchLp.bottomMargin = dp(8);
        launchBtn.setOnClickListener(v ->
                startActivity(new Intent(this, SecurityDashboardActivity.class)));
        section.addView(launchBtn, launchLp);

        // Privacy note
        TextView privacy = new TextView(this);
        privacy.setText("🔒  All analysis stays on-device — no cloud upload.");
        privacy.setTextSize(11f);
        privacy.setTextColor(0xFF94A3B8);
        privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(0, dp(4), 0, dp(16));
        section.addView(privacy);

        return section;
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
