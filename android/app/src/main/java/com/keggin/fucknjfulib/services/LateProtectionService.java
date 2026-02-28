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
import com.keggin.fucknjfulib.reservation.SeatReservation;
import com.keggin.fucknjfulib.utils.Constants;
import com.keggin.fucknjfulib.utils.DateUtils;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class LateProtectionService extends Service {
    private static final String TAG = "LateProtectionService";
    private static final String CHANNEL_ID = "late_protection_channel";
    private static final int NOTIFICATION_ID = 2001;
    private static final int ALARM_REQUEST_CODE = 2001;
    public static final String ACTION_CHECK = "com.keggin.fucknjfulib.ACTION_LATE_CHECK";
    public static final String ACTION_SCHEDULE = "com.keggin.fucknjfulib.ACTION_LATE_SCHEDULE";
    public static final String EXTRA_RESERVATION_UUID = "reservation_uuid";
    public static final String EXTRA_BEGIN_TIME = "begin_time";
    private ExecutorService executor;
    private PowerManager.WakeLock wakeLock;
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        executor = Executors.newSingleThreadExecutor();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fucknjfulib:LateProtection");
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "收到动作: " + action);
        if (ACTION_SCHEDULE.equals(action)) {
            scheduleCheckForTodayReservations();
        } else if (ACTION_CHECK.equals(action)) {
            startForeground(NOTIFICATION_ID, createCheckingNotification());
            String uuid = intent.getStringExtra(EXTRA_RESERVATION_UUID);
            long beginTime = intent.getLongExtra(EXTRA_BEGIN_TIME, 0);
            executeLateCheck(uuid, beginTime);
        }
        return START_NOT_STICKY;
    }
    private void scheduleCheckForTodayReservations() {
        executor.execute(() -> {
            try {
                AuthManager authManager = AuthManager.getInstance(this);
                SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
                if (!prefs.getBoolean(Constants.PREF_PREVENT_LATE, false)) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "迟到保护未启用");
                    return;
                }
                if (!authManager.isAuthenticated()) {
                    if (!authManager.authenticate()) {
                        LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "认证失败，无法检查预约");
                        return;
                    }
                }
                SeatReservation reservation = new SeatReservation(authManager);
                List<SeatReservation.ReservationInfo> todayReservations = 
                        reservation.getTodayReservations();
                if (todayReservations.isEmpty()) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "今日无预约");
                    return;
                }
                for (SeatReservation.ReservationInfo info : todayReservations) {
                    scheduleCheckForReservation(info);
                }
            } catch (Exception e) {
                LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "设置迟到检查任务出错: " + e.getMessage(), e);
            }
        });
    }
    private void scheduleCheckForReservation(SeatReservation.ReservationInfo info) {
        long checkTime = info.beginTime - Constants.LATE_CHECK_MINUTES_BEFORE * 60 * 1000;
        long now = System.currentTimeMillis();
        if (checkTime <= now) {
            LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "预约 " + info.uuid + " 的检查时间已过");
            return;
        }
        LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "设置迟到检查: " + info.seatName + ", 检查时间: " + 
                DateUtils.formatTimestamp(checkTime));
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, LateProtectionReceiver.class);
        intent.setAction(ACTION_CHECK);
        intent.putExtra(EXTRA_RESERVATION_UUID, info.uuid);
        intent.putExtra(EXTRA_BEGIN_TIME, info.beginTime);
        int requestCode = ALARM_REQUEST_CODE + info.uuid.hashCode();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    checkTime,
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    checkTime,
                    pendingIntent
            );
        }
    }
    private void executeLateCheck(String uuid, long beginTime) {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(3 * 60 * 1000L);
        }
        executor.execute(() -> {
            try {
                LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "执行迟到检查: " + uuid);
                AuthManager authManager = AuthManager.getInstance(this);
                SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
                if (!prefs.getBoolean(Constants.PREF_PREVENT_LATE, false)) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "迟到保护已关闭");
                    return;
                }
                if (!authManager.isAuthenticated()) {
                    if (!authManager.authenticate()) {
                        LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "认证失败");
                        showResultNotification(false, "迟到保护检查失败：认证失败");
                        return;
                    }
                }
                SeatReservation reservation = new SeatReservation(authManager);
                List<SeatReservation.ReservationInfo> reservations = 
                        reservation.getTodayReservations();
                SeatReservation.ReservationInfo targetResv = null;
                for (SeatReservation.ReservationInfo info : reservations) {
                    if (info.uuid.equals(uuid)) {
                        targetResv = info;
                        break;
                    }
                }
                if (targetResv == null) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "预约不存在或已被取消");
                    return;
                }
                String status = targetResv.statusName;
                if (status != null && (status.contains("使用") || status.contains("签到"))) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "用户已签到，无需保护");
                    showResultNotification(true, "您已签到，无需迟到保护");
                    return;
                }
                LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "用户未签到，执行迟到保护...");
                SeatReservation.ReserveResult cancelResult = reservation.cancelReservation(uuid);
                if (!cancelResult.success) {
                    LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "取消预约失败: " + cancelResult.message);
                    showResultNotification(false, "迟到保护失败：无法取消原预约");
                    return;
                }
                LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "已取消原预约，准备重新预约...");
                showResultNotification(true, "迟到保护：已取消原预约，正在重新预约...");
                String newStartTime = DateUtils.addHours(
                        DateUtils.formatTimestampToTime(beginTime), 
                        Constants.LATE_PROTECTION_DELAY_HOURS);
                String areaName = prefs.getString(Constants.PREF_TARGET_AREA, null);
                int seatNumber = prefs.getInt(Constants.PREF_TARGET_SEAT, 0);
                String endTime = prefs.getString(Constants.PREF_END_TIME, "22:00");
                if (!DateUtils.isValidDuration(newStartTime, endTime + ":00", 2)) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "剩余时间不足2小时，不再重新预约");
                    showResultNotification(true, 
                            "迟到保护：已取消原预约，剩余时间不足2小时，不再重新预约");
                    return;
                }
                SeatReservation.ReserveResult reserveResult = reservation.reserveTodaySeat(
                        areaName, seatNumber, newStartTime, endTime + ":00");
                if (reserveResult.success) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "迟到保护重新预约成功");
                    showResultNotification(true, 
                            "迟到保护成功：已重新预约，新开始时间 " + newStartTime);
                    Calendar cal = DateUtils.parseTimeToCalendar(
                            DateUtils.getTodayDate(), newStartTime);
                    if (cal != null) {
                        SeatReservation.ReservationInfo newInfo = new SeatReservation.ReservationInfo();
                        newInfo.uuid = reserveResult.uuid;
                        newInfo.beginTime = cal.getTimeInMillis();
                        newInfo.seatName = areaName + " " + seatNumber + "号";
                        scheduleCheckForReservation(newInfo);
                    }
                } else {
                    LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "迟到保护重新预约失败: " + reserveResult.message);
                    showResultNotification(false, 
                            "迟到保护：已取消原预约，但重新预约失败：" + reserveResult.message);
                }
            } catch (Exception e) {
                LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "迟到检查出错: " + e.getMessage(), e);
                showResultNotification(false, "迟到保护出错: " + e.getMessage());
            } finally {
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                stopForeground(true);
            }
        });
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "迟到保护服务",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("图书馆座位迟到保护服务");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }
    private Notification createCheckingNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("迟到保护")
                .setContentText("正在检查签到状态...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
    private void showResultNotification(boolean success, String message) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(success ? "迟到保护" : "迟到保护失败")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID + 1, notification);
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