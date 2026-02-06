package com.keggin.fucknjfulib.activities;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
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
import com.keggin.fucknjfulib.BuildConfig;
import com.keggin.fucknjfulib.R;
import com.keggin.fucknjfulib.services.AutoReserveService;
import com.keggin.fucknjfulib.services.LateProtectionService;
import com.keggin.fucknjfulib.storage.PreferenceManager;
import com.keggin.fucknjfulib.utils.Constants;
import com.keggin.fucknjfulib.utils.SystemPermissionChecker;
import java.util.ArrayList;
import java.util.List;
public class SettingsActivity extends AppCompatActivity {
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
    private LinearLayout layoutPlanTasks;
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
    private LinearLayout layoutGithub;
    private PreferenceManager preferenceManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        preferenceManager = new PreferenceManager(this);
        initViews();
        setupToolbar();
        loadSettings();
        setupClickListeners();
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
        layoutPlanTasks = findViewById(R.id.layoutPlanTasks);
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
        tvStartTime.setText(preferenceManager.getStartTime());
        tvEndTime.setText(preferenceManager.getEndTime());
        switchAutoReserve.setChecked(preferenceManager.isAutoReserveEnabled());
        switchLateProtection.setChecked(preferenceManager.isLateProtectionEnabled());
        switchAutoFindSeat.setChecked(preferenceManager.isAutoFindSeatEnabled());
        switchDarkMode.setChecked(preferenceManager.isDarkModeEnabled());
        tvStudentId.setText("学号：" + preferenceManager.getStudentId());
        tvVersion.setText(BuildConfig.VERSION_NAME);
    }
    private void setupClickListeners() {
        layoutNotificationPermission.setOnClickListener(v ->
            SystemPermissionChecker.openNotificationSettings(this));
        layoutExactAlarmPermission.setOnClickListener(v ->
            SystemPermissionChecker.openExactAlarmSettings(this));
        layoutBatteryOptimization.setOnClickListener(v ->
            SystemPermissionChecker.openBatteryOptimizationSettings(this));
        layoutAutoStartPermission.setOnClickListener(v ->
            SystemPermissionChecker.openAutoStartSettings(this));
        checkboxHidePermissionCard.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setHidePermissionCheck(isChecked);
            updatePermissionCardVisibility();
        });
        if (layoutPlanTasks != null) {
            layoutPlanTasks.setOnClickListener(v -> openPlanTasks());
        }
        layoutTargetArea.setOnClickListener(v -> showAreaPicker());
        layoutTargetSeat.setOnClickListener(v -> showSeatPicker());
        layoutStartTime.setOnClickListener(v -> showTimePicker(true));
        layoutEndTime.setOnClickListener(v -> showTimePicker(false));
        switchAutoReserve.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setAutoReserveEnabled(isChecked);
            if (isChecked) {
                scheduleAutoReserve();
                Toast.makeText(this, "自动预约已开启，将在每天7点执行", Toast.LENGTH_SHORT).show();
            } else {
                cancelAutoReserve();
                Toast.makeText(this, "自动预约已关闭", Toast.LENGTH_SHORT).show();
            }
        });
        switchLateProtection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setLateProtectionEnabled(isChecked);
            if (isChecked) {
                scheduleLateProtection();
                Toast.makeText(this, "迟到保护已开启", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "迟到保护已关闭", Toast.LENGTH_SHORT).show();
            }
        });
        switchAutoFindSeat.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setAutoFindSeatEnabled(isChecked);
            if (isChecked) {
                Toast.makeText(this, "自动寻座已开启", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "自动寻座已关闭", Toast.LENGTH_SHORT).show();
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
    private void openPlanTasks() {
        Intent intent = new Intent(this, PlanTasksActivity.class);
        startActivity(intent);
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
    private void showAreaPicker() {
        List<String> areaKeys = new ArrayList<>(Constants.SEAT_AREAS_MAP.keySet());
        String[] areaNames = new String[areaKeys.size()];
        for (int i = 0; i < areaKeys.size(); i++) {
            Constants.AreaInfo info = Constants.SEAT_AREAS_MAP.get(areaKeys.get(i));
            areaNames[i] = info != null ? info.name : areaKeys.get(i);
        }
        String currentArea = preferenceManager.getTargetArea();
        int currentIndex = areaKeys.indexOf(currentArea);
        if (currentIndex < 0) currentIndex = 0;
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
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = padding;
        params.rightMargin = padding;
        input.setLayoutParams(params);
        container.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("输入目标座位号 (1-" + maxSeat + ")")
                .setView(container)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = input.getText().toString();
                    if (text.isEmpty()) return;
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
        String[] timeOptions = getResources().getStringArray(R.array.time_options);
        String currentTime = isStartTime ? 
                preferenceManager.getStartTime() : 
                preferenceManager.getEndTime();
        int currentIndex = 0;
        for (int i = 0; i < timeOptions.length; i++) {
            if (timeOptions[i].equals(currentTime)) {
                currentIndex = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(isStartTime ? "选择开始时间" : "选择结束时间")
                .setSingleChoiceItems(timeOptions, currentIndex, (dialog, which) -> {
                    String selectedTime = timeOptions[which];
                    if (isStartTime) {
                        preferenceManager.setStartTime(selectedTime);
                        tvStartTime.setText(selectedTime);
                    } else {
                        preferenceManager.setEndTime(selectedTime);
                        tvEndTime.setText(selectedTime);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
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
        preferenceManager.clearCredentials();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
        boolean batteryOptDisabled = SystemPermissionChecker.isBatteryOptimizationDisabled(this);
        tvBatteryOptimizationStatus.setText(batteryOptDisabled
                ? getString(R.string.permission_status_granted)
                : getString(R.string.permission_status_not_granted));
        tvBatteryOptimizationStatus.setTextColor(getResources().getColor(
                batteryOptDisabled ? R.color.success : R.color.error, null));
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
}