package com.keggin.fucknjfulib.activities;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.keggin.fucknjfulib.R;
import com.keggin.fucknjfulib.auth.AuthManager;
import com.keggin.fucknjfulib.reservation.AutoFinder;
import com.keggin.fucknjfulib.reservation.SeatQuery;
import com.keggin.fucknjfulib.reservation.SeatReservation;
import com.keggin.fucknjfulib.services.LateProtectionService;
import com.keggin.fucknjfulib.storage.PreferenceManager;
import com.keggin.fucknjfulib.utils.Constants;
import com.keggin.fucknjfulib.utils.DateUtils;
import com.keggin.fucknjfulib.views.SeatMapView;
import com.keggin.fucknjfulib.views.TimelineBarView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 可视化选座页面
 * 分离自原本的 SeatQueryActivity，专注于地图化交互
 */
public class VisualSeatActivity extends AppCompatActivity {

    private static final String TAG = "VisualSeatActivity";
    private static final String FEATURE_NOTIFY_CHANNEL_ID = "feature_status_channel";
    private static final int NOTIFY_ID_AUTO_FIND = 3301;

    private Toolbar toolbar;
    private Spinner spinnerArea;
    private Spinner spinnerDate;
    private MaterialButton btnQuery;
    private SeatMapView seatMapView;
    private TextView tvEmptyHint;
    private FrameLayout loadingOverlay;
    private TextView tvZoomScale;

    private ExecutorService executor;
    private PreferenceManager preferenceManager;
    private AuthManager authManager;

    private List<String> areaKeys;
    private List<String> areaNames;
    private List<String> dateOptions;
    private String selectedDateStr;
    private Constants.AreaInfo currentAreaInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_visual_seat);

        executor = Executors.newSingleThreadExecutor();
        preferenceManager = new PreferenceManager(this);
        authManager = AuthManager.getInstance(this);

        initViews();
        setupToolbar();
        setupSpinners();
        setupClickListeners();
        // 打开即立即查询
        performQuery();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        spinnerArea = findViewById(R.id.spinnerArea);
        spinnerDate = findViewById(R.id.spinnerDate);
        btnQuery = findViewById(R.id.btnQuery);
        seatMapView = findViewById(R.id.seatMapView);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        tvZoomScale = findViewById(R.id.tvZoomScale);

        tvEmptyHint.setVisibility(View.VISIBLE);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("可视化预约");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSpinners() {
        // 区域
        areaKeys = new ArrayList<>(Constants.SEAT_AREAS_MAP.keySet());
        areaNames = new ArrayList<>();
        for (String key : areaKeys) {
            Constants.AreaInfo info = Constants.SEAT_AREAS_MAP.get(key);
            areaNames.add(info != null ? info.name : key);
        }

        ArrayAdapter<String> areaAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, areaNames);
        areaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerArea.setAdapter(areaAdapter);

        String lastArea = preferenceManager.getTargetArea();
        int idx = areaKeys.indexOf(lastArea);
        if (idx >= 0)
            spinnerArea.setSelection(idx);

        // 日期
        dateOptions = new ArrayList<>();
        dateOptions.add("今天 (" + DateUtils.getTodayDate() + ")");
        dateOptions.add("明天 (" + DateUtils.getTomorrowDate() + ")");

        ArrayAdapter<String> dateAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, dateOptions);
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDate.setAdapter(dateAdapter);
        spinnerDate.setSelection(0, false); // 默认今天
    }

    private void setupClickListeners() {
        btnQuery.setOnClickListener(v -> performQuery());

        seatMapView.setOnSeatClickListener(seat -> {
            if (seat.isAvailable()) {
                showReservationDialog(seat);
            } else {
                showOccupiedSeatDialog(seat);
            }
        });

        seatMapView.setOnZoomChangeListener(scale -> {
            if (tvZoomScale != null) {
                tvZoomScale.setText(Math.round(scale * 100) + "%");
            }
        });
    }

    private void performQuery() {
        int areaIdx = spinnerArea.getSelectedItemPosition();
        int dateIdx = spinnerDate.getSelectedItemPosition();
        if (areaIdx < 0)
            return;

        String areaKey = areaKeys.get(areaIdx);
        currentAreaInfo = Constants.SEAT_AREAS_MAP.get(areaKey);
        selectedDateStr = dateIdx == 0 ? DateUtils.getTodayDate() : DateUtils.getTomorrowDate();

        preferenceManager.setTargetArea(areaKey);
        showLoading(true);
        tvEmptyHint.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                if (!authManager.ensureLoggedIn()) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this, "认证失效，请重新登录", Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                SeatQuery query = new SeatQuery(authManager);
                SeatQuery.QueryResult result = query.querySeats(authManager.getToken(), currentAreaInfo,
                        selectedDateStr);

                runOnUiThread(() -> {
                    showLoading(false);
                    if (result.success && result.seatsData != null) {
                        seatMapView.setSeats(result.seatsData);
                        seatMapView.setRoomBackground(currentAreaInfo != null ? currentAreaInfo.roomId : -1);
                        if (result.seatsData.isEmpty()) {
                            tvEmptyHint.setText("当前区域暂无座位布局数据");
                            tvEmptyHint.setVisibility(View.VISIBLE);
                        }
                    } else {
                        Toast.makeText(this, "布局查询失败: " + result.message, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Query error", e);
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, "系统错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showOccupiedSeatDialog(SeatQuery.SeatInfo seat) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_occupied_seat, null);
        TextView tvSeatName = dialogView.findViewById(R.id.tvOccupiedSeatName);
        LinearLayout layoutSegments = dialogView.findViewById(R.id.layoutReservationSegments);
        LinearLayout layoutGapBooking = dialogView.findViewById(R.id.layoutGapBooking);
        LinearLayout layoutGapSlots = dialogView.findViewById(R.id.layoutGapSlots);
        TimelineBarView timelineBar = dialogView.findViewById(R.id.timelineBar);
        MaterialButton btnClose = dialogView.findViewById(R.id.btnOccupiedClose);

        String areaName = currentAreaInfo != null ? currentAreaInfo.name : "";
        tvSeatName.setText(areaName + " · " + seat.devName);
        // 1. Build segments list (sort by start time to fix gap calculation)
        List<SeatQuery.ReservationSlot> sortedReservations = new ArrayList<>(seat.reservations);
        sortedReservations.sort((a, b) -> Long.compare(a.startTime, b.startTime));

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());

        layoutSegments.removeAllViews();
        if (!sortedReservations.isEmpty()) {
            for (SeatQuery.ReservationSlot slot : sortedReservations) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_punish_record, layoutSegments, false);
                // Reusing item_punish_record.xml style: Title, Time, Points -> Start, End,
                // Status
                TextView tvTitle = row.findViewById(R.id.tvPunishTitle);
                TextView tvTime = row.findViewById(R.id.tvPunishTime);
                TextView tvPoints = row.findViewById(R.id.tvPunishPoints);

                tvTitle.setText(sdf.format(new java.util.Date(slot.startTime)) + " - "
                        + sdf.format(new java.util.Date(slot.endTime)));
                tvTime.setText(""); // clear time
                tvPoints.setText(slot.getStatusText());
                tvPoints.setTextColor(0xFFE53935);
                layoutSegments.addView(row);
            }

            long[] starts = new long[sortedReservations.size()];
            long[] ends = new long[sortedReservations.size()];
            for (int i = 0; i < sortedReservations.size(); i++) {
                starts[i] = sortedReservations.get(i).startTime;
                ends[i] = sortedReservations.get(i).endTime;
            }
            String closeTimeStr = DateUtils.getEndTimeWithoutSeconds(selectedDateStr) + ":00";
            timelineBar.setTimes(closeTimeStr, starts, ends);
        }

        // 2. Find Gap Slots (using sorted reservations)
        layoutGapSlots.removeAllViews();
        java.util.List<String[]> freeGaps = new ArrayList<>();

        try {
            java.util.Date dayStart = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .parse(selectedDateStr + " " + Constants.DEFAULT_START_TIME);
            java.util.Date dayEnd = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .parse(selectedDateStr + " " + DateUtils.getEndTimeWithoutSeconds(selectedDateStr));

            long tCurrent = dayStart.getTime();
            if (selectedDateStr.equals(DateUtils.getTodayDate())) {
                long nowMillis = System.currentTimeMillis();
                if (nowMillis > tCurrent) {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTimeInMillis(nowMillis);
                    int min = cal.get(java.util.Calendar.MINUTE);
                    if (min > 0 && min <= 30) {
                        cal.set(java.util.Calendar.MINUTE, 30);
                    } else if (min > 30) {
                        cal.add(java.util.Calendar.HOUR_OF_DAY, 1);
                        cal.set(java.util.Calendar.MINUTE, 0);
                    }
                    cal.set(java.util.Calendar.SECOND, 0);
                    cal.set(java.util.Calendar.MILLISECOND, 0);
                    tCurrent = cal.getTimeInMillis();
                }
            }
            long tClose = dayEnd.getTime();

            for (SeatQuery.ReservationSlot r : sortedReservations) {
                // 跳过已结束的预约（endTime 在当前游标之前）
                if (r.endTime <= tCurrent)
                    continue;
                if (r.startTime > tCurrent && r.startTime - tCurrent >= 2 * 3600000L) { // >= 2 hours
                    freeGaps.add(new String[] { sdf.format(new java.util.Date(tCurrent)),
                            sdf.format(new java.util.Date(r.startTime)) });
                }
                tCurrent = Math.max(tCurrent, r.endTime);
            }
            if (tClose - tCurrent >= 2 * 3600000L) {
                freeGaps.add(new String[] { sdf.format(new java.util.Date(tCurrent)),
                        sdf.format(new java.util.Date(tClose)) });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (freeGaps.isEmpty()) {
            layoutGapBooking.setVisibility(View.GONE);
        } else {
            layoutGapBooking.setVisibility(View.VISIBLE);
            for (String[] gap : freeGaps) {
                MaterialButton gapBtn = new MaterialButton(this);
                gapBtn.setText("预约 " + gap[0] + " - " + gap[1]);
                gapBtn.setTextSize(13f);
                gapBtn.setCornerRadius(12);
                gapBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE8F5E9));
                gapBtn.setTextColor(0xFF2E7D32);
                gapBtn.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFF81C784));
                gapBtn.setStrokeWidth(2);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 16);
                gapBtn.setLayoutParams(lp);

                AlertDialog d = new AlertDialog.Builder(this).setView(dialogView).create();
                gapBtn.setOnClickListener(v -> {
                    d.dismiss();
                    showReservationDialogForGap(seat, gap[0], gap[1]);
                });
                layoutGapSlots.addView(gapBtn);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showReservationDialogForGap(SeatQuery.SeatInfo seat, String start, String end) {
        showReservationDialog(seat, start, end);
    }

    private void showReservationDialog(SeatQuery.SeatInfo seat) {
        String defStart = preferenceManager.getStartTime();
        String defEnd = preferenceManager.getEndTime();
        String fallbackStart = (defStart != null && !defStart.trim().isEmpty())
                ? defStart
                : Constants.DEFAULT_START_TIME;

        if (selectedDateStr.equals(DateUtils.getTodayDate())) {
            long nowMillis = System.currentTimeMillis();
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(nowMillis);
            int min = cal.get(java.util.Calendar.MINUTE);
            if (min > 0 && min <= 30) {
                cal.set(java.util.Calendar.MINUTE, 30);
            } else if (min > 30) {
                cal.add(java.util.Calendar.HOUR_OF_DAY, 1);
                cal.set(java.util.Calendar.MINUTE, 0);
            }
            String curTimeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d", 
                 cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE));
            if (curTimeStr.compareTo(fallbackStart) > 0) {
                fallbackStart = curTimeStr;
            }
        }

        String fallbackEnd = DateUtils.getEndTimeWithoutSeconds(selectedDateStr);
        String finalEnd = (defEnd != null && !defEnd.trim().isEmpty()) ? defEnd : fallbackEnd;
        showReservationDialog(seat, fallbackStart, finalEnd);
    }

    private void showReservationDialog(SeatQuery.SeatInfo seat, String initialStart, String initialEnd) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_simple_reserve, null);
        TextView tvSeatInfo = dialogView.findViewById(R.id.tvSeatInfo);
        TextInputEditText etStart = dialogView.findViewById(R.id.etStartTime);
        TextInputEditText etEnd = dialogView.findViewById(R.id.etEndTime);
        MaterialButton btnCancelReserve = dialogView.findViewById(R.id.btnCancelReserve);
        MaterialButton btnConfirmReserve = dialogView.findViewById(R.id.btnConfirmReserve);

        tvSeatInfo.setText(currentAreaInfo.name + " · " + seat.devName);

        etStart.setText(initialStart);
        etEnd.setText(initialEnd);

        // TimePicker for start
        etStart.setOnClickListener(v -> {
            String cur = etStart.getText() != null ? etStart.getText().toString() : Constants.DEFAULT_START_TIME;
            int h = 7, m = 30;
            try {
                String[] p = cur.split(":");
                h = Integer.parseInt(p[0]);
                m = Integer.parseInt(p[1]);
            } catch (Exception ignored) {
            }
            new TimePickerDialog(this, (tp, hour, minute) -> etStart.setText(String.format("%02d:%02d", hour, minute)),
                    h, m, true).show();
        });

        // TimePicker for end
        etEnd.setOnClickListener(v -> {
            String fallbackEnd = DateUtils.getEndTimeWithoutSeconds(selectedDateStr);
            String cur = etEnd.getText() != null ? etEnd.getText().toString() : fallbackEnd;
            int h = Integer.parseInt(fallbackEnd.split(":")[0]), m = 0;
            try {
                String[] p = cur.split(":");
                h = Integer.parseInt(p[0]);
                m = Integer.parseInt(p[1]);
            } catch (Exception ignored) {
            }
            new TimePickerDialog(this, (tp, hour, minute) -> etEnd.setText(String.format("%02d:%02d", hour, minute)), h,
                    m, true).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        btnCancelReserve.setOnClickListener(v -> dialog.dismiss());
        btnConfirmReserve.setOnClickListener(v -> {
            String startStr = etStart.getText() != null
                    ? etStart.getText().toString()
                    : Constants.DEFAULT_START_TIME;
            String fallbackEnd = DateUtils.getEndTimeWithoutSeconds(selectedDateStr);
            String endStr = etEnd.getText() != null ? etEnd.getText().toString() : fallbackEnd;
            dialog.dismiss();
            executeReservation(seat, startStr, endStr);
        });
        dialog.show();
    }

    private void executeReservation(SeatQuery.SeatInfo seat, String start, String end) {
        showLoading(true);
        executor.execute(() -> {
            try {
                SeatReservation res = new SeatReservation(authManager);
                // 找到 seatId 索引
                int seatNumber = -1;
                for (int i = 0; i < currentAreaInfo.seatIds.length; i++) {
                    if (currentAreaInfo.seatIds[i] == seat.devId) {
                        seatNumber = i + 1;
                        break;
                    }
                }

                if (seatNumber == -1) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this, "内部错误: 未能映射座位号", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                boolean autoFindSeat = preferenceManager != null && preferenceManager.isAutoFindSeatEnabled();
                final String fDate = selectedDateStr;
                final String fStart = start;
                final String fEnd = end;
                if (autoFindSeat) {
                    AutoFinder autoFinder = new AutoFinder(authManager);
                    AutoFinder.AutoFindResult findResult = autoFinder.tryReserveWithAutoFind(
                            currentAreaInfo.name,
                            seatNumber,
                            selectedDateStr,
                            start,
                            end,
                            true);

                    runOnUiThread(() -> {
                        showLoading(false);
                        if (findResult.success) {
                            String seatDesc = (findResult.reservedSeat != null)
                                    ? findResult.reservedSeat.devName
                                    : "未知座位";
                            String successMsg = "自动寻座成功，已预约：" + seatDesc
                                    + "\n日期：" + fDate + "  时段：" + fStart + " - " + fEnd;
                            Toast.makeText(this, successMsg, Toast.LENGTH_LONG).show();
                            showFeatureNotification(NOTIFY_ID_AUTO_FIND, "自动寻座成功", successMsg);
                            scheduleLateProtectionIfEnabled();
                            performQuery(); // 刷新布局状态
                        } else {
                            String failMsg = findResult.message
                                    + "\n日期：" + fDate + "  时段：" + fStart + " - " + fEnd;
                            showFeatureNotification(NOTIFY_ID_AUTO_FIND, "自动寻座失败", failMsg);
                            new AlertDialog.Builder(this)
                                    .setTitle("预约失败")
                                    .setMessage(findResult.message)
                                    .setPositiveButton("确定", null)
                                    .show();
                        }
                    });
                } else {
                    SeatReservation.ReservationResult result = res.reserveSeat(
                            authManager.getToken(),
                            authManager.getAccNo(),
                            currentAreaInfo,
                            seatNumber,
                            start,
                            end,
                            selectedDateStr);

                    runOnUiThread(() -> {
                        showLoading(false);
                        if (result.success) {
                            Toast.makeText(this, "预约成功！", Toast.LENGTH_LONG).show();
                            scheduleLateProtectionIfEnabled();
                            performQuery(); // 刷新布局状态
                        } else {
                            new AlertDialog.Builder(this)
                                    .setTitle("预约失败")
                                    .setMessage(result.message)
                                    .setPositiveButton("确定", null)
                                    .show();
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, "预约异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showFeatureNotification(int notifyId, String title, String message) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        FEATURE_NOTIFY_CHANNEL_ID,
                        "功能状态通知",
                        NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("自动寻座等功能状态通知");
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) {
                    nm.createNotificationChannel(channel);
                }
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, FEATURE_NOTIFY_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);
            NotificationManagerCompat.from(this).notify(notifyId, builder.build());
        } catch (SecurityException ignored) {
            // Android 13+ 未授予通知权限时忽略
        }
    }

    private void scheduleLateProtectionIfEnabled() {
        if (preferenceManager == null || !preferenceManager.isLateProtectionEnabled()) {
            return;
        }
        Intent serviceIntent = new Intent(this, LateProtectionService.class);
        serviceIntent.setAction(LateProtectionService.ACTION_SCHEDULE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}