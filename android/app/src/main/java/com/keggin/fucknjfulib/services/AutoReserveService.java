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
        if (intent == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        LocalLogManager.getInstance(AutoReserveService.this).i(TAG, "收到动作: " + action);
        if (ACTION_SCHEDULE.equals(action)) {
            scheduleAutoReserve();
            showScheduledNotification();
        } else if (ACTION_EXECUTE.equals(action)) {
            startForeground(NOTIFICATION_ID, createExecutingNotification());
            executeAutoReserve();
        } else if (ACTION_CANCEL.equals(action)) {
            cancelAutoReserve();
            stopSelf();
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
    }
    private void executeAutoReserve() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(5 * 60 * 1000L); 
        }
        executor.execute(() -> {
            try {
                LocalLogManager.getInstance(AutoReserveService.this).i(TAG, "开始执行自动预约...");
                AuthManager authManager = AuthManager.getInstance(this);
                SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
                if (!prefs.getBoolean(Constants.PREF_AUTO_RESERVE, false)) {
                    LocalLogManager.getInstance(AutoReserveService.this).i(TAG, "自动预约未启用");
                    showResultNotification(false, "自动预约未启用");
                    return;
                }
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
                String closeTime = getTomorrowCloseTime();
                endTime = clampEndTime(endTime, closeTime);
                if (areaName == null || seatNumber <= 0) {
                    LocalLogManager.getInstance(AutoReserveService.this).e(TAG, "预约设置不完整");
                    showResultNotification(false, "请先设置预约信息");
                    return;
                }
                LocalLogManager.getInstance(AutoReserveService.this).i(TAG, "正在认证...");
                if (!authManager.refreshAuth()) {
                    LocalLogManager.getInstance(AutoReserveService.this).e(TAG, "认证失败: " + authManager.getErrorMessage());
                    showResultNotification(false, "认证失败: " + authManager.getErrorMessage());
                    return;
                }
                String tomorrow = DateUtils.getTomorrowDate();
                SeatReservation.ReserveResult result;
                if (autoFindSeat) {
                    AutoFinder autoFinder = new AutoFinder(authManager);
                    AutoFinder.AutoFindResult findResult = autoFinder.tryReserveWithAutoFind(
                            areaName, seatNumber, tomorrow, startTime, endTime, true);
                    if (findResult.success) {
                        String msg = findResult.reservedSeat != null 
                                ? "自动寻座成功: " + findResult.reservedSeat.devName
                                : "预约成功";
                        showResultNotification(true, msg);
                    } else {
                        showResultNotification(false, findResult.message);
                    }
                } else {
                    SeatReservation reservation = new SeatReservation(authManager);
                    result = reservation.reserveSeat(areaName, seatNumber, tomorrow, startTime, endTime);
                    showResultNotification(result.success, result.message);
                }
                scheduleAutoReserve();
            } catch (Exception e) {
                LocalLogManager.getInstance(AutoReserveService.this).e(TAG, "自动预约出错: " + e.getMessage(), e);
                showResultNotification(false, "自动预约出错: " + e.getMessage());
            } finally {
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
        getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
                .edit()
                .remove("next_reserve_time")
                .apply();
        LocalLogManager.getInstance(AutoReserveService.this).i(TAG, "自动预约已取消");
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
        long nextTime = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
                .getLong("next_reserve_time", 0);
        String timeStr = nextTime > 0 
                ? DateUtils.formatTimestamp(nextTime)
                : "未设置";
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("自动预约已开启")
                .setContentText("下次预约时间: " + timeStr)
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
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID + 2, notification);
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