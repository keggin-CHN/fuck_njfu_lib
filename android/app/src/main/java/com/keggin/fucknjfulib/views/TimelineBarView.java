package com.keggin.fucknjfulib.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.keggin.fucknjfulib.R;

/**
 * A horizontal bar that visualises one or more occupied time windows across
 * the library's open hours (07:30 – openEnd).
 *
 * Usage:
 * timelineBar.setTimes("07:30", "22:00", occupiedSlots);
 */
public class TimelineBarView extends View {

    private static final int OPEN_MINUTE = 7 * 60 + 30; // 07:30

    private Paint bgPaint;
    private Paint occupiedPaint;
    private Paint textPaint;

    private int closeMinute = 22 * 60; // default 22:00
    private long[] startMs;
    private long[] endMs;

    public TimelineBarView(Context context) {
        super(context);
        init(context);
    }

    public TimelineBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(ContextCompat.getColor(context, R.color.divider));
        bgPaint.setStyle(Paint.Style.FILL);

        occupiedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        occupiedPaint.setColor(ContextCompat.getColor(context, R.color.seat_occupied));
        occupiedPaint.setStyle(Paint.Style.FILL);
        occupiedPaint.setAlpha(230);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(ContextCompat.getColor(context, R.color.text_white));
        textPaint.setTextSize(28f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * @param openEnd     "22:00" or the day's actual close time
     * @param slotStartMs epoch-ms array of occupied windows
     * @param slotEndMs   epoch-ms array of occupied windows
     */
    public void setTimes(String openEnd, long[] slotStartMs, long[] slotEndMs) {
        if (openEnd != null) {
            try {
                String[] parts = openEnd.split(":");
                closeMinute = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            } catch (Exception ignored) {
            }
        }
        this.startMs = slotStartMs;
        this.endMs = slotEndMs;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float radius = h / 2f;

        // Background track
        canvas.drawRoundRect(new RectF(0, 0, w, h), radius, radius, bgPaint);

        int totalMinutes = closeMinute - OPEN_MINUTE;
        if (totalMinutes <= 0 || startMs == null || endMs == null)
            return;

        // Draw each occupied window
        for (int i = 0; i < startMs.length && i < endMs.length; i++) {
            int startMin = msToMinuteOfDay(startMs[i]);
            int endMin = msToMinuteOfDay(endMs[i]);

            float left = Math.max(0f, (float) (startMin - OPEN_MINUTE) / totalMinutes * w);
            float right = Math.min(w, (float) (endMin - OPEN_MINUTE) / totalMinutes * w);

            if (right > left) {
                canvas.drawRoundRect(new RectF(left, 0, right, h), radius, radius, occupiedPaint);
            }
        }
    }

    private int msToMinuteOfDay(long ms) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
    }
}
