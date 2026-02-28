package com.keggin.fucknjfulib.activities;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.keggin.fucknjfulib.R;
import com.keggin.fucknjfulib.auth.AuthManager;
import com.keggin.fucknjfulib.reservation.SeatReservation;
import com.keggin.fucknjfulib.reservation.TrafficQuery;
import com.keggin.fucknjfulib.storage.PreferenceManager;
import com.keggin.fucknjfulib.utils.Constants;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class DashboardActivity extends AppCompatActivity {
    private Toolbar toolbar;
    private ImageButton btnSettings; private android.widget.TextView btnLogs;
    private MaterialCardView cardCurrentReservation;
    private LinearLayout layoutNoReservation;
    private TextView tvNoReservationText;
    private LinearLayout layoutReservationInfo;
    private TextView tvReservationSeat;
    private TextView tvReservationTime;
    private TextView tvReservationStatus;
    private MaterialButton btnSignIn;
    private MaterialButton btnCancelReservation;
    private MaterialCardView cardReserveNow;
    private MaterialCardView cardQuerySeats;
    private MaterialCardView cardCheckTraffic;
    private MaterialCardView cardSettings;
    private ImageView ivAutoReserveStatus;
    private TextView tvAutoReserveStatus;
    private ImageView ivLateProtectionStatus;
    private TextView tvLateProtectionStatus;
    private ImageView ivAutoFindStatus;
    private TextView tvAutoFindStatus;
    private FrameLayout loadingOverlay;
    private ExecutorService executor;
    private PreferenceManager preferenceManager;
    private SeatReservation.ReservationInfo currentReservation;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        executor = Executors.newSingleThreadExecutor();
        preferenceManager = new PreferenceManager(this);
        initViews();
        setupClickListeners();
    }
    @Override
    protected void onResume() {
        super.onResume();
        boolean hasCache = loadCachedReservation();
        loadCurrentReservation(!hasCache);
        updateAutoStatus();
    }
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        btnSettings = findViewById(R.id.btnSettings); btnLogs = findViewById(R.id.btnLogs);
        cardCurrentReservation = findViewById(R.id.cardCurrentReservation);
        layoutNoReservation = findViewById(R.id.layoutNoReservation);
        tvNoReservationText = findViewById(R.id.tvNoReservationText);
        layoutReservationInfo = findViewById(R.id.layoutReservationInfo);
        tvReservationSeat = findViewById(R.id.tvReservationSeat);
        tvReservationTime = findViewById(R.id.tvReservationTime);
        tvReservationStatus = findViewById(R.id.tvReservationStatus);
        btnSignIn = findViewById(R.id.btnSignIn);
        btnCancelReservation = findViewById(R.id.btnCancelReservation);
        cardReserveNow = findViewById(R.id.cardReserveNow);
        cardQuerySeats = findViewById(R.id.cardQuerySeats);
        cardCheckTraffic = findViewById(R.id.cardCheckTraffic);
        cardSettings = findViewById(R.id.cardSettings);
        ivAutoReserveStatus = findViewById(R.id.ivAutoReserveStatus);
        tvAutoReserveStatus = findViewById(R.id.tvAutoReserveStatus);
        ivLateProtectionStatus = findViewById(R.id.ivLateProtectionStatus);
        tvLateProtectionStatus = findViewById(R.id.tvLateProtectionStatus);
        ivAutoFindStatus = findViewById(R.id.ivAutoFindStatus);
        tvAutoFindStatus = findViewById(R.id.tvAutoFindStatus);
        loadingOverlay = findViewById(R.id.loadingOverlay);
    }
    private void setupClickListeners() {
        btnSettings.setOnClickListener(v -> navigateToSettings()); btnLogs.setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));
        cardReserveNow.setOnClickListener(v -> reserveNow());
        cardQuerySeats.setOnClickListener(v -> navigateToSeatQuery());
        cardCheckTraffic.setOnClickListener(v -> checkTraffic());
        cardSettings.setOnClickListener(v -> navigateToSettings());
        btnSignIn.setOnClickListener(v -> signIn());
        btnCancelReservation.setOnClickListener(v -> cancelReservation());
    }
    private void loadCurrentReservation() {
        loadCurrentReservation(true);
    }
    private void loadCurrentReservation(boolean showOverlay) {
        if (showOverlay) {
            showLoading(true);
        }
        executor.execute(() -> {
            try {
                AuthManager authManager = AuthManager.getInstance(this);
                if (!authManager.ensureLoggedIn()) {
                    runOnUiThread(() -> {
                        if (showOverlay) {
                            showLoading(false);
                        }
                        Toast.makeText(this, "登录已过期，请重新登录", Toast.LENGTH_SHORT).show();
                        navigateToLogin();
                    });
                    return;
                }
                SeatReservation seatReservation = new SeatReservation(authManager);
                SeatReservation.ReservationInfo reservation = seatReservation.getCurrentReservation(
                        authManager.getToken(),
                        authManager.getAccNo()
                );
                runOnUiThread(() -> {
                    if (showOverlay) {
                        showLoading(false);
                    }
                    currentReservation = reservation;
                    updateReservationUI(reservation);
                    cacheCurrentReservationIfNeeded(reservation);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (showOverlay) {
                        showLoading(false);
                    }
                    Toast.makeText(this, "查询预约失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    private boolean loadCachedReservation() {
        String cached = preferenceManager.getCachedCurrentReservation();
        if (cached == null || cached.trim().isEmpty()) {
            showReservationLoadingState();
            return false;
        }
        try {
            SeatReservation.ReservationInfo info = parseReservationInfo(cached);
            if (info != null && info.hasReservation) {
                currentReservation = info;
                updateReservationUI(info);
                return true;
            }
        } catch (Exception ignored) {
        }
        preferenceManager.clearCachedCurrentReservation();
        showReservationLoadingState();
        return false;
    }
    private void showReservationLoadingState() {
        if (tvNoReservationText != null) {
            tvNoReservationText.setText(R.string.loading);
        }
        layoutNoReservation.setVisibility(View.VISIBLE);
        layoutReservationInfo.setVisibility(View.GONE);
    }
    private void cacheCurrentReservationIfNeeded(SeatReservation.ReservationInfo reservation) {
        if (reservation != null && reservation.hasReservation) {
            try {
                preferenceManager.setCachedCurrentReservation(toReservationJson(reservation));
            } catch (Exception ignored) {
            }
        } else {
            preferenceManager.clearCachedCurrentReservation();
        }
    }
    private SeatReservation.ReservationInfo parseReservationInfo(String json) throws Exception {
        JSONObject obj = new JSONObject(json);
        SeatReservation.ReservationInfo info = new SeatReservation.ReservationInfo();
        info.hasReservation = obj.optBoolean("hasReservation", false);
        info.uuid = obj.optString("uuid", null);
        info.resvId = obj.optString("resvId", info.uuid);
        info.areaName = obj.optString("areaName", null);
        info.seatLabel = obj.optString("seatLabel", null);
        info.seatName = obj.optString("seatName", null);
        info.startTime = obj.optString("startTime", null);
        info.endTime = obj.optString("endTime", null);
        info.beginTime = obj.optLong("beginTime", 0);
        info.endTimestamp = obj.optLong("endTimestamp", 0);
        info.state = obj.optString("state", null);
        info.statusName = obj.optString("statusName", null);
        return info;
    }
    private String toReservationJson(SeatReservation.ReservationInfo reservation) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("hasReservation", reservation.hasReservation);
        obj.put("uuid", reservation.uuid);
        obj.put("resvId", reservation.resvId);
        obj.put("areaName", reservation.areaName);
        obj.put("seatLabel", reservation.seatLabel);
        obj.put("seatName", reservation.seatName);
        obj.put("startTime", reservation.startTime);
        obj.put("endTime", reservation.endTime);
        obj.put("beginTime", reservation.beginTime);
        obj.put("endTimestamp", reservation.endTimestamp);
        obj.put("state", reservation.state);
        obj.put("statusName", reservation.statusName);
        return obj.toString();
    }
    private void updateReservationUI(SeatReservation.ReservationInfo reservation) {
        if (reservation == null) {
            showReservationLoadingState();
            return;
        }
        if (!reservation.hasReservation) {
            if (tvNoReservationText != null) {
                tvNoReservationText.setText(R.string.no_reservation);
            }
            layoutNoReservation.setVisibility(View.VISIBLE);
            layoutReservationInfo.setVisibility(View.GONE);
            return;
        }
        layoutNoReservation.setVisibility(View.GONE);
        layoutReservationInfo.setVisibility(View.VISIBLE);
        tvReservationSeat.setText(reservation.areaName + " - 座位" + reservation.seatLabel);
        tvReservationTime.setText(reservation.startTime + " - " + reservation.endTime);
        tvReservationStatus.setText(getStatusText(reservation.state));
        tvReservationStatus.setTextColor(getStatusColor(reservation.state));
        updateButtonVisibility(reservation.state);
    }
    private String getStatusText(String state) {
        if (state == null) return "未知";
        switch (state) {
            case "RESERVE": return "已预约";
            case "CHECK_IN": return "使用中";
            case "AWAY": return "暂离";
            case "LATE": return "迟到";
            default: return state;
        }
    }
    private int getStatusColor(String state) {
        if (state == null) return getColor(R.color.text_secondary);
        switch (state) {
            case "RESERVE": return getColor(R.color.warning);
            case "CHECK_IN": return getColor(R.color.success);
            case "AWAY": return getColor(R.color.info);
            case "LATE": return getColor(R.color.error);
            default: return getColor(R.color.text_secondary);
        }
    }
    private void updateButtonVisibility(String state) {
        if ("RESERVE".equals(state) || "LATE".equals(state)) {
            btnSignIn.setVisibility(View.VISIBLE);
            btnSignIn.setText(R.string.btn_sign_in);
        } else if ("CHECK_IN".equals(state)) {
            btnSignIn.setVisibility(View.VISIBLE);
            btnSignIn.setText(R.string.btn_sign_out);
        } else if ("AWAY".equals(state)) {
            btnSignIn.setVisibility(View.VISIBLE);
            btnSignIn.setText("返回");
        } else {
            btnSignIn.setVisibility(View.GONE);
        }
    }
    private void reserveNow() {
        showLoading(true);
        executor.execute(() -> {
            try {
                AuthManager authManager = AuthManager.getInstance(this);
                if (!authManager.ensureLoggedIn()) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        navigateToLogin();
                    });
                    return;
                }
                String areaKey = preferenceManager.getTargetArea();
                int seatNumber = preferenceManager.getTargetSeat();
                String startTime = preferenceManager.getStartTime();
                String endTime = preferenceManager.getEndTime();
                String closeTime = getTomorrowCloseTime();
                endTime = clampEndTime(endTime, closeTime);
                Constants.AreaInfo areaInfo = Constants.SEAT_AREAS_MAP.get(areaKey);
                if (areaInfo == null) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this, "请先在设置中配置目标区域", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                SeatReservation seatReservation = new SeatReservation(authManager);
                SeatReservation.ReservationResult result = seatReservation.reserveSeat(
                        authManager.getToken(),
                        authManager.getAccNo(),
                        areaInfo,
                        seatNumber,
                        startTime,
                        endTime,
                        null 
                );
                runOnUiThread(() -> {
                    showLoading(false);
                    if (result.success) {
                        Toast.makeText(this, "预约成功！", Toast.LENGTH_SHORT).show();
                        loadCurrentReservation();
                    } else {
                        Toast.makeText(this, "预约失败: " + result.message, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, "预约出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    private void signIn() {
        if (currentReservation == null) return;
        showLoading(true);
        executor.execute(() -> {
            try {
                AuthManager authManager = AuthManager.getInstance(this);
                SeatReservation seatReservation = new SeatReservation(authManager);
                SeatReservation.OperationResult result;
                if ("CHECK_IN".equals(currentReservation.state)) {
                    result = seatReservation.signOut(
                            authManager.getToken(),
                            authManager.getAccNo(),
                            currentReservation.resvId
                    );
                } else {
                    result = seatReservation.signIn(
                            authManager.getToken(),
                            authManager.getAccNo(),
                            currentReservation.resvId
                    );
                }
                runOnUiThread(() -> {
                    showLoading(false);
                    if (result.success) {
                        Toast.makeText(this, "操作成功", Toast.LENGTH_SHORT).show();
                        loadCurrentReservation();
                    } else {
                        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, "操作失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    private void cancelReservation() {
        if (currentReservation == null) return;
        new AlertDialog.Builder(this)
                .setTitle("确认取消")
                .setMessage("确定要取消当前预约吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    performCancelReservation();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    private void performCancelReservation() {
        showLoading(true);
        executor.execute(() -> {
            try {
                AuthManager authManager = AuthManager.getInstance(this);
                SeatReservation seatReservation = new SeatReservation(authManager);
                SeatReservation.OperationResult result = seatReservation.cancelReservation(
                        authManager.getToken(),
                        authManager.getAccNo(),
                        currentReservation.resvId
                );
                runOnUiThread(() -> {
                    showLoading(false);
                    if (result.success) {
                        Toast.makeText(this, "已取消预约", Toast.LENGTH_SHORT).show();
                        loadCurrentReservation();
                    } else {
                        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, "取消失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    private void checkTraffic() {
        showLoading(true);
        executor.execute(() -> {
            TrafficQuery.TrafficInfo trafficInfo = TrafficQuery.queryCurrentTraffic();
            runOnUiThread(() -> {
                showLoading(false);
                if (trafficInfo.success) {
                    String description = TrafficQuery.getOccupancyDescription(trafficInfo.occupancyRate);
                    new AlertDialog.Builder(this)
                            .setTitle("图书馆当前人数")
                            .setMessage(String.format(
                                    "当前人数：%d 人\n" +
                                    "总容量：%d 人\n" +
                                    "占用率：%.1f%%\n" +
                                    "状态：%s",
                                    trafficInfo.currentCount,
                                    trafficInfo.totalCapacity,
                                    trafficInfo.occupancyRate,
                                    description
                            ))
                            .setPositiveButton("确定", null)
                            .show();
                } else {
                    Toast.makeText(this, "查询失败: " + trafficInfo.errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
    private void updateAutoStatus() {
        boolean autoReserve = preferenceManager.isAutoReserveEnabled();
        ivAutoReserveStatus.setColorFilter(getColor(autoReserve ? R.color.success : R.color.text_hint));
        tvAutoReserveStatus.setText(autoReserve ? "已开启" : "未开启");
        tvAutoReserveStatus.setTextColor(getColor(autoReserve ? R.color.success : R.color.text_hint));
        boolean lateProtection = preferenceManager.isLateProtectionEnabled();
        ivLateProtectionStatus.setColorFilter(getColor(lateProtection ? R.color.success : R.color.text_hint));
        tvLateProtectionStatus.setText(lateProtection ? "已开启" : "未开启");
        tvLateProtectionStatus.setTextColor(getColor(lateProtection ? R.color.success : R.color.text_hint));
        boolean autoFind = preferenceManager.isAutoFindSeatEnabled();
        ivAutoFindStatus.setColorFilter(getColor(autoFind ? R.color.success : R.color.text_hint));
        tvAutoFindStatus.setText(autoFind ? "已开启" : "未开启");
        tvAutoFindStatus.setTextColor(getColor(autoFind ? R.color.success : R.color.text_hint));
    }
    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }
    private void navigateToSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
    private void navigateToSeatQuery() {
        Intent intent = new Intent(this, SeatQueryActivity.class);
        startActivity(intent);
    }
    private void navigateToLogin() {
        preferenceManager.setLoggedIn(false);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    private String getTomorrowCloseTime() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        return dayOfWeek == Calendar.FRIDAY ? "20:00" : "22:00";
    }
    private String clampEndTime(String endTime, String closeTime) {
        Integer endMinutes = parseTimeToMinutes(endTime);
        Integer closeMinutes = parseTimeToMinutes(closeTime);
        if (endMinutes == null || closeMinutes == null) {
            return endTime;
        }
        if (endMinutes > closeMinutes) {
            return closeTime;
        }
        return endTime;
    }
    private Integer parseTimeToMinutes(String hhmm) {
        if (hhmm == null) return null;
        String[] parts = hhmm.trim().split(":");
        if (parts.length != 2) return null;
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            return h * 60 + m;
        } catch (Exception e) {
            return null;
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
