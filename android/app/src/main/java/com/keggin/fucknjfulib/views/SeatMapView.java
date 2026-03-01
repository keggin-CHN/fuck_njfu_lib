package com.keggin.fucknjfulib.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.keggin.fucknjfulib.reservation.SeatQuery;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 可视化座位地图 — 仿网页版
 * 原理：与网页一致, 使用百分比坐标定位(left%, top%)
 * 内部使用固定虚拟画布(1082x700), 座位圆点大小为12px
 * 支持双指缩放和拖动, 自动检测桌子配对
 */
public class SeatMapView extends View {

    private static final String TAG = "SeatMapView";

    // 虚拟画布 — 网页源码里 boxWidth 参考值就是 1082
    private static final float BOX_W = 1082f;
    private static final float BOX_H = 700f;
    // 网页的 pointSize = 12
    private static final float POINT_SIZE = 12f;

    private List<SeatQuery.SeatInfo> seats = new ArrayList<>();
    private List<TablePair> tables = new ArrayList<>();

    // 画笔
    private Paint availablePaint;
    private Paint occupiedPaint;
    private Paint selectedPaint;
    private Paint selectedStrokePaint;
    private Paint bgPaint;
    private Paint roomBgPaint;
    private Paint tablePaint;
    private Paint tableStrokePaint;
    private Paint wallPaint;
    private Paint legendPaint;
    private Paint legendTextPaint;
    private Paint seatTextPaint;
    private RectF tmpRect = new RectF();

    // 手势
    private float scaleFactor = 1.0f;
    private float translateX = 0f;
    private float translateY = 0f;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private float density;

    // 选中
    private SeatQuery.SeatInfo selectedSeat = null;
    private OnSeatClickListener onSeatClickListener;

    public interface OnSeatClickListener {
        void onSeatClick(SeatQuery.SeatInfo seat);
    }

    private static class TablePair {
        SeatQuery.SeatInfo seat1, seat2;

        TablePair(SeatQuery.SeatInfo s1, SeatQuery.SeatInfo s2) {
            seat1 = s1;
            seat2 = s2;
        }
    }

    public SeatMapView(Context c) {
        super(c);
        init(c);
    }

    public SeatMapView(Context c, AttributeSet a) {
        super(c, a);
        init(c);
    }

    public SeatMapView(Context c, AttributeSet a, int d) {
        super(c, a, d);
        init(c);
    }

    private void init(Context ctx) {
        density = ctx.getResources().getDisplayMetrics().density;

        availablePaint = makePaint("#4CAF50");
        occupiedPaint = makePaint("#FF9800");
        selectedPaint = makePaint("#2196F3");

        selectedStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedStrokePaint.setColor(Color.parseColor("#0D47A1"));
        selectedStrokePaint.setStyle(Paint.Style.STROKE);
        selectedStrokePaint.setStrokeWidth(3f);

        bgPaint = makePaint("#E8E8E8");
        roomBgPaint = makePaint("#FFFFFF");

        tablePaint = makePaint("#C8A96E");
        tableStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tableStrokePaint.setColor(Color.parseColor("#A68B5B"));
        tableStrokePaint.setStyle(Paint.Style.STROKE);
        tableStrokePaint.setStrokeWidth(1f);

        wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallPaint.setColor(Color.parseColor("#999999"));
        wallPaint.setStyle(Paint.Style.STROKE);
        wallPaint.setStrokeWidth(2f);

        seatTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        seatTextPaint.setColor(Color.WHITE);
        seatTextPaint.setTextSize(POINT_SIZE * 0.5f);
        seatTextPaint.setTextAlign(Paint.Align.CENTER);
        seatTextPaint.setFakeBoldText(true);

        legendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legendPaint.setStyle(Paint.Style.FILL);

        legendTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legendTextPaint.setColor(Color.parseColor("#666666"));
        legendTextPaint.setTextSize(11 * density);
        legendTextPaint.setTextAlign(Paint.Align.LEFT);

        scaleDetector = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                scaleFactor *= d.getScaleFactor();
                scaleFactor = Math.max(0.5f, Math.min(8.0f, scaleFactor));
                invalidate();
                return true;
            }
        });

        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (e1 == null)
                    return false;
                translateX -= dx;
                translateY -= dy;
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                handleTap(e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                scaleFactor = 1.0f;
                translateX = 0f;
                translateY = 0f;
                invalidate();
                return true;
            }
        });
    }

    private Paint makePaint(String color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.parseColor(color));
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    public void setSeats(List<SeatQuery.SeatInfo> seatList) {
        seats = seatList != null ? seatList : new ArrayList<>();
        selectedSeat = null;
        scaleFactor = 1.0f;
        translateX = 0f;
        translateY = 0f;
        detectTables();
        invalidate();
    }

    public SeatQuery.SeatInfo getSelectedSeat() {
        return selectedSeat;
    }

    private void detectTables() {
        tables.clear();
        if (seats.isEmpty())
            return;
        Set<Integer> paired = new HashSet<>();
        List<SeatQuery.SeatInfo> sorted = new ArrayList<>(seats);
        sorted.sort((a, b) -> {
            int c = Float.compare(a.coordX, b.coordX);
            return c != 0 ? c : Float.compare(a.coordY, b.coordY);
        });
        for (int i = 0; i < sorted.size(); i++) {
            SeatQuery.SeatInfo s1 = sorted.get(i);
            if (s1.coordX < 0 || paired.contains(s1.devId))
                continue;
            for (int j = i + 1; j < sorted.size(); j++) {
                SeatQuery.SeatInfo s2 = sorted.get(j);
                if (s2.coordX < 0 || paired.contains(s2.devId))
                    continue;
                float xg = Math.abs(s1.coordX - s2.coordX);
                float yg = Math.abs(s1.coordY - s2.coordY);
                if (xg < 1.5f && yg > 2.0f && yg < 5.0f) {
                    tables.add(new TablePair(s1, s2));
                    paired.add(s1.devId);
                    paired.add(s2.devId);
                    break;
                }
            }
        }
    }

    public void setOnSeatClickListener(OnSeatClickListener l) {
        onSeatClickListener = l;
    }

    // 百分比坐标 → 虚拟画布坐标
    private float cx(float pct) {
        return (pct / 100f) * BOX_W;
    }

    private float cy(float pct) {
        return (pct / 100f) * BOX_H;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int vw = getWidth(), vh = getHeight();
        if (vw == 0 || vh == 0 || seats.isEmpty())
            return;

        float legendH = 32 * density;
        float drawH = vh - legendH;

        // 适配缩放: 虚拟画布填满视图宽度
        float baseScale = vw / BOX_W;
        float bsH = drawH / BOX_H;
        baseScale = Math.min(baseScale, bsH);
        float ts = baseScale * scaleFactor;

        // 灰色外围背景
        canvas.drawRect(0, 0, vw, vh, bgPaint);

        canvas.save();
        // 将虚拟画布居中显示在视图中
        float offX = (vw - BOX_W * baseScale) / 2f;
        float offY = (drawH - BOX_H * baseScale) / 2f;
        canvas.translate(offX + translateX, offY + translateY);
        canvas.scale(ts, ts);

        // 白色房间
        tmpRect.set(0, 0, BOX_W, BOX_H);
        canvas.drawRect(tmpRect, roomBgPaint);
        canvas.drawRect(tmpRect, wallPaint);

        // 桌子
        float halfPt = POINT_SIZE / 2f;
        for (TablePair t : tables) {
            float x1 = cx(t.seat1.coordX), y1 = cy(t.seat1.coordY);
            float x2 = cx(t.seat2.coordX), y2 = cy(t.seat2.coordY);
            float tL = Math.min(x1, x2) - halfPt * 0.5f;
            float tR = Math.max(x1, x2) + halfPt * 0.5f;
            float tT = Math.min(y1, y2) + POINT_SIZE + 1;
            float tB = Math.max(y1, y2) - POINT_SIZE - 1;
            if (tT < tB) {
                tmpRect.set(tL, tT, tR, tB);
                canvas.drawRoundRect(tmpRect, 2, 2, tablePaint);
                canvas.drawRoundRect(tmpRect, 2, 2, tableStrokePaint);
            }
        }

        // 座位 (圆点, 和网页一样大小 12px)
        for (SeatQuery.SeatInfo seat : seats) {
            if (seat.coordX < 0 || seat.coordY < 0)
                continue;
            float sx = cx(seat.coordX), sy = cy(seat.coordY);

            Paint p = seat == selectedSeat ? selectedPaint : seat.isAvailable() ? availablePaint : occupiedPaint;
            canvas.drawCircle(sx, sy, halfPt, p);

            if (seat == selectedSeat) {
                canvas.drawCircle(sx, sy, halfPt + 3, selectedStrokePaint);
            }

            // 缩放大时显示座位号
            if (ts > 1.2f) {
                String num = extractNum(seat.devName);
                if (num != null)
                    canvas.drawText(num, sx, sy + seatTextPaint.getTextSize() * 0.35f, seatTextPaint);
            }
        }

        canvas.restore();

        // 图例
        drawLegend(canvas, vw, vh);
    }

    private void drawLegend(Canvas canvas, int w, int h) {
        float y = h - 8 * density;
        float x = 10 * density;
        float r = 5 * density;
        float g = 5 * density;
        float sp = 72 * density;

        legendPaint.setColor(Color.parseColor("#4CAF50"));
        canvas.drawCircle(x + r, y - r, r, legendPaint);
        canvas.drawText("空闲", x + r * 2 + g, y, legendTextPaint);

        x += sp;
        legendPaint.setColor(Color.parseColor("#FF9800"));
        canvas.drawCircle(x + r, y - r, r, legendPaint);
        canvas.drawText("使用中", x + r * 2 + g, y, legendTextPaint);

        x += sp + 12 * density;
        legendPaint.setColor(Color.parseColor("#2196F3"));
        canvas.drawCircle(x + r, y - r, r, legendPaint);
        canvas.drawText("选中", x + r * 2 + g, y, legendTextPaint);

        // 统计
        int a = 0;
        for (SeatQuery.SeatInfo s : seats)
            if (s.isAvailable())
                a++;
        String st = a + "/" + seats.size();
        canvas.drawText(st, w - legendTextPaint.measureText(st) - 10 * density, y, legendTextPaint);
    }

    private String extractNum(String name) {
        if (name == null)
            return null;
        int s = -1;
        for (int i = name.length() - 1; i >= 0; i--) {
            if (Character.isDigit(name.charAt(i)))
                s = i;
            else
                break;
        }
        if (s >= 0)
            try {
                return String.valueOf(Integer.parseInt(name.substring(s)));
            } catch (Exception e) {
            }
        return null;
    }

    private void handleTap(float tapX, float tapY) {
        int vw = getWidth(), vh = getHeight();
        float legendH = 32 * density;
        float drawH = vh - legendH;
        float baseScale = Math.min(vw / BOX_W, drawH / BOX_H);
        float ts = baseScale * scaleFactor;
        float offX = (vw - BOX_W * baseScale) / 2f + translateX;
        float offY = (drawH - BOX_H * baseScale) / 2f + translateY;

        float bestDist = Float.MAX_VALUE;
        SeatQuery.SeatInfo best = null;

        for (SeatQuery.SeatInfo seat : seats) {
            if (seat.coordX < 0 || seat.coordY < 0)
                continue;
            float sx = cx(seat.coordX) * ts + offX;
            float sy = cy(seat.coordY) * ts + offY;
            float d = (float) Math.sqrt((tapX - sx) * (tapX - sx) + (tapY - sy) * (tapY - sy));
            float hitR = Math.max(POINT_SIZE / 2f * ts, 14 * density);
            if (d < hitR && d < bestDist) {
                bestDist = d;
                best = seat;
            }
        }

        if (best != null) {
            selectedSeat = best;
            invalidate();
            if (onSeatClickListener != null)
                onSeatClickListener.onSeatClick(best);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        getParent().requestDisallowInterceptTouchEvent(true);
        scaleDetector.onTouchEvent(e);
        gestureDetector.onTouchEvent(e);
        if (e.getAction() == MotionEvent.ACTION_UP)
            performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
