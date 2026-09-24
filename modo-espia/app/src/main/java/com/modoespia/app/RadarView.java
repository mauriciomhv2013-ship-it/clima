package com.modoespia.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class RadarView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float angle = 0f;

    public RadarView(Context context) {
        super(context);
        init();
    }

    public RadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setBackgroundColor(Color.rgb(2, 10, 8));
        gridPaint.setColor(Color.rgb(0, 130, 90));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2f);
        sweepPaint.setColor(Color.rgb(0, 255, 170));
        sweepPaint.setStrokeWidth(4f);
        dotPaint.setColor(Color.rgb(130, 255, 210));
        post(animator);
    }

    private final Runnable animator = new Runnable() {
        @Override public void run() {
            angle += 2.2f;
            if (angle >= 360f) angle = 0f;
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
        float r = Math.min(w, h) * 0.43f;

        for (int i = 1; i <= 4; i++) {
            float rr = r * i / 4f;
            canvas.drawCircle(cx, cy, rr, gridPaint);
        }
        canvas.drawLine(cx - r, cy, cx + r, cy, gridPaint);
        canvas.drawLine(cx, cy - r, cx, cy + r, gridPaint);

        double rad = Math.toRadians(angle);
        float x = cx + (float)Math.cos(rad) * r;
        float y = cy + (float)Math.sin(rad) * r;
        canvas.drawLine(cx, cy, x, y, sweepPaint);

        canvas.drawCircle(cx + r * 0.35f, cy - r * 0.2f, 7f, dotPaint);
        canvas.drawCircle(cx - r * 0.2f, cy + r * 0.45f, 5f, dotPaint);
    }
}
