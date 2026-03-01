package com.keggin.fucknjfulib.activities;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.keggin.fucknjfulib.R;
import com.keggin.fucknjfulib.auth.AuthManager;
import com.keggin.fucknjfulib.reservation.SeatReservation;
import com.keggin.fucknjfulib.reservation.TrafficQuery;
import com.keggin.fucknjfulib.storage.PreferenceManager;
import com.keggin.fucknjfulib.utils.Constants;
import com.keggin.fucknjfulib.utils.DateUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardActivity extends AppCompatActivity {
    private Toolbar toolbar;
    private ImageButton btnSettings;
    private android.widget.TextView btnLogs;
    private MaterialCardView cardCurrentReservation;
    private LinearLayout layoutNoReservation;
    private TextView tvNoReservationText;
    private LinearLayout layoutReservationInfo;
    private TextView tvReservationSeat;
    private LinearLayout layoutTodayReservation;
    private TextView tvTodayReservationLabel;
    private TextView tvTodayReservationSeat;
    private TextView tvTodayReservationTime;
    private TextView tvTodayReservationStatus;
    private LinearLayout layoutTomorrowReservation;
    private TextView tvTomorrowReservationLabel;
    private TextView tvTomorrowReservationSeat;
    private TextView tvTomorrowReservationTime;
    private TextView tvTomorrowReservationStatus;
    private MaterialButton btnTodaySignIn;
    private MaterialButton btnTodayCancelReservation;
    private MaterialButton btnTomorrowCancelReservation;
    private MaterialCardView cardReserveNow;
    private MaterialCardView cardQuerySeats;
    private MaterialCardView cardCheckTraffic;
    private MaterialCardView cardSettings;
    private MaterialCardView cardAccountInfo;
    private MaterialCardView cardPlanTasks;
    private TextView tvViolationBadge;
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
    private SeatReservation.ReservationInfo todayReservationForActions;
    private SeatReservation.ReservationInfo tomorrowReservationForActions;

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
        checkPunishInfo();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        btnSettings = findViewById(R.id.btnSettings);
        btnLogs = findViewById(R.id.btnLogs);
        cardCurrentReservation = findViewById(R.id.cardCurrentReservation);
        layoutNoReservation = findViewById(R.id.layoutNoReservation);
        tvNoReservationText = findViewById(R.id.tvNoReservationText);
        layoutReservationInfo = findViewById(R.id.layoutReservationInfo);
        tvReservationSeat = findViewById(R.id.tvReservationSeat);
        layoutTodayReservation = findViewById(R.id.layoutTodayReservation);
        tvTodayReservationLabel = findViewById(R.id.tvTodayReservationLabel);
        tvTodayReservationSeat = findViewById(R.id.tvTodayReservationSeat);
        tvTodayReservationTime = findViewById(R.id.tvTodayReservationTime);
        tvTodayReservationStatus = findViewById(R.id.tvTodayReservationStatus);
        layoutTomorrowReservation = findViewById(R.id.layoutTomorrowReservation);
        tvTomorrowReservationLabel = findViewById(R.id.tvTomorrowReservationLabel);
        tvTomorrowReservationSeat = findViewById(R.id.tvTomorrowReservationSeat);
        tvTomorrowReservationTime = findViewById(R.id.tvTomorrowReservationTime);
        tvTomorrowReservationStatus = findViewById(R.id.tvTomorrowReservationStatus);
        btnTodaySignIn = findViewById(R.id.btnTodaySignIn);
        btnTodayCancelReservation = findViewById(R.id.btnTodayCancelReservation);
        btnTomorrowCancelReservation = findViewById(R.id.btnTomorrowCancelReservation);
        cardReserveNow = findViewById(R.id.cardReserveNow);
        cardQuerySeats = findViewById(R.id.cardQuerySeats);
        cardCheckTraffic = findViewById(R.id.cardCheckTraffic);
        cardSettings = findViewById(R.id.cardSettings);
        cardAccountInfo = findViewById(R.id.cardAccountInfo);
        cardPlanTasks = findViewById(R.id.cardPlanTasks);
        tvViolationBadge = findViewById(R.id.tvViolationBadge);
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
        btnLogs.setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));
        cardReserveNow.setOnClickListener(v -> showReserveNowDialog());
        cardQuerySeats.setOnClickListener(v -> navigateToVisualSeat());
        cardCheckTraffic.setOnClickListener(v -> checkTraffic());
        cardSettings.setOnClickListener(v -> navigateToSettings());
        if (btnTodaySignIn != null) {
            btnTodaySignIn.setOnClickListener(v -> signIn(todayReservationForActions));
        }
        if (btnTodayCancelReservation != null) {
            btnTodayCancelReservation.setOnClickListener(v -> cancelReservation(todayReservationForActions, "今天"));
        }
        if (btnTomorrowCancelReservation != null) {
            btnTomorrowCancelReservation.setOnClickListener(v -> cancelReservation(tomorrowReservationForActions, "明天"));
        }
        if (cardAccountInfo != null)
            cardAccountInfo.setOnClickListener(v -> navigateToAccountInfo());
        if (cardPlanTasks != null)
            cardPlanTasks.setOnClickListener(v -> navigateToPlanTasks());
    }

    private void navigateToAccountInfo() {
        startActivity(new Intent(this, AccountInfoActivity.class));
    }

    private void navigateToPlanTasks() {
        startActivity(new Intent(this, PlanTasksActivity.class));
    }

    /**
     * Loads punish count in background and updates the badge on the Account Info
     * card.
     */
    private void checkPunishInfo() {
        executor.execute(() -> {
            try {
                AuthManager auth = AuthManager.getInstance(this);
                if (!auth.ensureLoggedIn())
                    return;
                String token = auth.getToken();
                if (token == null)
                    return;
                com.keggin.fucknjfulib.network.HttpClientManager http = com.keggin.fucknjfulib.network.HttpClientManager
                        .getInstance(null);
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("token", token);
                headers.put("lan", "1");
                headers.put("Accept", com.keggin.fucknjfulib.network.ApiConstants.ACCEPT_JSON);
                okhttp3.Response resp = http.get(com.keggin.fucknjfulib.network.ApiConstants.getPunishInfoUrl(),
                        headers);
                if (resp.isSuccessful()) {
                    String body = com.keggin.fucknjfulib.network.HttpClientManager.getResponseBody(resp);
                    if (body != null) {
                        org.json.JSONObject json = new org.json.JSONObject(body);
                        if (json.optInt("code") == 0) {
                            org.json.JSONArray arr = json.optJSONArray("data");
                            int count = arr != null ? arr.length() : 0;
                            runOnUiThread(() -> {
                                if (tvViolationBadge != null) {
                                    if (count == 0) {
                                        tvViolationBadge.setText("记录正常");
                                        tvViolationBadge.setTextColor(0xFF388E3C);
                                    } else {
                                        tvViolationBadge.setText("违约: " + count + " 条");
                                        tvViolationBadge.setTextColor(0xFFE53935);
                                    }
                                }
                            });
                        }
                    }
                } else {
                    resp.close();
                }
            } catch (Exception ignored) {
            }
        });
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
                List<SeatReservation.ReservationInfo> reservations = seatReservation.getTodayAndTomorrowReservations();
                SeatReservation.ReservationInfo todayReservation = pickFirstReservationForDate(
                        reservations, DateUtils.getTodayDate());
                SeatReservation.ReservationInfo tomorrowReservation = pickFirstReservationForDate(
                        reservations, DateUtils.getTomorrowDate());
                SeatReservation.ReservationInfo primaryReservation = selectPrimaryReservation(
                        todayReservation, tomorrowReservation);

                runOnUiThread(() -> {
                    if (showOverlay) {
                        showLoading(false);
                    }
                    currentReservation = primaryReservation;
                    updateReservationUI(todayReservation, tomorrowReservation);
                    cacheCurrentReservationIfNeeded(primaryReservation);
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
                String today = DateUtils.getTodayDate();
                String tomorrow = DateUtils.getTomorrowDate();
                SeatReservation.ReservationInfo todayReservation = null;
                SeatReservation.ReservationInfo tomorrowReservation = null;

                if (today.equals(info.onDate)) {
                    todayReservation = info;
                } else if (tomorrow.equals(info.onDate)) {
                    tomorrowReservation = info;
                } else {
                    preferenceManager.clearCachedCurrentReservation();
                    showReservationLoadingState();
                    return false;
                }

                currentReservation = selectPrimaryReservation(todayReservation, tomorrowReservation);
                updateReservationUI(todayReservation, tomorrowReservation);
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
        info.onDate = obj.optString("onDate", null);
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
        obj.put("onDate", reservation.onDate != null ? reservation.onDate : "");
        obj.put("startTime", reservation.startTime);
        obj.put("endTime", reservation.endTime);
        obj.put("beginTime", reservation.beginTime);
        obj.put("endTimestamp", reservation.endTimestamp);
        obj.put("state", reservation.state);
        obj.put("statusName", reservation.statusName);
        return obj.toString();
    }

    private void updateReservationUI(SeatReservation.ReservationInfo todayReservation,
            SeatReservation.ReservationInfo tomorrowReservation) {
        boolean hasTodayReservation = todayReservation != null && todayReservation.hasReservation;
        boolean hasTomorrowReservation = tomorrowReservation != null && tomorrowReservation.hasReservation;

        if (!hasTodayReservation && !hasTomorrowReservation) {
            if (tvNoReservationText != null) {
                tvNoReservationText.setText(R.string.no_reservation);
            }
            layoutNoReservation.setVisibility(View.VISIBLE);
            layoutReservationInfo.setVisibility(View.GONE);
            todayReservationForActions = null;
            tomorrowReservationForActions = null;
            updateActionButtonsForReservations(null, null);
            return;
        }

        layoutNoReservation.setVisibility(View.GONE);
        layoutReservationInfo.setVisibility(View.VISIBLE);

        if (tvReservationSeat != null && tvReservationSeat.getParent() instanceof View) {
            ((View) tvReservationSeat.getParent()).setVisibility(View.GONE);
        }

        if (layoutTodayReservation != null) {
            layoutTodayReservation.setVisibility(hasTodayReservation ? View.VISIBLE : View.GONE);
        }
        if (layoutTomorrowReservation != null) {
            layoutTomorrowReservation.setVisibility(hasTomorrowReservation ? View.VISIBLE : View.GONE);
        }

        if (hasTodayReservation) {
            bindDayReservation(todayReservation, true);
        }
        if (hasTomorrowReservation) {
            bindDayReservation(tomorrowReservation, false);
        }

        todayReservationForActions = hasTodayReservation ? todayReservation : null;
        tomorrowReservationForActions = hasTomorrowReservation ? tomorrowReservation : null;

        currentReservation = selectPrimaryReservation(todayReservationForActions, tomorrowReservationForActions);
        updateActionButtonsForReservations(todayReservationForActions, tomorrowReservationForActions);
    }

    private String formatSeatLine(SeatReservation.ReservationInfo reservation) {
        if (reservation == null || !reservation.hasReservation) {
            return "座位：暂无预约";
        }
        String area = reservation.areaName != null && !reservation.areaName.trim().isEmpty()
                ? reservation.areaName
                : "--";
        if (reservation.seatLabel != null && !reservation.seatLabel.trim().isEmpty()) {
            return "座位：" + area + " · " + reservation.seatLabel + "号";
        }
        if (reservation.seatName != null && !reservation.seatName.trim().isEmpty()) {
            return "座位：" + area + " · " + reservation.seatName;
        }
        return "座位：" + area;
    }

    private void bindDayReservation(SeatReservation.ReservationInfo reservation, boolean isToday) {
        TextView labelView = isToday ? tvTodayReservationLabel : tvTomorrowReservationLabel;
        TextView seatView = isToday ? tvTodayReservationSeat : tvTomorrowReservationSeat;
        TextView timeView = isToday ? tvTodayReservationTime : tvTomorrowReservationTime;
        TextView statusView = isToday ? tvTodayReservationStatus : tvTomorrowReservationStatus;
        LinearLayout container = isToday ? layoutTodayReservation : layoutTomorrowReservation;

        if (reservation == null || !reservation.hasReservation) {
            if (container != null) {
                container.setVisibility(View.GONE);
            }
            return;
        }

        if (container != null) {
            container.setVisibility(View.VISIBLE);
        }

        String defaultDate = isToday ? DateUtils.getTodayDate() : DateUtils.getTomorrowDate();
        String dayPrefix = isToday ? "今天" : "明天";
        String dateText = reservation.onDate != null && !reservation.onDate.trim().isEmpty()
                ? reservation.onDate
                : defaultDate;
        labelView.setText(dayPrefix + " · " + dateText);

        seatView.setText(formatSeatLine(reservation));
        String start = reservation.startTime != null && !reservation.startTime.trim().isEmpty() ? reservation.startTime : "--:--";
        String end = reservation.endTime != null && !reservation.endTime.trim().isEmpty() ? reservation.endTime : "--:--";
        timeView.setText(start + " - " + end);
        statusView.setText(getStatusText(reservation.state));
        statusView.setTextColor(getStatusColor(reservation.state));
    }

    private SeatReservation.ReservationInfo pickFirstReservationForDate(
            List<SeatReservation.ReservationInfo> reservations, String date) {
        if (reservations == null || reservations.isEmpty() || date == null) {
            return null;
        }
        SeatReservation.ReservationInfo best = null;
        for (SeatReservation.ReservationInfo info : reservations) {
            if (info == null || !info.hasReservation) {
                continue;
            }
            if (!date.equals(info.onDate)) {
                continue;
            }
            if (best == null || (info.beginTime > 0 && (best.beginTime <= 0 || info.beginTime < best.beginTime))) {
                best = info;
            }
        }
        return best;
    }

    private SeatReservation.ReservationInfo selectPrimaryReservation(
            SeatReservation.ReservationInfo todayReservation,
            SeatReservation.ReservationInfo tomorrowReservation) {
        if (todayReservation != null && todayReservation.hasReservation) {
            return todayReservation;
        }
        if (tomorrowReservation != null && tomorrowReservation.hasReservation) {
            return tomorrowReservation;
        }
        return null;
    }

    private void updateActionButtonsForReservations(SeatReservation.ReservationInfo todayReservation,
            SeatReservation.ReservationInfo tomorrowReservation) {
        if (btnTodaySignIn != null) {
            btnTodaySignIn.setVisibility(View.GONE);
        }
        if (btnTodayCancelReservation != null) {
            btnTodayCancelReservation.setVisibility(View.GONE);
        }
        if (btnTomorrowCancelReservation != null) {
            btnTomorrowCancelReservation.setVisibility(View.GONE);
        }

        if (todayReservation != null && todayReservation.hasReservation) {
            if (btnTodayCancelReservation != null) {
                btnTodayCancelReservation.setVisibility(View.VISIBLE);
            }
            if (btnTodaySignIn != null) {
                btnTodaySignIn.setVisibility(View.VISIBLE);
                updateButtonVisibility(btnTodaySignIn, todayReservation.state);
            }
        }

        if (tomorrowReservation != null && tomorrowReservation.hasReservation) {
            if (btnTomorrowCancelReservation != null) {
                btnTomorrowCancelReservation.setVisibility(View.VISIBLE);
            }
        }
    }

    private String getStatusText(String state) {
        if (state == null)
            return "未知";
        switch (state) {
            case "RESERVE":
                return "已预约";
            case "CHECK_IN":
                return "使用中";
            case "AWAY":
                return "暂离";
            case "LATE":
                return "迟到";
            default:
                return state;
        }
    }

    private int getStatusColor(String state) {
        if (state == null)
            return getColor(R.color.text_secondary);
        switch (state) {
            case "RESERVE":
                return getColor(R.color.warning);
            case "CHECK_IN":
                return getColor(R.color.success);
            case "AWAY":
                return getColor(R.color.info);
            case "LATE":
                return getColor(R.color.error);
            default:
                return getColor(R.color.text_secondary);
        }
    }

    private void updateButtonVisibility(MaterialButton actionButton, String state) {
        if (actionButton == null) {
            return;
        }
        if ("RESERVE".equals(state) || "LATE".equals(state)) {
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setText(R.string.btn_sign_in);
        } else if ("CHECK_IN".equals(state)) {
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setText(R.string.btn_sign_out);
        } else if ("AWAY".equals(state)) {
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setText("返回");
        } else {
            actionButton.setVisibility(View.GONE);
        }
    }

    private void showReserveNowDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reserve_now, null);

        MaterialButton btnDateToday = dialogView.findViewById(R.id.btnDateToday);
        MaterialButton btnDateTomorrow = dialogView.findViewById(R.id.btnDateTomorrow);
        Spinner spinnerArea = dialogView.findViewById(R.id.spinnerArea);
        TextInputEditText etSeatNumber = dialogView.findViewById(R.id.etSeatNumber);
        TextInputEditText etStartTime = dialogView.findViewById(R.id.etStartTime);
        TextInputEditText etEndTime = dialogView.findViewById(R.id.etEndTime);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);
        MaterialButton btnReserveConfirm = dialogView.findViewById(R.id.btnReserveConfirm);

        // Area spinner
        List<String> areaKeys = new ArrayList<>(Constants.SEAT_AREAS_MAP.keySet());
        List<String> areaNames = new ArrayList<>();
        for (String key : areaKeys) {
            Constants.AreaInfo info = Constants.SEAT_AREAS_MAP.get(key);
            areaNames.add(info != null ? info.name : key);
        }
        ArrayAdapter<String> areaAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, areaNames);
        areaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerArea.setAdapter(areaAdapter);
        String lastArea = preferenceManager.getTargetArea();
        int idx = areaKeys.indexOf(lastArea);
        if (idx >= 0)
            spinnerArea.setSelection(idx);

        // Default seat from preferences
        int lastSeat = preferenceManager.getTargetSeat();
        etSeatNumber.setText(lastSeat > 0 ? String.valueOf(lastSeat) : "");

        // Date selection: use deep-blue for selected, neutral for unselected
        final String[] selectedDate = { DateUtils.getTomorrowDate() }; // default tomorrow

        // Helper to apply selected/unselected states
        java.util.function.BiConsumer<MaterialButton, Boolean> applyDateStyle = (btn, selected) -> {
            if (selected) {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.primary)));
                btn.setTextColor(0xFFFFFFFF);
                btn.setStrokeColor(android.content.res.ColorStateList.valueOf(getColor(R.color.primary)));
            } else {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
                btn.setTextColor(getColor(R.color.primary));
                btn.setStrokeColor(android.content.res.ColorStateList.valueOf(getColor(R.color.primary)));
            }
        };
        // Initial state: tomorrow selected
        applyDateStyle.accept(btnDateTomorrow, true);
        applyDateStyle.accept(btnDateToday, false);

        btnDateToday.setOnClickListener(v -> {
            selectedDate[0] = DateUtils.getTodayDate();
            applyDateStyle.accept(btnDateToday, true);
            applyDateStyle.accept(btnDateTomorrow, false);
        });
        btnDateTomorrow.setOnClickListener(v -> {
            selectedDate[0] = DateUtils.getTomorrowDate();
            applyDateStyle.accept(btnDateTomorrow, true);
            applyDateStyle.accept(btnDateToday, false);
        });

        // Default times
        String defStart = preferenceManager.getStartTime();
        String defEnd = preferenceManager.getEndTime();
        String fallbackStartTime = (defStart != null && !defStart.trim().isEmpty())
                ? defStart
                : Constants.DEFAULT_START_TIME;
        String fallbackEndTime = (defEnd != null && !defEnd.trim().isEmpty())
                ? defEnd
                : DateUtils.getEndTimeWithoutSeconds(selectedDate[0]);

        etStartTime.setText(fallbackStartTime);
        etEndTime.setText(clampEndTime(fallbackEndTime, DateUtils.getEndTimeWithoutSeconds(selectedDate[0])));

        // TimePicker for start time
        etStartTime.setOnClickListener(v -> {
            String cur = etStartTime.getText() != null
                    ? etStartTime.getText().toString()
                    : Constants.DEFAULT_START_TIME;
            int h = 7, m = 30;
            try {
                String[] p = cur.split(":");
                h = Integer.parseInt(p[0]);
                m = Integer.parseInt(p[1]);
            } catch (Exception ignored) {
            }
            new TimePickerDialog(this,
                    (tp, hour, minute) -> etStartTime.setText(String.format("%02d:%02d", hour, minute)), h, m, true)
                    .show();
        });

        // TimePicker for end time
        etEndTime.setOnClickListener(v -> {
            String defaultEnd = DateUtils.getEndTimeWithoutSeconds(selectedDate[0]);
            String cur = etEndTime.getText() != null ? etEndTime.getText().toString() : defaultEnd;
            int h = Integer.parseInt(defaultEnd.split(":")[0]), m = 0;
            try {
                String[] p = cur.split(":");
                h = Integer.parseInt(p[0]);
                m = Integer.parseInt(p[1]);
            } catch (Exception ignored) {
            }
            new TimePickerDialog(this,
                    (tp, hour, minute) -> etEndTime.setText(String.format("%02d:%02d", hour, minute)), h, m, true)
                    .show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnReserveConfirm.setOnClickListener(v -> {
            int areaIdx = spinnerArea.getSelectedItemPosition();
            String areaKey = areaKeys.get(areaIdx);
            Constants.AreaInfo areaInfo = Constants.SEAT_AREAS_MAP.get(areaKey);
            if (areaInfo == null) {
                Toast.makeText(this, "区域无效", Toast.LENGTH_SHORT).show();
                return;
            }

            String seatStr = etSeatNumber.getText() != null ? etSeatNumber.getText().toString().trim() : "";
            int seatNum;
            try {
                seatNum = Integer.parseInt(seatStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效的座位号", Toast.LENGTH_SHORT).show();
                return;
            }
            if (seatNum < 1 || seatNum > areaInfo.seatCount) {
                Toast.makeText(this, "座位号超出范围 (1-" + areaInfo.seatCount + ")", Toast.LENGTH_SHORT).show();
                return;
            }

            String date = selectedDate[0];
            String closeTime = DateUtils.getEndTimeWithoutSeconds(date);
            String startTime = etStartTime.getText() != null && etStartTime.getText().toString().trim().length() > 0
                    ? etStartTime.getText().toString().trim()
                    : fallbackStartTime;
            String endTime = etEndTime.getText() != null && etEndTime.getText().toString().trim().length() > 0
                    ? etEndTime.getText().toString().trim()
                    : closeTime;
            endTime = clampEndTime(endTime, closeTime);

            dialog.dismiss();
            executeReserveNow(areaInfo, seatNum, startTime, endTime, date);
        });
        dialog.show();
    }

    private void executeReserveNow(Constants.AreaInfo areaInfo, int seatNumber, String startTime, String endTime,
            String date) {
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
                SeatReservation seatReservation = new SeatReservation(authManager);
                SeatReservation.ReservationResult result = seatReservation.reserveSeat(
                        authManager.getToken(), authManager.getAccNo(),
                        areaInfo, seatNumber, startTime, endTime, date);
                runOnUiThread(() -> {
                    showLoading(false);
                    if (result.success) {
                        Toast.makeText(this, "预约成功！", Toast.LENGTH_SHORT).show();
                        loadCurrentReservation();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("预约失败")
                                .setMessage(result.message)
                                .setPositiveButton("确定", null).show();
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

    private void signIn(SeatReservation.ReservationInfo targetReservation) {
        if (targetReservation == null || !targetReservation.hasReservation)
            return;
        showLoading(true);
        executor.execute(() -> {
            try {
                AuthManager authManager = AuthManager.getInstance(this);
                SeatReservation seatReservation = new SeatReservation(authManager);
                SeatReservation.OperationResult result;
                if ("CHECK_IN".equals(targetReservation.state)) {
                    result = seatReservation.signOut(
                            authManager.getToken(),
                            authManager.getAccNo(),
                            targetReservation.resvId);
                } else {
                    result = seatReservation.signIn(
                            authManager.getToken(),
                            authManager.getAccNo(),
                            targetReservation.resvId);
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

    private void cancelReservation(SeatReservation.ReservationInfo targetReservation, String dayLabel) {
        if (targetReservation == null || !targetReservation.hasReservation)
            return;
        new AlertDialog.Builder(this)
                .setTitle("确认取消")
                .setMessage("确定要取消" + dayLabel + "的预约吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    performCancelReservation(targetReservation);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void performCancelReservation(SeatReservation.ReservationInfo targetReservation) {
        showLoading(true);
        executor.execute(() -> {
            try {
                AuthManager authManager = AuthManager.getInstance(this);
                SeatReservation seatReservation = new SeatReservation(authManager);
                SeatReservation.OperationResult result = seatReservation.cancelReservation(
                        authManager.getToken(),
                        authManager.getAccNo(),
                        targetReservation.resvId);
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
            TrafficQuery.TrafficInfo trafficInfo = TrafficQuery.queryCurrentTraffic(DashboardActivity.this);
            runOnUiThread(() -> {
                showLoading(false);
                if (trafficInfo.success) {
                    // Inflate custom traffic info dialog
                    View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_traffic_info, null);

                    TextView tvCount = dialogView.findViewById(R.id.tv_current_count);
                    TextView tvTotal = dialogView.findViewById(R.id.tv_total_capacity);
                    TextView tvRate = dialogView.findViewById(R.id.tv_occupancy_rate);
                    TextView tvTime = dialogView.findViewById(R.id.tv_update_time);
                    ProgressBar pbOccupancy = dialogView.findViewById(R.id.pb_occupancy);
                    com.google.android.material.button.MaterialButton btnConfirm = dialogView
                            .findViewById(R.id.btn_confirm);

                    tvCount.setText(String.valueOf(trafficInfo.currentCount));
                    tvTotal.setText(String.valueOf(trafficInfo.totalCapacity));
                    tvRate.setText(String.format("%.1f%%", trafficInfo.occupancyRate));
                    tvTime.setText("更新于 " + (trafficInfo.updateTime != null ? trafficInfo.updateTime : "--:--"));
                    pbOccupancy.setProgress(Math.min(100, (int) trafficInfo.occupancyRate));

                    AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                            .setView(dialogView)
                            .create();
                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                    }
                    btnConfirm.setOnClickListener(v -> dialog.dismiss());
                    dialog.show();
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
        startActivity(new Intent(this, SeatQueryActivity.class));
    }

    private void navigateToVisualSeat() {
        startActivity(new Intent(this, VisualSeatActivity.class));
    }

    private void navigateToLogin() {
        preferenceManager.setLoggedIn(false);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String getTomorrowCloseTime() {
        return DateUtils.getEndTimeWithoutSeconds(DateUtils.getTomorrowDate());
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
        if (hhmm == null)
            return null;
        String[] parts = hhmm.trim().split(":");
        if (parts.length != 2)
            return null;
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
