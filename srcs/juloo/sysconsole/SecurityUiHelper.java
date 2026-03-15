package juloo.sysconsole;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SecurityUiHelper {

    public static final int CLR_BG        = 0xFF1A2A3A;
    public static final int CLR_CARD      = 0xFF243447;
    public static final int CLR_CARD2     = 0xFF1E3048;
    public static final int CLR_TEAL      = 0xFF00A896;
    public static final int CLR_ORANGE    = 0xFFFF9F1C;
    public static final int CLR_RED       = 0xFFE63946;
    public static final int CLR_GREEN     = 0xFF22C55E;
    public static final int CLR_TEXT      = 0xFFE2E8F0;
    public static final int CLR_SECONDARY = 0xFF94A3B8;
    public static final int CLR_DIVIDER   = 0xFF2D4A63;
    public static final int CLR_NAV_BG    = 0xFF162030;

    private final Context ctx;
    private final float density;

    public SecurityUiHelper(Context ctx) {
        this.ctx     = ctx;
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    public int dp(int dp) {
        return Math.round(dp * density);
    }

    public LinearLayout card(int radiusDp, int extraPaddingDp) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = dp(extraPaddingDp > 0 ? extraPaddingDp : 16);
        c.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CLR_CARD);
        bg.setCornerRadius(dp(radiusDp > 0 ? radiusDp : 12));
        c.setBackground(bg);
        return c;
    }

    public LinearLayout card() { return card(12, 16); }

    public View divider() {
        View v = new View(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin    = dp(10);
        lp.bottomMargin = dp(10);
        v.setLayoutParams(lp);
        v.setBackgroundColor(CLR_DIVIDER);
        return v;
    }

    public TextView label(String text, float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }

    public TextView sectionHeader(String text) {
        TextView tv = label(text, 11f, CLR_SECONDARY, true);
        tv.setLetterSpacing(0.1f);
        tv.setPadding(0, dp(6), 0, dp(8));
        return tv;
    }

    public View badge(String text, int bgColor, int textColor, int radiusDp) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(10f);
        tv.setTextColor(textColor);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        int ph = dp(6), pv = dp(2);
        tv.setPadding(ph, pv, ph, pv);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(radiusDp > 0 ? radiusDp : 20));
        tv.setBackground(bg);
        return tv;
    }

    public View riskBadge(AppSecurityInfo info) {
        return badge(info.riskLevelLabel(), info.riskColor(), 0xFFFFFFFF, 20);
    }

    public View colorDot(int color, int sizeDp) {
        View v = new View(ctx);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        v.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        lp.rightMargin = dp(8);
        lp.gravity     = Gravity.CENTER_VERTICAL;
        v.setLayoutParams(lp);
        return v;
    }

    public LinearLayout row(boolean centerV) {
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(LinearLayout.HORIZONTAL);
        if (centerV) r.setGravity(Gravity.CENTER_VERTICAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return r;
    }

    public View spacer(int dp) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(dp)));
        return v;
    }

    /** Fixed-width spacer for use inside horizontal LinearLayouts. */
    public View hspacer(int dp) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(dp),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return v;
    }

    public LinearLayout statCard(String value, String labelText, int accentColor) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(12), dp(14), dp(12), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CLR_CARD);
        bg.setCornerRadius(dp(12));
        GradientDrawable border = new GradientDrawable();
        border.setColor(CLR_CARD);
        border.setCornerRadius(dp(12));
        border.setStroke(dp(1), accentColor & 0x55FFFFFF | (accentColor & 0xFF000000));
        c.setBackground(border);

        TextView tvVal = new TextView(ctx);
        tvVal.setText(value);
        tvVal.setTextSize(22f);
        tvVal.setTextColor(accentColor);
        tvVal.setTypeface(tvVal.getTypeface(), Typeface.BOLD);
        tvVal.setGravity(Gravity.CENTER);
        c.addView(tvVal);

        TextView tvLbl = new TextView(ctx);
        tvLbl.setText(labelText);
        tvLbl.setTextSize(10f);
        tvLbl.setTextColor(CLR_SECONDARY);
        tvLbl.setGravity(Gravity.CENTER);
        tvLbl.setPadding(0, dp(2), 0, 0);
        c.addView(tvLbl);

        return c;
    }

    public android.widget.Button primaryButton(String text, int fillColor) {
        android.widget.Button btn = new android.widget.Button(ctx);
        btn.setText(text);
        btn.setTextSize(13f);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTypeface(btn.getTypeface(), Typeface.BOLD);
        btn.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fillColor);
        bg.setCornerRadius(dp(10));
        btn.setBackground(bg);
        return btn;
    }

    public View rippleBg(int color, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radiusDp));
        View v = new View(ctx);
        v.setBackground(bg);
        return v;
    }

    public LinearLayout permissionRow(String permName, String desc, boolean granted, boolean usedRecently) {
        LinearLayout row = row(true);
        row.setPadding(0, dp(6), 0, dp(6));

        int dotColor = granted ? (usedRecently ? CLR_RED : CLR_GREEN) : 0xFF475569;
        row.addView(colorDot(dotColor, 8));

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(lp);

        TextView tvName = label(permName, 13f, CLR_TEXT, granted);
        textCol.addView(tvName);
        if (!desc.isEmpty()) {
            TextView tvDesc = label(desc, 11f, CLR_SECONDARY, false);
            tvDesc.setPadding(0, dp(1), 0, 0);
            textCol.addView(tvDesc);
        }
        row.addView(textCol);

        if (usedRecently && granted) {
            View b = badge("Used Recently", 0x33E63946, CLR_RED, 20);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.leftMargin = dp(8);
            row.addView(b, blp);
        } else if (!granted) {
            View b = badge("Denied", 0x22475569, CLR_SECONDARY, 20);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.leftMargin = dp(8);
            row.addView(b, blp);
        }
        return row;
    }
}
