package com.example.mobileapp.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import java.util.List;

public class OverlayView extends View {
    private List<RectF> faces;
    private List<PointF> eyes;
    private List<Boolean> eyesOpen; // true - открыт, false - закрыт

    private final Paint facePaint = new Paint();
    private final Paint openEyePaint = new Paint();
    private final Paint closedEyePaint = new Paint();

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        facePaint.setColor(Color.GREEN);
        facePaint.setStyle(Paint.Style.STROKE);
        facePaint.setStrokeWidth(5);

        openEyePaint.setColor(Color.GREEN);
        openEyePaint.setStyle(Paint.Style.FILL);
        openEyePaint.setStrokeWidth(10);

        closedEyePaint.setColor(Color.RED);
        closedEyePaint.setStyle(Paint.Style.FILL);
        closedEyePaint.setStrokeWidth(10);
    }

    public void update(List<RectF> faces, List<PointF> eyes, List<Boolean> eyesOpen) {
        this.faces = faces;
        this.eyes = eyes;
        this.eyesOpen = eyesOpen;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (faces != null) {
            for (RectF face : faces) {
                canvas.drawOval(face, facePaint);
            }
        }
        if (eyes != null && eyesOpen != null) {
            for (int i = 0; i < eyes.size(); i++) {
                PointF p = eyes.get(i);
                boolean open = eyesOpen.get(i);
                canvas.drawCircle(p.x, p.y, 14, open ? openEyePaint : closedEyePaint);
            }
        }
    }
}
