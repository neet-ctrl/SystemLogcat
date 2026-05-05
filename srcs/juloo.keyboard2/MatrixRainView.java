package juloo.keyboard2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

public class MatrixRainView extends View {

    private static final char[] CHARS =
        "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン0123456789ABCDEF!@#$%&*".toCharArray();

    private Paint mPaintHead, mPaintMid, mPaintDim, mPaintBg;
    private int[]    mColY;
    private int[]    mColLen;
    private char[][] mColChars;
    private int mCols, mRows;
    private Handler  mHandler;
    private Runnable mTick;
    private float    mCellPx;
    private boolean  mRunning;
    private float    mSpeedFactor = 1f;
    private float    mDensityFactor = 1f;
    private Random   mRand = new Random();

    public MatrixRainView(Context ctx) { super(ctx); init(ctx); }
    public MatrixRainView(Context ctx, AttributeSet a) { super(ctx, a); init(ctx); }

    private void init(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        mCellPx = 18f * d;

        mPaintHead = makePaint(0xFFFFFFFF);
        mPaintMid  = makePaint(0xFF00FF41);
        mPaintDim  = makePaint(0x8000BB28);
        mPaintBg   = new Paint();
        mPaintBg.setColor(0xE0000000);
        mPaintBg.setStyle(Paint.Style.FILL);

        mHandler = new Handler();
        mTick = new Runnable() {
            @Override public void run() {
                tick();
                invalidate();
                if (mRunning) mHandler.postDelayed(this, frameMs());
            }
        };
    }

    private Paint makePaint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(mCellPx * 0.85f);
        p.setTypeface(Typeface.MONOSPACE);
        return p;
    }

    public void setSpeed(String s) {
        switch (s) {
            case "slow": mSpeedFactor = 0.45f; break;
            case "fast": mSpeedFactor = 2.2f;  break;
            default:     mSpeedFactor = 1.0f;  break;
        }
    }

    public void setDensity(String d) {
        switch (d) {
            case "low":  mDensityFactor = 0.5f; break;
            case "high": mDensityFactor = 1.0f; break;
            default:     mDensityFactor = 0.75f; break;
        }
    }

    private long frameMs() { return (long)(75f / mSpeedFactor); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        mCols = Math.max(1, (int)(w / mCellPx));
        mRows = Math.max(1, (int)(h / mCellPx)) + 8;
        int activeCols = (int)(mCols * mDensityFactor);
        mColY     = new int[mCols];
        mColLen   = new int[mCols];
        mColChars = new char[mCols][mRows + 10];
        for (int c = 0; c < mCols; c++) {
            mColY[c]   = c < activeCols ? -mRand.nextInt(mRows) : Integer.MIN_VALUE;
            mColLen[c] = 8 + mRand.nextInt(12);
            for (int r = 0; r < mRows + 10; r++)
                mColChars[c][r] = CHARS[mRand.nextInt(CHARS.length)];
        }
    }

    private void tick() {
        if (mColY == null) return;
        int activeCols = (int)(mCols * mDensityFactor);
        for (int c = 0; c < mCols; c++) {
            if (mColY[c] == Integer.MIN_VALUE) continue;
            mColY[c]++;
            if (mColY[c] > mRows + mColLen[c]) {
                if (c < activeCols) {
                    mColY[c]   = -mRand.nextInt(mRows);
                    mColLen[c] = 8 + mRand.nextInt(12);
                } else {
                    mColY[c] = Integer.MIN_VALUE;
                }
            }
            if (mRand.nextInt(4) == 0) {
                int r = mRand.nextInt(mRows + 10);
                mColChars[c][r] = CHARS[mRand.nextInt(CHARS.length)];
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), mPaintBg);
        if (mColY == null) return;
        for (int c = 0; c < mCols; c++) {
            int head = mColY[c];
            if (head == Integer.MIN_VALUE) continue;
            float x = c * mCellPx;
            int len = mColLen[c];
            for (int t = len; t >= 1; t--) {
                int row = head - t;
                if (row < 0 || row >= mRows) continue;
                float y = (row + 1) * mCellPx;
                Paint p = (t <= 2) ? mPaintMid : mPaintDim;
                canvas.drawText(String.valueOf(mColChars[c][row % (mRows + 10)]), x, y, p);
            }
            if (head >= 0 && head < mRows) {
                float y = (head + 1) * mCellPx;
                canvas.drawText(String.valueOf(mColChars[c][head % (mRows + 10)]), x, y, mPaintHead);
            }
        }
    }

    public void startAnimation() {
        if (!mRunning) { mRunning = true; mHandler.post(mTick); }
    }

    public void stopAnimation() {
        mRunning = false;
        mHandler.removeCallbacks(mTick);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); startAnimation(); }
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); stopAnimation(); }
    @Override protected void onVisibilityChanged(View v, int vis) {
        super.onVisibilityChanged(v, vis);
        if (vis == VISIBLE) startAnimation(); else stopAnimation();
    }
}
