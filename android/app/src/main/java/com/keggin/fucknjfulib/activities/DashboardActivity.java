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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主界面
 */
public class DashboardActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private ImageButton btnSettings;

    // 当前预约卡片
    private MaterialCardView cardCurrentReservation;
    private LinearLayout layoutNoReservation;
    private LinearLayout layoutReservationInfo;
    private TextView tvReservationSeat;
    private TextView tvReservationTime;
    private TextView tvReservationStatus;
    private MaterialButton btnSignIn;
    private MaterialButton btnCancelReservation;

    // 快捷操作
    private MaterialCardView cardReserveNow;
    private MaterialCardView cardQuerySeats;
    private MaterialCardView cardCheckTraffic;
    private MaterialCardView cardSettings;

    // 自动化状态
    private ImageView ivAutoReserveStatus;
    private TextView tvAutoReserveStatus;
    private ImageView ivLateProtectionStatus;
    private TextView tvLateProtectionStatus;
    private ImageView ivAutoFindStatus;
    private TextView tvAutoFindStatus;

    // 加载遮罩
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
        loadCurrentReservation();
        updateAutoStatus();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        btnSettings = findViewById(R.id.btnSettings);

        cardCurrentReservation = findViewById(R.id.cardCurrentReservation);
        layoutNoReservation = findViewById(R.id.layoutNoReservation);
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
        btnSettings.setOnClickListener(v -> navigateToSettings());

        cardReserveNow.setOnClickListener(v -> reserveNow());
        cardQuerySeats.setOnClickListener(v -> navigateToSeatQuery());
        cardCheckTraffic.setOnClickListener(v -> checkTraffic());
        cardSettings.setOnClickListener(v -> navigateToSettings());

        btnSignIn.setOnClickListener(v -> signIn());
        btnCancelReservation.setOnClickListener(v -> cancelReservation());
    }

    private void loadCurrentReservation() {
        showLoading(true);

        executor.execute(() -> {
            try {
                AuthManager authManager = AuthManager.getInstance(this);
                
                // 确保登录状态
                if (!authManager.ensureLoggedIn()) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this, "登录已过期，请重新登录", Toast.LENGTH_SHORT).show();
                        navigateToLogin();
                    });
                    return;
                }

                // 查询当前预约
                SeatReservation seatReservation = new SeatReservation(authManager);
                SeatReservation.ReservationInfo reservation = seatReservation.getCurrentReservation(
                        authManager.getToken(),
                        authManager.getAccNo()
                );

                runOnUiThread(() -> {
                    showLoading(false);
                    currentReservation = reservation;
                    updateReservationUI(reservation);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, "查询预约失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateReservationUI(SeatReservation.ReservationInfo reservation) {
        if (reservation == null || !reservation.hasReservation) {
            layoutNoReservation.setVisibility(View.VISIBLE);
            layoutReservationInfo.setVisibility(View.GONE);
        } else {
            layoutNoReservation.setVisibility(View.GONE);
            layoutReservationInfo.setVisibility(View.VISIBLE);

            tvReservationSeat.setText(reservation.areaName + " - 座位" + reservation.seatLabel);
            tvReservationTime.setText(reservation.startTime + " - " + reservation.endTime);
            tvReservationStatus.setText(getStatusText(reservation.state));
            tvReservationStatus.setTextColor(getStatusColor(reservation.state));

            // 根据状态显示不同的按钮
            updateButtonVisibility(reservation.state);
        }
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

                // 获取预约设置
                String areaKey = preferenceManager.getTargetArea();
                int seatNumber = preferenceManager.getTargetSeat();
                String startTime = preferenceManager.getStartTime();
                String endTime = preferenceManager.getEndTime();

                Constants.AreaInfo areaInfo = Constants.SEAT_AREAS_MAP.get(areaKey);
                if (areaInfo == null) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this, "请先在设置中配置目标区域", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // 执行预约
                SeatReservation seatReservation = new SeatReservation(authManager);
                SeatReservation.ReservationResult result = seatReservation.reserveSeat(
                        authManager.getToken(),
                        authManager.getAccNo(),
                        areaInfo,
                        seatNumber,
                        startTime,
                        endTime,
                        null // 明天日期，null表示预约明天
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
                    // 签退
                    result = seatReservation.signOut(
                            authManager.getToken(),
                            authManager.getAccNo(),
                            currentReservation.resvId
                    );
                } else {
                    // 签到
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
            // TrafficQuery 不需要认证，但为了保持会话活跃，可以在这里检查一下
            // AuthManager.getInstance(this).ensureLoggedIn();
            
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
        // 更新自动预约状态
        boolean autoReserve = preferenceManager.isAutoReserveEnabled();
        ivAutoReserveStatus.setColorFilter(getColor(autoReserve ? R.color.success : R.color.text_hint));
        tvAutoReserveStatus.setText(autoReserve ? "已开启" : "未开启");
        tvAutoReserveStatus.setTextColor(getColor(autoReserve ? R.color.success : R.color.text_hint));

        // 更新迟到保护状态
        boolean lateProtection = preferenceManager.isLateProtectionEnabled();
        ivLateProtectionStatus.setColorFilter(getColor(lateProtection ? R.color.success : R.color.text_hint));
        tvLateProtectionStatus.setText(lateProtection ? "已开启" : "未开启");
        tvLateProtectionStatus.setTextColor(getColor(lateProtection ? R.color.success : R.color.text_hint));

        // 更新自动寻座状态
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}