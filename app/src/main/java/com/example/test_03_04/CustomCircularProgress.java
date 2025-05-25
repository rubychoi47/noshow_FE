package com.example.test_03_04;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class CustomCircularProgress extends View {
    private int progress = 0;  // 현재 진행률 (0~100)
    private int max = 100;  // 최대값을 100으로 설정
    private int backgroundColor = 0xFFA9A9A9; //진한 회색
    private int progressColor = 0xFF0F66AE; // 진행 색 (파란색)

    private Paint backgroundPaint;
    private Paint progressPaint;
    private RectF rectF;

    public CustomCircularProgress(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(20); // 원 테두리 두께

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setColor(progressColor);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(20); // 진행률 테두리 두께
        progressPaint.setStrokeCap(Paint.Cap.ROUND); // 끝을 둥글게

        rectF = new RectF();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int size = Math.min(width, height) - 20; // 패딩을 고려한 원의 크기

        rectF.set(10, 10, size, size);  // 원 그리기 위한 좌표 설정

        // 배경 원형 테두리 (회색)
        canvas.drawArc(rectF, 0, 360, false, backgroundPaint);

        // 진행률 원형 테두리 (파란색)
        float angle = (progress / (float) max) * 360;
        canvas.drawArc(rectF, -90, angle, false, progressPaint);  // 시작각도 -90으로 설정
    }

    public void setProgress(int progress) {
        this.progress = progress;
        invalidate(); // 다시 그리기
    }
}