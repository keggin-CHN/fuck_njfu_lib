package com.keggin.fucknjfulib.activities;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.keggin.fucknjfulib.BuildConfig;
import com.keggin.fucknjfulib.R;
import com.keggin.fucknjfulib.auth.AuthManager;
import com.keggin.fucknjfulib.network.ApiConstants;
import com.keggin.fucknjfulib.network.HttpClientManager;
import com.keggin.fucknjfulib.services.AutoReserveService;
import com.keggin.fucknjfulib.services.LateProtectionService;
import com.keggin.fucknjfulib.storage.PreferenceManager;
import com.keggin.fucknjfulib.utils.Constants;
import com.keggin.fucknjfulib.utils.SystemPermissionChecker;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Response;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private static final String FEATURE_NOTIFY_CHANNEL_ID = "feature_status_channel";
    private static final int NOTIFY_ID_AUTO_RESERVE = 3201;
    private static final int NOTIFY_ID_LATE_PROTECTION = 3202;
    private static final int NOTIFY_ID_AUTO_FIND = 3203;
    private Toolbar toolbar;
    private CardView cardPermissionCheck;
    private LinearLayout layoutNotificationPermission;
    private LinearLayout layoutExactAlarmPermission;
    private LinearLayout layoutBatteryOptimization;
    private LinearLayout layoutAutoStartPermission;
    private TextView tvNotificationStatus;
    private TextView tvExactAlarmStatus;
    private TextView tvBatteryOptimizationStatus;
    private TextView tvAutoStartStatus;
    private CheckBox checkboxHidePermissionCard;

    private LinearLayout layoutTargetArea;
    private LinearLayout layoutTargetSeat;
    private LinearLayout layoutStartTime;
    private LinearLayout layoutEndTime;
    private TextView tvTargetArea;
    private TextView tvTargetSeat;
    private TextView tvStartTime;
    private TextView tvEndTime;
    private SwitchMaterial switchAutoReserve;
    private SwitchMaterial switchLateProtection;
    private SwitchMaterial switchAutoFindSeat;
    private SwitchMaterial switchDarkMode;
    private TextView tvStudentId;
    private LinearLayout layoutLogout;
    private TextView tvVersion;
    private TextView tvUserCredit;
    private LinearLayout layoutGithub;
    private PreferenceManager preferenceManager;
    private ExecutorService executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        preferenceManager = new PreferenceManager(this);
        executor = Executors.newSingleThreadExecutor();
        initViews();
        setupToolbar();
        loadSettings();
        setupClickListeners();
        loadUserProfile();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        cardPermissionCheck = findViewById(R.id.cardPermissionCheck);
        layoutNotificationPermission = findViewById(R.id.layoutNotificationPermission);
        layoutExactAlarmPermission = findViewById(R.id.layoutExactAlarmPermission);
        layoutBatteryOptimization = findViewById(R.id.layoutBatteryOptimization);
        layoutAutoStartPermission = findViewById(R.id.layoutAutoStartPermission);
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus);
        tvExactAlarmStatus = findViewById(R.id.tvExactAlarmStatus);
        tvBatteryOptimizationStatus = findViewById(R.id.tvBatteryOptimizationStatus);
        tvAutoStartStatus = findViewById(R.id.tvAutoStartStatus);
        checkboxHidePermissionCard = findViewById(R.id.checkboxHidePermissionCard);

        layoutTargetArea = findViewById(R.id.layoutTargetArea);
        layoutTargetSeat = findViewById(R.id.layoutTargetSeat);
        layoutStartTime = findViewById(R.id.layoutStartTime);
        layoutEndTime = findViewById(R.id.layoutEndTime);
        tvTargetArea = findViewById(R.id.tvTargetArea);
        tvTargetSeat = findViewById(R.id.tvTargetSeat);
        tvStartTime = findViewById(R.id.tvStartTime);
        tvEndTime = findViewById(R.id.tvEndTime);
        switchAutoReserve = findViewById(R.id.switchAutoReserve);
        switchLateProtection = findViewById(R.id.switchLateProtection);
        switchAutoFindSeat = findViewById(R.id.switchAutoFindSeat);
        switchDarkMode = findViewById(R.id.switchDarkMode);
        tvStudentId = findViewById(R.id.tvStudentId);
        layoutLogout = findViewById(R.id.layoutLogout);
        tvVersion = findViewById(R.id.tvVersion);
        tvUserCredit = findViewById(R.id.tvUserCredit);
        layoutGithub = findViewById(R.id.layoutGithub);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadSettings() {
        checkSystemPermissions();
        updatePermissionCardVisibility();
        String areaKey = preferenceManager.getTargetArea();
        tvTargetArea.setText(preferenceManager.getAreaName(areaKey));
        tvTargetSeat.setText(String.valueOf(preferenceManager.getTargetSeat()));

        String startTime = preferenceManager.getStartTime();
        String endTime = preferenceManager.getEndTime();
        tvStartTime.setText(preferenceManager.hasStartTimeConfigured() ? startTime : "--:--");
        tvEndTime.setText(preferenceManager.hasEndTimeConfigured() ? endTime : "--:--");
        switchAutoReserve.setChecked(preferenceManager.isAutoReserveEnabled());
        switchLateProtection.setChecked(preferenceManager.isLateProtectionEnabled());
        switchAutoFindSeat.setChecked(preferenceManager.isAutoFindSeatEnabled());
        switchDarkMode.setChecked(preferenceManager.isDarkModeEnabled());
        tvStudentId.setText("学号：" + preferenceManager.getStudentId());
        tvVersion.setText(BuildConfig.VERSION_NAME);
    }

    private void loadUserProfile() {
        executor.execute(() -> {
            try {
                AuthManager authManager = AuthManager.getInstance(this);
                if (!authManager.ensureLoggedIn())
                    return;
                String token = authManager.getToken();
                if (token == null)
                    return;
                HttpClientManager httpClient = HttpClientManager.getInstance(null);
                String url = ApiConstants.getUserInfoUrl();
                Map<String, String> headers = new HashMap<>();
                headers.put("token", token);
                headers.put("lan", "1");
                headers.put("Accept", ApiConstants.ACCEPT_JSON);
                Response response = httpClient.get(url, headers);
                try {
                    if (response.isSuccessful()) {
                        String body = HttpClientManager.getResponseBody(response);
                        if (body != null) {
                            JSONObject json = new JSONObject(body);
                            if (json.optInt("code") == 0) {
                                JSONObject data = json.optJSONObject("data");
                                if (data != null) {
                                    String name = data.optString("trueName", "");
                                    int credit = data.optInt("creditScore", -1);
                                    int limit = data.optInt("limitScore", -1);
                                    runOnUiThread(() -> {
                                        if (!name.isEmpty()) {
                                            tvStudentId.setText(name + "（" + preferenceManager.getStudentId() + "）");
                                        }
                                        if (tvUserCredit != null && credit >= 0) {
                                            tvUserCredit.setVisibility(View.VISIBLE);
                                            tvUserCredit
                                                    .setText("信用分: " + credit + (limit >= 0 ? " / 扣分: " + limit : ""));
                                        }
                                    });
                                }
                            }
                        }
                    }
                } finally {
                    response.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "加载用户信息失败: " + e.getMessage());
            }
        });
    }

    private void setupClickListeners() {
        layoutNotificationPermission.setOnClickListener(v -> SystemPermissionChecker.openNotificationSettings(this));
        layoutExactAlarmPermission.setOnClickListener(v -> SystemPermissionChecker.openExactAlarmSettings(this));
        layoutBatteryOptimization.setOnClickListener(v -> {
            if (isHuaweiFamilyDevice()) {
                Toast.makeText(this, "华为/荣耀设备将打开应用信息页，请在“电池/后台运行”中手动允许后台运行", Toast.LENGTH_LONG)
                        .show();
            }
            SystemPermissionChecker.openBatteryOptimizationSettings(this);
        });
        layoutAutoStartPermission.setOnClickListener(v -> {
            if (isHuaweiFamilyDevice()) {
                Toast.makeText(this, "华为/荣耀设备将打开应用信息页，请在“启动管理”中允许自启动与关联启动", Toast.LENGTH_LONG)
                        .show();
            }
            SystemPermissionChecker.openAutoStartSettings(this);
        });
        checkboxHidePermissionCard.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setHidePermissionCheck(isChecked);
            updatePermissionCardVisibility();
        });

        layoutTargetArea.setOnClickListener(v -> showAreaPicker());
        layoutTargetSeat.setOnClickListener(v -> showSeatPicker());
        layoutStartTime.setOnClickListener(v -> showTimePicker(true));
        layoutEndTime.setOnClickListener(v -> showTimePicker(false));
        switchAutoReserve.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setAutoReserveEnabled(isChecked);
            String targetSummary = buildTargetConfigSummary();
            if (isChecked) {
                scheduleAutoReserve();
                Toast.makeText(this, "自动预约已开启，将在每天7点执行", Toast.LENGTH_SHORT).show();
                showFeatureNotification(
                        NOTIFY_ID_AUTO_RESERVE,
                        "自动预约已开启",
                        "每日 07:00 自动执行预约任务\n" + targetSummary);
            } else {
                cancelAutoReserve();
                Toast.makeText(this, "自动预约已关闭", Toast.LENGTH_SHORT).show();
                showFeatureNotification(
                        NOTIFY_ID_AUTO_RESERVE,
                        "自动预约已关闭",
                        "已取消自动预约任务\n" + targetSummary);
            }
        });
        switchLateProtection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setLateProtectionEnabled(isChecked);
            String targetSummary = buildTargetConfigSummary();
            if (isChecked) {
                scheduleLateProtection();
                Toast.makeText(this, "迟到保护已开启", Toast.LENGTH_SHORT).show();
                showFeatureNotification(
                        NOTIFY_ID_LATE_PROTECTION,
                        "迟到保护已开启",
                        "已开始安排迟到保护检查任务\n" + targetSummary);
            } else {
                cancelLateProtection();
                Toast.makeText(this, "迟到保护已关闭", Toast.LENGTH_SHORT).show();
                showFeatureNotification(
                        NOTIFY_ID_LATE_PROTECTION,
                        "迟到保护已关闭",
                        "已取消迟到保护任务\n" + targetSummary);
            }
        });
        switchAutoFindSeat.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setAutoFindSeatEnabled(isChecked);
            String targetSummary = buildTargetConfigSummary();
            if (isChecked) {
                Toast.makeText(this, "自动寻座已开启", Toast.LENGTH_SHORT).show();
                showFeatureNotification(
                        NOTIFY_ID_AUTO_FIND,
                        "自动寻座已开启",
                        "目标座位冲突时将自动尝试备选座位\n" + targetSummary);
            } else {
                Toast.makeText(this, "自动寻座已关闭", Toast.LENGTH_SHORT).show();
                showFeatureNotification(
                        NOTIFY_ID_AUTO_FIND,
                        "自动寻座已关闭",
                        "将不再自动尝试备选座位\n" + targetSummary);
            }
        });
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setDarkModeEnabled(isChecked);
            AppCompatDelegate.setDefaultNightMode(isChecked
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
            recreate();
        });
        layoutLogout.setOnClickListener(v -> showLogoutConfirm());
        layoutGithub.setOnClickListener(v -> openGithubPage());
    }

    private void scheduleAutoReserve() {
        Intent serviceIntent = new Intent(this, AutoReserveService.class);
        serviceIntent.setAction(AutoReserveService.ACTION_SCHEDULE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void cancelAutoReserve() {
        Intent serviceIntent = new Intent(this, AutoReserveService.class);
        serviceIntent.setAction(AutoReserveService.ACTION_CANCEL);
        startService(serviceIntent);
    }

    private void scheduleLateProtection() {
        Intent serviceIntent = new Intent(this, LateProtectionService.class);
        serviceIntent.setAction(LateProtectionService.ACTION_SCHEDULE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void cancelLateProtection() {
        Intent serviceIntent = new Intent(this, LateProtectionService.class);
        serviceIntent.setAction(LateProtectionService.ACTION_CANCEL);
        startService(serviceIntent);
    }

    private void showAreaPicker() {
        List<String> areaKeys = new ArrayList<>(Constants.SEAT_AREAS_MAP.keySet());
        String[] areaNames = new String[areaKeys.size()];
        for (int i = 0; i < areaKeys.size(); i++) {
            Constants.AreaInfo info = Constants.SEAT_AREAS_MAP.get(areaKeys.get(i));
            areaNames[i] = info != null ? info.name : areaKeys.get(i);
        }
        String currentArea = preferenceManager.getTargetArea();
        int currentIndex = areaKeys.indexOf(currentArea);
        if (currentIndex < 0)
            currentIndex = 0;
        new AlertDialog.Builder(this)
                .setTitle("选择目标区域")
                .setSingleChoiceItems(areaNames, currentIndex, (dialog, which) -> {
                    String selectedKey = areaKeys.get(which);
                    preferenceManager.setTargetArea(selectedKey);
                    tvTargetArea.setText(areaNames[which]);
                    Constants.AreaInfo areaInfo = Constants.SEAT_AREAS_MAP.get(selectedKey);
                    if (areaInfo != null && areaInfo.seatIds.length > 0) {
                        preferenceManager.setTargetSeat(1);
                        tvTargetSeat.setText("1");
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSeatPicker() {
        String areaKey = preferenceManager.getTargetArea();
        Constants.AreaInfo areaInfo = Constants.SEAT_AREAS_MAP.get(areaKey);
        if (areaInfo == null) {
            Toast.makeText(this, "请先选择目标区域", Toast.LENGTH_SHORT).show();
            return;
        }
        int maxSeat = areaInfo.seatCount;
        int currentSeat = preferenceManager.getTargetSeat();
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(currentSeat));
        input.selectAll();
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = padding;
        params.rightMargin = padding;
        input.setLayoutParams(params);
        container.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("输入目标座位号 (1-" + maxSeat + ")")
                .setView(container)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = input.getText().toString();
                    if (text.isEmpty())
                        return;
                    try {
                        int seatNum = Integer.parseInt(text);
                        if (seatNum < 1 || seatNum > maxSeat) {
                            Toast.makeText(this, "座位号超出范围 (1-" + maxSeat + ")", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        preferenceManager.setTargetSeat(seatNum);
                        tvTargetSeat.setText(String.valueOf(seatNum));
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showTimePicker(boolean isStartTime) {
        String fallback = isStartTime ? Constants.DEFAULT_START_TIME : Constants.DEFAULT_END_TIME;
        String currentTime = isStartTime ? preferenceManager.getStartTime() : preferenceManager.getEndTime();
        int[] hm = parseTimeOrDefault(currentTime, fallback);

        new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    String selectedTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                    if (isStartTime) {
                        preferenceManager.setStartTime(selectedTime);
                        tvStartTime.setText(selectedTime);
                    } else {
                        preferenceManager.setEndTime(selectedTime);
                        tvEndTime.setText(selectedTime);
                    }
                },
                hm[0],
                hm[1],
                true).show();
    }

    private int[] parseTimeOrDefault(String value, String fallback) {
        String target = (value == null || value.trim().isEmpty()) ? fallback : value.trim();
        try {
            String[] parts = target.split(":");
            if (parts.length >= 2) {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                if (h >= 0 && h <= 23 && m >= 0 && m <= 59) {
                    return new int[] { h, m };
                }
            }
        } catch (Exception ignored) {
        }
        try {
            String[] parts = fallback.split(":");
            return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
        } catch (Exception ignored) {
            return new int[] { 7, 30 };
        }
    }

    private void showLogoutConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？退出后需要重新输入账号密码。")
                .setPositiveButton("确定", (dialog, which) -> {
                    performLogout();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void performLogout() {
        cancelAutoReserve();
        cancelLateProtection();
        preferenceManager.clearCredentials();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String buildTargetConfigSummary() {
        String areaKey = preferenceManager.getTargetArea();
        String areaName = preferenceManager.getAreaName(areaKey);
        if (areaName == null || areaName.trim().isEmpty()) {
            areaName = "未设置区域";
        }

        int seatNumber = preferenceManager.getTargetSeat();
        String seatText = seatNumber > 0 ? seatNumber + "号" : "未设置座位";

        String startTime = preferenceManager.getStartTime();
        String endTime = preferenceManager.getEndTime();
        if (startTime == null || startTime.trim().isEmpty()) {
            startTime = Constants.DEFAULT_START_TIME;
        }
        if (endTime == null || endTime.trim().isEmpty()) {
            endTime = Constants.DEFAULT_END_TIME;
        }

        return "目标：" + areaName + " " + seatText + "\n时段：" + startTime + " - " + endTime;
    }

    private void showFeatureNotification(int notifyId, String title, String message) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        FEATURE_NOTIFY_CHANNEL_ID,
                        "功能状态通知",
                        NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("自动预约/迟到保护/自动寻座状态通知");
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

    private boolean isHuaweiFamilyDevice() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        return manufacturer.contains("huawei") || manufacturer.contains("honor");
    }

    private void checkSystemPermissions() {
        boolean hasNotification = SystemPermissionChecker.isNotificationPermissionGranted(this);
        tvNotificationStatus.setText(hasNotification
                ? getString(R.string.permission_status_granted)
                : getString(R.string.permission_status_not_granted));
        tvNotificationStatus.setTextColor(getResources().getColor(
                hasNotification ? R.color.success : R.color.error, null));
        boolean hasExactAlarm = SystemPermissionChecker.isExactAlarmPermissionGranted(this);
        tvExactAlarmStatus.setText(hasExactAlarm
                ? getString(R.string.permission_status_granted)
                : getString(R.string.permission_status_not_granted));
        tvExactAlarmStatus.setTextColor(getResources().getColor(
                hasExactAlarm ? R.color.success : R.color.error, null));
        boolean isHuaweiFamily = isHuaweiFamilyDevice();
        if (isHuaweiFamily) {
            // HarmonyOS/EMUI 无法稳定读取后台运行与自启动状态，统一改为手动检查提示
            tvBatteryOptimizationStatus.setText(getString(R.string.permission_status_check_manually));
            tvBatteryOptimizationStatus.setTextColor(getResources().getColor(R.color.text_secondary, null));
        } else {
            boolean batteryOptDisabled = SystemPermissionChecker.isBatteryOptimizationDisabled(this);
            tvBatteryOptimizationStatus.setText(batteryOptDisabled
                    ? getString(R.string.permission_status_granted)
                    : getString(R.string.permission_status_not_granted));
            tvBatteryOptimizationStatus.setTextColor(getResources().getColor(
                    batteryOptDisabled ? R.color.success : R.color.error, null));
        }
        tvAutoStartStatus.setText(getString(R.string.permission_status_check_manually));
        tvAutoStartStatus.setTextColor(getResources().getColor(R.color.text_secondary, null));
    }

    private void updatePermissionCardVisibility() {
        boolean allGranted = SystemPermissionChecker.areAllPermissionsGranted(this);
        boolean hideByUser = preferenceManager.isHidePermissionCheck();
        if (hideByUser || (allGranted && checkboxHidePermissionCard.isChecked())) {
            cardPermissionCheck.setVisibility(View.GONE);
        } else {
            cardPermissionCheck.setVisibility(View.VISIBLE);
        }
        checkboxHidePermissionCard.setChecked(hideByUser);
    }

    private void openGithubPage() {
        String url = getString(R.string.setting_github_url);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkSystemPermissions();
        updatePermissionCardVisibility();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}