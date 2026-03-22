package com.keggin.fucknjfulib.services;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import com.keggin.fucknjfulib.utils.LocalLogManager;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.keggin.fucknjfulib.R;
import com.keggin.fucknjfulib.auth.AuthManager;
import com.keggin.fucknjfulib.reservation.AutoFinder;
import com.keggin.fucknjfulib.reservation.SeatReservation;
import com.keggin.fucknjfulib.utils.Constants;
import com.keggin.fucknjfulib.utils.DateUtils;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
public class AutoReserveService extends Service {
    private static final String TAG = "AutoReserveService";
    private static final String CHANNEL_ID = "auto_reserve_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int ALARM_REQUEST_CODE = 1001;
    public static final String ACTION_SCHEDULE = "com.keggin.fucknjfulib.ACTION_SCHEDULE";
    public static final String ACTION_EXECUTE = "com.keggin.fucknjfulib.ACTION_EXECUTE";
    public static final String ACTION_CANCEL = "com.keggin.fucknjfulib.ACTION_CANCEL";
    private ExecutorService executor;
    private PowerManager.WakeLock wakeLock;
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        executor = Executors.newSingleThreadExecutor();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fucknjfulib:AutoReserve");
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createServiceNotification("自动预约服务准备中..."));
        if (intent == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        LocalLogManager.getInstance(AutoReserveService.this).i(TAG, "收到动作: " + action);
        if (ACTION_SCHEDULE.equals(action)) {
            scheduleAutoReserve();
            showScheduledNotification();
            startKeepAliveService();
            requestBatteryOptimizationIfNeeded();
            stopForeground(true);
        } else if (ACTION_EXECUTE.equals(action)) {
            startForeground(NOTIFICATION_ID, createExecutingNotification());
            executeAutoReserve();
        } else if (ACTION_CANCEL.equals(action)) {
            cancelAutoReserve();
            stopKeepAliveService();
            stopSelf();
            stopForeground(true);
        }
        return START_NOT_STICKY;
    }
    private void scheduleAutoReserve() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AutoReserveReceiver.class);
        intent.setAction(ACTION_EXECUTE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Constants.DEFAULT_RESERVE_HOUR);
        calendar.set(Calendar.MINUTE, Constants.DEFAULT_RESERVE_MINUTE);
        calendar.set(Calendar.SECOND, Constants.DEFAULT_RESERVE_SECOND);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        LocalLogManager.getInstance(AutoReserveService.this).i(TAG, "设置定时预约: " + calendar.getTime());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            } else {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }
        getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
                .edit()
                .putLong("next_reserve_time", calendar.getTimeInMillis())
                .apply();
        scheduleWorkManagerBackup(calendar.getTimeInMillis());
    }
    private void scheduleWorkManagerBackup(long targetTimeMillis) {
        try {
            long delay = targetTimeMillis - System.currentTimeMillis();
            if (delay <= 0) delay = 60 * 1000;
            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(AutoReserveWorker.class)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .addTag(AutoReserveWorker.WORK_NAME)
                    .build();
            WorkManager.getInstance(this)
                    .enqueueUniqueWork(
                            AutoReserveWorker.WORK_NAME,
                            androidx.work.ExistingWorkPolicy.REPLACE,
                            workRequest);
            LocalLogManager.getInstance(this).i(TAG,
                    "WorkManager 备份任务已注册，延迟 " + (delay / 1000 / 60) + " 分钟");
        } catch (Exception e) {
            LocalLogManager.getInstance(this).e(TAG,
                    "WorkManager 备份任务注册失败: " + e.getMessage());
        }
    }
    private void executeAutoReserve() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(5 * 60 * 1000L);
        }
        executor.execute(() -> {
            boolean shouldReschedule = false;
            try {
                LocalLogManager.getInstance(AutoReserveService.this).i(TAG, "开始执行自动预约...");
                getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
                        .edit()
                        .putLong("last_reserve_execute_time", System.currentTimeMillis())
                        .apply();
                AuthManager authManager = AuthManager.getInstance(this);
                SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
                if (!prefs.getBoolean(Constants.PREF_AUTO_RESERVE, false)) {
                    LocalLogManager.getInstance(AutoReserveService.this).i(TAG, "自动预约未启用");
                    showResultNotification(false, "自动预约未启用");
                    return;
                }
                shouldReschedule = true;
                String areaName = prefs.getString(Constants.PREF_TARGET_AREA, null);
                int seatNumber = prefs.getInt(Constants.PREF_TARGET_SEAT, 0);
                String startTime = prefs.getString(Constants.PREF_START_TIME, Constants.DEFAULT_START_TIME);
                String endTime = prefs.getString(Constants.PREF_END_TIME, Constants.DEFAULT_END_TIME);
                boolean autoFindSeat = prefs.getBoolean(Constants.PREF_AUTO_FIND_SEAT, false);
                WeeklyPlanConfig weeklyPlan = getTomorrowWeeklyPlan(prefs);
                if (weeklyPlan != null && weeklyPlan.enabled) {
                    areaName = weeklyPlan.areaName;
                    seatNumber = weeklyPlan.seatNumber;
                    startTime = weeklyPlan.startTime;
                    endTime = weeklyPlan.endTime;
                    LocalLogManager.getInstance(AutoReserveService.this).i(TAG, "使用周计划任务配置: " + areaName + " 座位" + seatNumber + " " + startTime + "-" + endTime);
                }
                String tomorrow = DateUtils.getTomorrowDate();
                String closeTime = DateUtils.getEndTimeWithoutSeconds(tomorrow);
                endTime = clampEndTime(endTime, closeTime);
                String reservationDetail = buildReservationDetail(tomorrow, areaName, seatNumber, startTime, endTime);
                if (areaName == null || areaName.trim().isEmpty() || seatNumber <= 0) {
                    LocalLogManager.getInstance(AutoReserveService.this).e(TAG, "预约设置不完整");
                    showResultNotification(false, "请先设置预约信息\n" + reservationDetail);
                    return;
                }
                LocalLogManager.getInstance(AutoReserveService.this).i(TAG, "正在认证...");
                if (!authManager.refreshAuth()) {
                    LocalLogManager.getInstance(AutoReserveService.this).e(TAG, "认证失败: " + authManager.getErrorMessage());
                    showResultNotification(false, "认证失败: " + authManager.getErrorMessage() + "\n" + reservationDetail);
                    return;
                }
                boolean reserveSucceeded = false;
                SeatReservation.ReserveResult result;
                if (autoFindSeat) {
                    AutoFinder autoFinder = new AutoFinder(authManager);
                    AutoFinder.AutoFindResult findResult = autoFinder.tryReserveWithAutoFind(
                            areaName, seatNumber, tomorrow, startTime, endTime, true);
                    if (findResult.success) {
                        reserveSucceeded = true;
                        String actualSeat = findResult.reservedSeat != null
                                ? findResult.reservedSeat.devName
                                : (resolveAreaDisplayName(areaName) + " " + seatNumber + "号");
                        showResultNotification(true,
                                "自动寻座成功：已预约 " + actualSeat + "\n" + reservationDetail);
                    } else {
                        showResultNotification(false, findResult.message + "\n" + reservationDetail);
                    }
                } else {
                    SeatReservation reservation = new SeatReservation(authManager);
                    result = reservation.reserveSeat(areaName, seatNumber, tomorrow, startTime, endTime);
                    reserveSucceeded = result.success;
                    showResultNotification(result.success, result.message + "\n" + reservationDetail);
                }
                if (reserveSucceeded) {
                    scheduleLateProtectionIfEnabled();
                }
            } catch (Exception e) {
                LocalLogManager.getInstance(AutoReserveService.this).e(TAG, "自动预约出错: " + e.getMessage(), e);
                showResultNotification(false, "自动预约出错: " + e.getMessage());
            } finally {
                if (shouldReschedule) {
                    try {
                        scheduleAutoReserve();
                    } catch (Exception e) {
                        LocalLogManager.getInstance(AutoReserveService.this).e(TAG, "重置下一次自动预约失败: " + e.getMessage(), e);
                    }
                }
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                stopForeground(true);
            }
        });
    }
    private void cancelAutoReserve() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AutoReserveReceiver.class);
        intent.setAction(ACTION_EXECUTE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(pendingIntent);
        try {
            WorkManager.getInstance(this).cancelUniqueWork(AutoReserveWorker.WORK_NAME);
        } catch (Exception e) {
            LocalLogManager.getInstance(this).e(TAG, "取消 WorkManager 任务失败: " + e.getMessage());
        }
        getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
                .edit()
                .remove("next_reserve_time")
                .remove("last_reserve_execute_time")
                .apply();
        LocalLogManager.getInstance(AutoReserveService.this).i(TAG, "自动预约已取消（AlarmManager + WorkManager）");
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "自动预约服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("图书馆座位自动预约服务");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }
    private Notification createServiceNotification(String contentText) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("自动预约")
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
    private Notification createExecutingNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("正在预约座位")
                .setContentText("正在为您预约明天的座位...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
    private void showScheduledNotification() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        long nextTime = prefs.getLong("next_reserve_time", 0);
        String timeStr = nextTime > 0
                ? DateUtils.formatTimestamp(nextTime)
                : "未设置";
        String reserveDate = getReservationDateForExecution(nextTime);
        String areaName = prefs.getString(Constants.PREF_TARGET_AREA, null);
        int seatNumber = prefs.getInt(Constants.PREF_TARGET_SEAT, 0);
        String startTime = prefs.getString(Constants.PREF_START_TIME, Constants.DEFAULT_START_TIME);
        String endTime = prefs.getString(Constants.PREF_END_TIME, Constants.DEFAULT_END_TIME);
        WeeklyPlanConfig weeklyPlan = getTomorrowWeeklyPlan(prefs);
        if (weeklyPlan != null && weeklyPlan.enabled) {
            areaName = weeklyPlan.areaName;
            seatNumber = weeklyPlan.seatNumber;
            startTime = weeklyPlan.startTime;
            endTime = weeklyPlan.endTime;
        }
        String closeTime = DateUtils.getEndTimeWithoutSeconds(reserveDate);
        endTime = clampEndTime(endTime, closeTime);
        String detail = buildReservationDetail(reserveDate, areaName, seatNumber, startTime, endTime);
        String message = "下次执行时间：" + timeStr + "\n" + detail;
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("自动预约已开启")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID + 1, notification);
    }
    private void showResultNotification(boolean success, String message) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(success ? "预约成功" : "预约失败")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID + 2, notification);
    }
    private String buildReservationDetail(String reservationDate, String areaName, int seatNumber, String startTime, String endTime) {
        String dateText = (reservationDate == null || reservationDate.trim().isEmpty())
                ? DateUtils.getTomorrowDate()
                : reservationDate;
        String displayArea = resolveAreaDisplayName(areaName);
        String seatText = seatNumber > 0 ? seatNumber + "号" : "未设置座位";
        String startText = (startTime == null || startTime.trim().isEmpty())
                ? Constants.DEFAULT_START_TIME
                : startTime;
        String endText = (endTime == null || endTime.trim().isEmpty())
                ? Constants.DEFAULT_END_TIME
                : endTime;
        return "预约日期：" + dateText
                + "\n目标座位：" + displayArea + " " + seatText
                + "\n预约时段：" + startText + " - " + endText;
    }
    private String resolveAreaDisplayName(String areaName) {
        if (areaName == null || areaName.trim().isEmpty()) {
            return "未设置区域";
        }
        Constants.AreaInfo info = Constants.SEAT_AREAS_MAP.get(areaName);
        if (info != null && info.name != null && !info.name.trim().isEmpty()) {
            return info.name;
        }
        return areaName;
    }
    private String getReservationDateForExecution(long executeAtMillis) {
        if (executeAtMillis <= 0) {
            return DateUtils.getTomorrowDate();
        }
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(executeAtMillis);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(cal.getTime());
    }
    private static class WeeklyPlanConfig {
        boolean enabled;
        String areaName;
        int seatNumber;
        String startTime;
        String endTime;
    }
    @Nullable
    private WeeklyPlanConfig getTomorrowWeeklyPlan(SharedPreferences prefs) {
        String json = prefs.getString(Constants.PREF_WEEKLY_PLAN_TASKS, null);
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        String dayKey = mapDayOfWeekToKey(cal.get(Calendar.DAY_OF_WEEK));
        if (dayKey == null) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONObject obj = root.optJSONObject(dayKey);
            if (obj == null) {
                return null;
            }
            WeeklyPlanConfig cfg = new WeeklyPlanConfig();
            cfg.enabled = obj.optBoolean("enabled", false);
            cfg.areaName = obj.optString("area", null);
            cfg.seatNumber = obj.optInt("seat", 0);
            cfg.startTime = obj.optString("start", null);
            cfg.endTime = obj.optString("end", null);
            if (!cfg.enabled) {
                return cfg;
            }
            if (cfg.areaName == null || cfg.areaName.trim().isEmpty()
                    || cfg.seatNumber <= 0
                    || cfg.startTime == null || cfg.startTime.trim().isEmpty()
                    || cfg.endTime == null || cfg.endTime.trim().isEmpty()) {
                return null;
            }
            return cfg;
        } catch (Exception e) {
            LocalLogManager.getInstance(AutoReserveService.this).e(TAG, "解析周计划任务失败: " + e.getMessage(), e);
            return null;
        }
    }
    @Nullable
    private String mapDayOfWeekToKey(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY:
                return "mon";
            case Calendar.TUESDAY:
                return "tue";
            case Calendar.WEDNESDAY:
                return "wed";
            case Calendar.THURSDAY:
                return "thu";
            case Calendar.FRIDAY:
                return "fri";
            case Calendar.SATURDAY:
                return "sat";
            case Calendar.SUNDAY:
                return "sun";
            default:
                return null;
        }
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
            LocalLogManager.getInstance(AutoReserveService.this).i(TAG, "结束时间 " + endTime + " 超过闭馆时间 " + closeTime + "，自动截断");
            return closeTime;
        }
        return endTime;
    }
    @Nullable
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
    private void scheduleLateProtectionIfEnabled() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(Constants.PREF_PREVENT_LATE, false)) {
            return;
        }
        Intent serviceIntent = new Intent(this, LateProtectionService.class);
        serviceIntent.setAction(LateProtectionService.ACTION_SCHEDULE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        LocalLogManager.getInstance(AutoReserveService.this).i(TAG, "预约成功后已触发迟到保护任务重排");
    }
    private void startKeepAliveService() {
        try {
            Intent intent = new Intent(this, KeepAliveService.class);
            intent.setAction(KeepAliveService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            LocalLogManager.getInstance(this).e(TAG, "启动保活服务失败: " + e.getMessage());
        }
    }
    private void stopKeepAliveService() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        boolean preventLate = prefs.getBoolean(Constants.PREF_PREVENT_LATE, false);
        if (!preventLate) {
            try {
                Intent intent = new Intent(this, KeepAliveService.class);
                intent.setAction(KeepAliveService.ACTION_STOP);
                startService(intent);
            } catch (Exception ignored) {}
        }
    }
    private void requestBatteryOptimizationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    LocalLogManager.getInstance(this).e(TAG, "请求电池优化豁免失败: " + e.getMessage());
                }
            }
        }
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}