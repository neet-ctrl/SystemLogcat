package juloo.sysconsole;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlertsActivity extends Activity {

    private SecurityScanManager mMgr;
    private SecurityUiHelper    mUi;
    private LinearLayout        mListContainer;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        mMgr = SecurityHub.getManager(this);
        mUi  = new SecurityUiHelper(this);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SecurityUiHelper.CLR_BG);

        root.addView(buildTopBar());

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(SecurityUiHelper.CLR_BG);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(mUi.dp(16), mUi.dp(12), mUi.dp(16), mUi.dp(24));

        mListContainer = new LinearLayout(this);
        mListContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(mListContainer);

        sv.addView(content);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(sv, svLp);

        setContentView(root);
        populateAlerts();
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) result = getResources().getDimensionPixelSize(resId);
        return Math.max(result, mUi.dp(24));
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(SecurityUiHelper.CLR_NAV_BG);
        bar.setPadding(mUi.dp(16), mUi.dp(10) + getStatusBarHeight(), mUi.dp(16), mUi.dp(10));

        Button back = new Button(this);
        back.setText("←");
        back.setTextSize(18f);
        back.setTextColor(SecurityUiHelper.CLR_TEAL);
        back.setBackground(null);
        back.setAllCaps(false);
        back.setPadding(0, 0, mUi.dp(8), 0);
        back.setOnClickListener(v -> finish());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bar.addView(back, lp);

        TextView title = mUi.label("Security Alerts", 17f, SecurityUiHelper.CLR_TEXT, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(title, tlp);

        Button clear = new Button(this);
        clear.setText("Clear All");
        clear.setTextSize(12f);
        clear.setTextColor(SecurityUiHelper.CLR_RED);
        clear.setBackground(null);
        clear.setAllCaps(false);
        clear.setOnClickListener(v -> {
            mListContainer.removeAllViews();
            mListContainer.addView(emptyView("All alerts cleared."));
        });
        bar.addView(clear);
        return bar;
    }

    private void populateAlerts() {
        mListContainer.removeAllViews();
        List<SecurityAlert> alerts = mMgr.getCachedAlerts();
        if (alerts == null || alerts.isEmpty()) {
            mListContainer.addView(emptyView("No alerts found. Run a scan from the dashboard."));
            return;
        }

        mListContainer.addView(mUi.label(alerts.size() + " ALERTS", 11f,
                SecurityUiHelper.CLR_SECONDARY, true));
        mListContainer.addView(mUi.spacer(8));

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());

        for (SecurityAlert alert : alerts) {
            if (alert.dismissed) continue;
            mListContainer.addView(buildAlertCard(alert, sdf));
            mListContainer.addView(mUi.spacer(8));
        }
    }

    private View buildAlertCard(SecurityAlert alert, SimpleDateFormat sdf) {
        LinearLayout card = mUi.card(12, 14);
        card.setClickable(true);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(cardLp);

        LinearLayout topRow = mUi.row(true);

        View severityBar = new View(this);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(mUi.dp(3),
                LinearLayout.LayoutParams.MATCH_PARENT);
        barLp.rightMargin = mUi.dp(12);
        severityBar.setLayoutParams(barLp);
        android.graphics.drawable.GradientDrawable barBg =
                new android.graphics.drawable.GradientDrawable();
        barBg.setColor(alert.severityColor());
        barBg.setCornerRadius(mUi.dp(4));
        severityBar.setBackground(barBg);

        LinearLayout textPart = new LinearLayout(this);
        textPart.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tpLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textPart.setLayoutParams(tpLp);

        LinearLayout typeRow = mUi.row(true);
        View typeBadge = mUi.badge(alert.typeLabel(),
                alert.severityColor() & 0x33FFFFFF,
                alert.severityColor(), 20);
        typeRow.addView(typeBadge);

        TextView tvTime = mUi.label(sdf.format(new Date(alert.timestamp)),
                10f, SecurityUiHelper.CLR_SECONDARY, false);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timeLp.leftMargin = mUi.dp(8);
        typeRow.addView(tvTime, timeLp);

        textPart.addView(typeRow);
        textPart.addView(mUi.spacer(4));

        TextView tvTitle = mUi.label(alert.title, 13f, SecurityUiHelper.CLR_TEXT, true);
        textPart.addView(tvTitle);

        TextView tvDesc = mUi.label(alert.description, 12f, SecurityUiHelper.CLR_SECONDARY, false);
        tvDesc.setPadding(0, mUi.dp(2), 0, 0);
        textPart.addView(tvDesc);

        topRow.addView(severityBar);
        topRow.addView(textPart);

        Button dismiss = new Button(this);
        dismiss.setText("✕");
        dismiss.setTextSize(12f);
        dismiss.setTextColor(SecurityUiHelper.CLR_SECONDARY);
        dismiss.setBackground(null);
        dismiss.setAllCaps(false);
        dismiss.setPadding(mUi.dp(8), 0, 0, 0);
        dismiss.setOnClickListener(v -> {
            alert.dismissed = true;
            LinearLayout parent = (LinearLayout) card.getParent();
            if (parent != null) {
                int idx = parent.indexOfChild(card);
                parent.removeViewAt(idx);
                if (idx < parent.getChildCount()) {
                    View spacer = parent.getChildAt(idx);
                    if (spacer != null) parent.removeViewAt(idx);
                }
            }
        });
        topRow.addView(dismiss);

        card.addView(topRow);
        return card;
    }

    private View emptyView(String msg) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(mUi.dp(16), mUi.dp(48), mUi.dp(16), mUi.dp(48));
        c.addView(mUi.label("🔔", 36f, SecurityUiHelper.CLR_SECONDARY, false));
        c.addView(mUi.spacer(8));
        c.addView(mUi.label(msg, 14f, SecurityUiHelper.CLR_SECONDARY, false));
        return c;
    }
}
