package com.modoespia.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class RadarView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float angle = -90f;

    public RadarView(Context context) {
        super(context);
        init();
    }

    public RadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        gridPaint.setColor(Color.rgb(0, 104, 74));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2f);

        sweepPaint.setColor(Color.rgb(0, 255, 170));
        sweepPaint.setStrokeWidth(3.5f);
        sweepPaint.setShadowLayer(14f, 0f, 0f, Color.rgb(0, 255, 170));

        glowPaint.setColor(Color.argb(48, 0, 255, 170));
        glowPaint.setStyle(Paint.Style.FILL);

        dotPaint.setColor(Color.rgb(160, 255, 220));
        dotPaint.setShadowLayer(12f, 0f, 0f, Color.rgb(0, 255, 170));

        textPaint.setColor(Color.rgb(76, 154, 128));
        textPaint.setTextSize(24f);

        post(animator);
    }

    private final Runnable animator = new Runnable() {
        @Override public void run() {
            angle += 1.8f;
            if (angle >= 270f) angle = -90f;
            invalidate();
            postDelayed(this, 16);
        }
    };

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(animator);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) * 0.40f;

        for (int i = 1; i <= 4; i++) {
            float rr = r * i / 4f;
            gridPaint.setAlpha(i == 4 ? 220 : 145);
            canvas.drawCircle(cx, cy, rr, gridPaint);
        }

        gridPaint.setAlpha(145);
        canvas.drawLine(cx - r, cy, cx + r, cy, gridPaint);
        canvas.drawLine(cx, cy - r, cx, cy + r, gridPaint);
        canvas.drawLine(cx - r * .70f, cy - r * .70f, cx + r * .70f, cy + r * .70f, gridPaint);
        canvas.drawLine(cx - r * .70f, cy + r * .70f, cx + r * .70f, cy - r * .70f, gridPaint);

        double rad = Math.toRadians(angle);
        float x = cx + (float)Math.cos(rad) * r;
        float y = cy + (float)Math.sin(rad) * r;
        canvas.drawCircle(cx, cy, r * .09f, glowPaint);
        canvas.drawLine(cx, cy, x, y, sweepPaint);

        drawDot(canvas, cx + r * .32f, cy - r * .22f, 7f);
        drawDot(canvas, cx - r * .18f, cy + r * .43f, 5f);
        drawDot(canvas, cx + r * .12f, cy + r * .14f, 4f);

        canvas.drawText("N", cx - 8f, cy - r - 14f, textPaint);
        canvas.drawText("S", cx - 8f, cy + r + 32f, textPaint);
        canvas.drawText("W", cx - r - 34f, cy + 8f, textPaint);
        canvas.drawText("E", cx + r + 14f, cy + 8f, textPaint);
    }

    private void drawDot(Canvas canvas, float x, float y, float radius) {
        canvas.drawCircle(x, y, radius * 2.3f, glowPaint);
        canvas.drawCircle(x, y, radius, dotPaint);
    }
}
