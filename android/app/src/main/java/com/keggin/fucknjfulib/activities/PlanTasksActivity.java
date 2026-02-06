package com.keggin.fucknjfulib.activities;
import android.text.InputType;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.keggin.fucknjfulib.R;
import com.keggin.fucknjfulib.storage.PreferenceManager;
import com.keggin.fucknjfulib.utils.Constants;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
public class PlanTasksActivity extends AppCompatActivity {
    private static final String KEY_MON = "mon";
    private static final String KEY_TUE = "tue";
    private static final String KEY_WED = "wed";
    private static final String KEY_THU = "thu";
    private static final String KEY_FRI = "fri";
    private static final String KEY_SAT = "sat";
    private static final String KEY_SUN = "sun";
    private Toolbar toolbar;
    private MaterialCardView cardMonday;
    private MaterialCardView cardTuesday;
    private MaterialCardView cardWednesday;
    private MaterialCardView cardThursday;
    private MaterialCardView cardFriday;
    private MaterialCardView cardSaturday;
    private MaterialCardView cardSunday;
    private TextView tvMondaySummary;
    private TextView tvTuesdaySummary;
    private TextView tvWednesdaySummary;
    private TextView tvThursdaySummary;
    private TextView tvFridaySummary;
    private TextView tvSaturdaySummary;
    private TextView tvSundaySummary;
    private PreferenceManager preferenceManager;
    private JSONObject weeklyRoot;
    private static class DayPlan {
        boolean enabled;
        String areaName;
        int seatNumber;
        String startTime; 
        String endTime;   
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plan_tasks);
        preferenceManager = new PreferenceManager(this);
        initViews();
        setupToolbar();
        loadWeeklyPlan();
        updateAllSummaries();
        setupClickListeners();
    }
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        cardMonday = findViewById(R.id.cardMonday);
        cardTuesday = findViewById(R.id.cardTuesday);
        cardWednesday = findViewById(R.id.cardWednesday);
        cardThursday = findViewById(R.id.cardThursday);
        cardFriday = findViewById(R.id.cardFriday);
        cardSaturday = findViewById(R.id.cardSaturday);
        cardSunday = findViewById(R.id.cardSunday);
        tvMondaySummary = findViewById(R.id.tvMondaySummary);
        tvTuesdaySummary = findViewById(R.id.tvTuesdaySummary);
        tvWednesdaySummary = findViewById(R.id.tvWednesdaySummary);
        tvThursdaySummary = findViewById(R.id.tvThursdaySummary);
        tvFridaySummary = findViewById(R.id.tvFridaySummary);
        tvSaturdaySummary = findViewById(R.id.tvSaturdaySummary);
        tvSundaySummary = findViewById(R.id.tvSundaySummary);
    }
    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }
    private void setupClickListeners() {
        cardMonday.setOnClickListener(v -> openEditDialog(KEY_MON, "周一"));
        cardTuesday.setOnClickListener(v -> openEditDialog(KEY_TUE, "周二"));
        cardWednesday.setOnClickListener(v -> openEditDialog(KEY_WED, "周三"));
        cardThursday.setOnClickListener(v -> openEditDialog(KEY_THU, "周四"));
        cardFriday.setOnClickListener(v -> openEditDialog(KEY_FRI, "周五"));
        cardSaturday.setOnClickListener(v -> openEditDialog(KEY_SAT, "周六"));
        cardSunday.setOnClickListener(v -> openEditDialog(KEY_SUN, "周日"));
    }
    private void loadWeeklyPlan() {
        String json = preferenceManager.getWeeklyPlanTasksJson();
        if (json == null || json.trim().isEmpty()) {
            weeklyRoot = new JSONObject();
            return;
        }
        try {
            weeklyRoot = new JSONObject(json);
        } catch (Exception e) {
            weeklyRoot = new JSONObject();
            preferenceManager.clearWeeklyPlanTasksJson();
        }
    }
    private void persistWeeklyPlan() {
        if (weeklyRoot == null || weeklyRoot.length() == 0) {
            preferenceManager.clearWeeklyPlanTasksJson();
            return;
        }
        preferenceManager.setWeeklyPlanTasksJson(weeklyRoot.toString());
    }
    private void clearPlan(String dayKey) {
        if (weeklyRoot != null) {
            weeklyRoot.remove(dayKey);
            persistWeeklyPlan();
        }
    }
    private DayPlan getPlan(String dayKey) {
        DayPlan plan = new DayPlan();
        plan.enabled = false;
        plan.areaName = preferenceManager.getTargetArea();
        plan.seatNumber = preferenceManager.getTargetSeat();
        plan.startTime = getDefaultStartTimeFromPhone();
        plan.endTime = getDefaultCloseTime(dayKey);
        if (weeklyRoot == null) {
            return plan;
        }
        JSONObject obj = weeklyRoot.optJSONObject(dayKey);
        if (obj == null) {
            return plan;
        }
        plan.enabled = obj.optBoolean("enabled", false);
        plan.areaName = obj.optString("area", plan.areaName);
        plan.seatNumber = obj.optInt("seat", plan.seatNumber);
        plan.startTime = obj.optString("start", plan.startTime);
        plan.endTime = obj.optString("end", plan.endTime);
        return plan;
    }
    private void savePlan(String dayKey, DayPlan plan) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("enabled", plan.enabled);
            obj.put("area", plan.areaName);
            obj.put("seat", plan.seatNumber);
            obj.put("start", plan.startTime);
            obj.put("end", plan.endTime);
            if (weeklyRoot == null) {
                weeklyRoot = new JSONObject();
            }
            weeklyRoot.put(dayKey, obj);
            persistWeeklyPlan();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private void updateAllSummaries() {
        updateDaySummary(KEY_MON, tvMondaySummary);
        updateDaySummary(KEY_TUE, tvTuesdaySummary);
        updateDaySummary(KEY_WED, tvWednesdaySummary);
        updateDaySummary(KEY_THU, tvThursdaySummary);
        updateDaySummary(KEY_FRI, tvFridaySummary);
        updateDaySummary(KEY_SAT, tvSaturdaySummary);
        updateDaySummary(KEY_SUN, tvSundaySummary);
    }
    private void updateDaySummary(String dayKey, TextView tv) {
        DayPlan plan = getPlan(dayKey);
        if (plan.enabled) {
            tv.setText(plan.areaName + " - 座位" + plan.seatNumber + "  " + plan.startTime + " - " + plan.endTime);
        } else {
            boolean hasStored = weeklyRoot != null && weeklyRoot.optJSONObject(dayKey) != null;
            tv.setText(hasStored ? "未启用" : "未设置");
        }
    }
    private void openEditDialog(String dayKey, String dayLabel) {
        DayPlan plan = getPlan(dayKey);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        int innerPadding = (int) (12 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        SwitchMaterial swEnable = new SwitchMaterial(this);
        swEnable.setText("启用该天计划");
        swEnable.setChecked(plan.enabled);
        root.addView(swEnable);
        root.addView(makeDivider());
        LinearLayout rowArea = makeRow("区域", plan.areaName);
        TextView tvAreaValue = (TextView) rowArea.getChildAt(1);
        rowArea.setPadding(0, innerPadding, 0, innerPadding);
        rowArea.setOnClickListener(v -> showAreaPicker(plan, tvAreaValue));
        root.addView(rowArea);
        LinearLayout rowSeat = makeRow("座位号", String.valueOf(plan.seatNumber));
        TextView tvSeatValue = (TextView) rowSeat.getChildAt(1);
        rowSeat.setPadding(0, innerPadding, 0, innerPadding);
        rowSeat.setOnClickListener(v -> showSeatPicker(plan, tvSeatValue));
        root.addView(rowSeat);
        LinearLayout rowStart = makeRow("开始时间", plan.startTime);
        TextView tvStartValue = (TextView) rowStart.getChildAt(1);
        rowStart.setPadding(0, innerPadding, 0, innerPadding);
        rowStart.setOnClickListener(v -> showTimePickerForDay(dayKey, true, tvStartValue, t -> plan.startTime = t));
        root.addView(rowStart);
        LinearLayout rowEnd = makeRow("结束时间", plan.endTime);
        TextView tvEndValue = (TextView) rowEnd.getChildAt(1);
        rowEnd.setPadding(0, innerPadding, 0, innerPadding);
        rowEnd.setOnClickListener(v -> showTimePickerForDay(dayKey, false, tvEndValue, t -> plan.endTime = t));
        root.addView(rowEnd);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(dayLabel + "计划")
                .setView(root)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .setNeutralButton("清除", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                plan.enabled = swEnable.isChecked();
                if (plan.enabled) {
                    String err = validatePlan(plan, dayKey);
                    if (err != null) {
                        Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                savePlan(dayKey, plan);
                updateAllSummaries();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                clearPlan(dayKey);
                updateAllSummaries();
                dialog.dismiss();
            });
        });
        dialog.show();
    }
    @Nullable
    private String validatePlan(DayPlan plan, String dayKey) {
        if (plan.areaName == null || plan.areaName.trim().isEmpty()) {
            return "请选择区域";
        }
        Constants.SeatArea area = Constants.getAreaByName(plan.areaName);
        if (area == null) {
            return "区域无效";
        }
        if (plan.seatNumber < 1 || plan.seatNumber > area.seatsCount) {
            return "座位号超出范围 (1-" + area.seatsCount + ")";
        }
        if (plan.startTime == null || plan.startTime.trim().isEmpty()
                || plan.endTime == null || plan.endTime.trim().isEmpty()) {
            return "请选择时间段";
        }
        Integer start = parseTimeToMinutes(plan.startTime);
        Integer end = parseTimeToMinutes(plan.endTime);
        if (start == null || end == null) {
            return "时间格式不正确";
        }
        if (start >= end) {
            return "结束时间必须晚于开始时间";
        }
        String closeTime = getDefaultCloseTime(dayKey);
        Integer closeMinutes = parseTimeToMinutes(closeTime);
        if (closeMinutes != null && end > closeMinutes) {
            return "结束时间不能晚于闭馆时间 (" + closeTime + ")";
        }
        return null;
    }
    private interface TimeSelectedCallback {
        void onSelected(String time);
    }
    private void showTimePickerForDay(String dayKey, boolean isStart, TextView tvValue, TimeSelectedCallback callback) {
        String[] allTimeOptions = getResources().getStringArray(R.array.time_options);
        String[] timeOptions = getTimeOptionsForDayKey(dayKey, allTimeOptions);
        String current = tvValue.getText().toString();
        int currentIndex = 0;
        for (int i = 0; i < timeOptions.length; i++) {
            if (timeOptions[i].equals(current)) {
                currentIndex = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(isStart ? "选择开始时间" : "选择结束时间")
                .setSingleChoiceItems(timeOptions, currentIndex, (dialog, which) -> {
                    String selected = timeOptions[which];
                    tvValue.setText(selected);
                    callback.onSelected(selected);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    private String[] getTimeOptionsForDayKey(String dayKey, String[] allTimeOptions) {
        String closeTime = getDefaultCloseTime(dayKey);
        Integer closeMinutes = parseTimeToMinutes(closeTime);
        if (closeMinutes == null) {
            return allTimeOptions;
        }
        List<String> filtered = new ArrayList<>();
        for (String opt : allTimeOptions) {
            Integer m = parseTimeToMinutes(opt);
            if (m != null && m <= closeMinutes) {
                filtered.add(opt);
            }
        }
        return filtered.toArray(new String[0]);
    }
    private void showAreaPicker(DayPlan plan, TextView tvAreaValue) {
        List<String> areaKeys = new ArrayList<>(Constants.SEAT_AREAS_MAP.keySet());
        String[] areaNames = new String[areaKeys.size()];
        for (int i = 0; i < areaKeys.size(); i++) {
            areaNames[i] = areaKeys.get(i);
        }
        int currentIndex = areaKeys.indexOf(plan.areaName);
        if (currentIndex < 0) currentIndex = 0;
        new AlertDialog.Builder(this)
                .setTitle("选择区域")
                .setSingleChoiceItems(areaNames, currentIndex, (dialog, which) -> {
                    plan.areaName = areaKeys.get(which);
                    tvAreaValue.setText(plan.areaName);
                    Constants.SeatArea area = Constants.getAreaByName(plan.areaName);
                    if (area != null && plan.seatNumber > area.seatsCount) {
                        plan.seatNumber = 1;
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    private void showSeatPicker(DayPlan plan, TextView tvSeatValue) {
        Constants.SeatArea area = Constants.getAreaByName(plan.areaName);
        if (area == null) {
            Toast.makeText(this, "请先选择区域", Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(plan.seatNumber));
        input.selectAll();
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = padding;
        params.rightMargin = padding;
        input.setLayoutParams(params);
        container.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("输入座位号 (1-" + area.seatsCount + ")")
                .setView(container)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    try {
                        int seat = Integer.parseInt(text);
                        if (seat < 1 || seat > area.seatsCount) {
                            Toast.makeText(this, "座位号超出范围 (1-" + area.seatsCount + ")", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        plan.seatNumber = seat;
                        tvSeatValue.setText(String.valueOf(seat));
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
    private LinearLayout makeRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(14);
        tvLabel.setTextColor(getResources().getColor(R.color.text_primary, null));
        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(14);
        tvValue.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvValue.setPadding((int) (12 * getResources().getDisplayMetrics().density), 0, 0, 0);
        LinearLayout.LayoutParams lpLabel = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(tvLabel, lpLabel);
        row.addView(tvValue);
        return row;
    }
    private View makeDivider() {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (1 * getResources().getDisplayMetrics().density)
        );
        lp.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
        lp.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
        v.setLayoutParams(lp);
        v.setBackgroundColor(getResources().getColor(R.color.divider, null));
        return v;
    }
    private String getDefaultStartTimeFromPhone() {
        Calendar cal = Calendar.getInstance();
        int minute = cal.get(Calendar.MINUTE);
        int rounded = ((minute + 29) / 30) * 30;
        if (rounded >= 60) {
            cal.add(Calendar.HOUR_OF_DAY, 1);
            rounded = 0;
        }
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        return String.format(Locale.getDefault(), "%02d:%02d", hour, rounded);
    }
    private String getDefaultCloseTime(String dayKey) {
        if (KEY_FRI.equals(dayKey)) {
            return "20:00";
        }
        return "22:00";
    }
    @Nullable
    private Integer parseTimeToMinutes(String hhmm) {
        if (hhmm == null) return null;
        String s = hhmm.trim();
        String[] parts = s.split(":");
        if (parts.length != 2) return null;
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return h * 60 + m;
        } catch (Exception e) {
            return null;
        }
    }
}