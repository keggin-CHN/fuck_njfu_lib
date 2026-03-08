package com.keggin.fucknjfulib.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

public class SeatMapView extends View {
    private static final String TAG = "SeatMapView";
    private static final float POINT_SIZE = 14f;
    private List<SeatQuery.SeatInfo> seats = new ArrayList<>();
    private List<TablePair> tables = new ArrayList<>();
    private Bitmap backgroundBitmap = null;
    private final RectF roomRect = new RectF();
    private Paint bitmapPaint;
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
    private Paint seatBorderPaint;
    private float scaleFactor = 1.0f;
    private float translateX = 0f;
    private float translateY = 0f;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float density;
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
        bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bitmapPaint.setFilterBitmap(true);
        
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
        seatTextPaint.setTextSize(POINT_SIZE * 0.8f);
        seatTextPaint.setTextAlign(Paint.Align.CENTER);
        seatTextPaint.setFakeBoldText(true);

        seatBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        seatBorderPaint.setColor(Color.parseColor("#33000000"));
        seatBorderPaint.setStyle(Paint.Style.STROKE);
        seatBorderPaint.setStrokeWidth(1f * density);

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
                float nextScale;
                if (scaleFactor < 1.8f) {
                    nextScale = 2.0f;
                } else if (scaleFactor < 3.2f) {
                    nextScale = 3.5f;
                } else {
                    nextScale = 1.0f;
                }

                float oldScale = Math.max(0.5f, scaleFactor);
                if (nextScale == 1.0f) {
                    translateX = 0f;
                    translateY = 0f;
                } else {
                    float focusX = e.getX();
                    float focusY = e.getY();
                    translateX = focusX - ((focusX - translateX) / oldScale) * nextScale;
                    translateY = focusY - ((focusY - translateY) / oldScale) * nextScale;
                }
                scaleFactor = nextScale;
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
        invalidate();
    }
    
    public void setRoomBackground(int roomId) {
        if (roomId <= 0) {
            backgroundBitmap = null;
            invalidate();
            return;
        }
        String resName = "room_bg_" + roomId;
        int resId = getResources().getIdentifier(resName, "drawable", getContext().getPackageName());
        if (resId == 0) {
            Log.w(TAG, "未找到本地背景图资源: " + resName);
            backgroundBitmap = null;
            invalidate();
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resId);
        if (bitmap == null) {
            Log.e(TAG, "本地背景图解码失败: " + resName);
            backgroundBitmap = null;
            invalidate();
            return;
        }
        backgroundBitmap = bitmap;
        Log.d(TAG, "本地背景图加载成功: " + resName + " " + bitmap.getWidth() + "x" + bitmap.getHeight());
        invalidate();
    }

    public void setBackground(SeatQuery.RoomBackground background) {
        // 保留兼容接口，背景图改为按 roomId 从 drawable 本地加载
        if (background == null) {
            backgroundBitmap = null;
            invalidate();
        }
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
                if (xg < 1.5f && yg > 2.0f && yg < 6.0f) {
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
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int vw = getWidth(), vh = getHeight();
        if (vw == 0 || vh == 0) return;

        canvas.drawRect(0, 0, vw, vh, bgPaint);

        float roomRatio = (backgroundBitmap != null)
                ? (float) backgroundBitmap.getWidth() / backgroundBitmap.getHeight()
                : 16f / 9f;
        float viewRatio = (float) vw / vh;

        float rw, rh, rl, rt;
        if (viewRatio > roomRatio) {
            rh = vh;
            rw = rh * roomRatio;
            rl = (vw - rw) / 2f;
            rt = 0f;
        } else {
            rw = vw;
            rh = rw / roomRatio;
            rl = 0f;
            rt = (vh - rh) / 2f;
        }
        roomRect.set(rl, rt, rl + rw, rt + rh);

        canvas.save();
        canvas.translate(translateX, translateY);
        canvas.scale(scaleFactor, scaleFactor, roomRect.centerX(), roomRect.centerY());

        canvas.drawRect(roomRect, roomBgPaint);
        if (backgroundBitmap != null) {
            bitmapPaint.setAlpha(166); // 对齐 HTML bg-image 的 opacity: 0.65
            canvas.drawBitmap(backgroundBitmap, null, roomRect, bitmapPaint);
            bitmapPaint.setAlpha(255);
        }

        float seatSize = POINT_SIZE; // 对齐 HTML seat width/height=14px
        float radius = seatSize / 2f;

        for (SeatQuery.SeatInfo seat : seats) {
            if (seat.coordX < 0 || seat.coordY < 0) continue;

            // 对齐 HTML: left/top 是座位左上角锚点（默认非 center-anchor）
            float left = roomRect.left + (seat.coordX / 100f) * roomRect.width();
            float top = roomRect.top + (seat.coordY / 100f) * roomRect.height();
            float cx = left + radius;
            float cy = top + radius;

            Paint p = seat == selectedSeat ? selectedPaint : seat.isAvailable() ? availablePaint : occupiedPaint;
            canvas.drawCircle(cx, cy, radius, p);
            canvas.drawCircle(cx, cy, radius, seatBorderPaint);

            if (seat == selectedSeat) {
                canvas.drawCircle(cx, cy, radius + 3f, selectedStrokePaint);
            }

            String seatNum = extractNum(seat.devName);
            if (seatNum != null && !seatNum.trim().isEmpty()) {
                float textSize = seatNum.length() >= 3 ? POINT_SIZE * 0.55f : POINT_SIZE * 0.7f;
                seatTextPaint.setTextSize(textSize);
                Paint.FontMetrics fm = seatTextPaint.getFontMetrics();
                float baseline = cy - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(seatNum, cx, baseline, seatTextPaint);
            }
        }

        canvas.restore();
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
        if (roomRect.isEmpty()) return;

        // 逆变换：先去平移，再围绕 roomRect 中心逆缩放
        float sx = (tapX - translateX - roomRect.centerX()) / scaleFactor + roomRect.centerX();
        float sy = (tapY - translateY - roomRect.centerY()) / scaleFactor + roomRect.centerY();

        float seatSize = POINT_SIZE;
        float radius = seatSize / 2f;

        float bestDist = Float.MAX_VALUE;
        SeatQuery.SeatInfo best = null;

        for (SeatQuery.SeatInfo seat : seats) {
            if (seat.coordX < 0 || seat.coordY < 0) continue;

            float left = roomRect.left + (seat.coordX / 100f) * roomRect.width();
            float top = roomRect.top + (seat.coordY / 100f) * roomRect.height();
            float cx = left + radius;
            float cy = top + radius;

            float dx = sx - cx;
            float dy = sy - cy;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float hitR = Math.max(radius, 14f * density / Math.max(scaleFactor, 0.5f));

            if (d <= hitR && d < bestDist) {
                bestDist = d;
                best = seat;
            }
        }

        if (best != null) {
            selectedSeat = best;
            invalidate();
            if (onSeatClickListener != null) onSeatClickListener.onSeatClick(best);
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
