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

/**
 * 迟到保护服务
 * 在预约开始前20分钟检查用户是否已签到
 * 如果未签到，取消当前预约并延后1小时重新预约
 */
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
        Log.d(TAG, "收到动作: " + action);
        
        if (ACTION_SCHEDULE.equals(action)) {
            // 设置迟到检查任务
            scheduleCheckForTodayReservations();
        } else if (ACTION_CHECK.equals(action)) {
            // 执行迟到检查
            startForeground(NOTIFICATION_ID, createCheckingNotification());
            String uuid = intent.getStringExtra(EXTRA_RESERVATION_UUID);
            long beginTime = intent.getLongExtra(EXTRA_BEGIN_TIME, 0);
            executeLateCheck(uuid, beginTime);
        }
        
        return START_NOT_STICKY;
    }
    
    /**
     * 为今日预约设置迟到检查任务
     */
    private void scheduleCheckForTodayReservations() {
        executor.execute(() -> {
            try {
                AuthManager authManager = AuthManager.getInstance(this);
                // 与设置页一致的明文首选项
                SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
                
                // 检查是否启用迟到保护
                if (!prefs.getBoolean(Constants.PREF_PREVENT_LATE, false)) {
                    Log.d(TAG, "迟到保护未启用");
                    return;
                }
                
                // 需要先认证
                if (!authManager.isAuthenticated()) {
                    if (!authManager.authenticate()) {
                        Log.e(TAG, "认证失败，无法检查预约");
                        return;
                    }
                }
                
                // 获取今日预约
                SeatReservation reservation = new SeatReservation(authManager);
                List<SeatReservation.ReservationInfo> todayReservations = 
                        reservation.getTodayReservations();
                
                if (todayReservations.isEmpty()) {
                    Log.d(TAG, "今日无预约");
                    return;
                }
                
                // 为每个预约设置检查任务
                for (SeatReservation.ReservationInfo info : todayReservations) {
                    scheduleCheckForReservation(info);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "设置迟到检查任务出错: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * 为单个预约设置检查任务
     */
    private void scheduleCheckForReservation(SeatReservation.ReservationInfo info) {
        long checkTime = info.beginTime - Constants.LATE_CHECK_MINUTES_BEFORE * 60 * 1000;
        long now = System.currentTimeMillis();
        
        // 如果检查时间已过，跳过
        if (checkTime <= now) {
            Log.d(TAG, "预约 " + info.uuid + " 的检查时间已过");
            return;
        }
        
        Log.d(TAG, "设置迟到检查: " + info.seatName + ", 检查时间: " + 
                DateUtils.formatTimestamp(checkTime));
        
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        
        Intent intent = new Intent(this, LateProtectionReceiver.class);
        intent.setAction(ACTION_CHECK);
        intent.putExtra(EXTRA_RESERVATION_UUID, info.uuid);
        intent.putExtra(EXTRA_BEGIN_TIME, info.beginTime);
        
        // 使用 UUID 的 hashCode 作为请求码，确保每个预约有独立的 PendingIntent
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
    
    /**
     * 执行迟到检查
     */
    private void executeLateCheck(String uuid, long beginTime) {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(3 * 60 * 1000L);
        }
        
        executor.execute(() -> {
            try {
                Log.d(TAG, "执行迟到检查: " + uuid);
                
                AuthManager authManager = AuthManager.getInstance(this);
                // 与设置页一致的明文首选项
                SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
                
                // 再次检查是否启用
                if (!prefs.getBoolean(Constants.PREF_PREVENT_LATE, false)) {
                    Log.d(TAG, "迟到保护已关闭");
                    return;
                }
                
                // 认证
                if (!authManager.isAuthenticated()) {
                    if (!authManager.authenticate()) {
                        Log.e(TAG, "认证失败");
                        showResultNotification(false, "迟到保护检查失败：认证失败");
                        return;
                    }
                }
                
                // 获取当前预约状态
                SeatReservation reservation = new SeatReservation(authManager);
                List<SeatReservation.ReservationInfo> reservations = 
                        reservation.getTodayReservations();
                
                // 查找目标预约
                SeatReservation.ReservationInfo targetResv = null;
                for (SeatReservation.ReservationInfo info : reservations) {
                    if (info.uuid.equals(uuid)) {
                        targetResv = info;
                        break;
                    }
                }
                
                if (targetResv == null) {
                    Log.d(TAG, "预约不存在或已被取消");
                    return;
                }
                
                // 检查状态
                String status = targetResv.statusName;
                if (status != null && (status.contains("使用") || status.contains("签到"))) {
                    Log.d(TAG, "用户已签到，无需保护");
                    showResultNotification(true, "您已签到，无需迟到保护");
                    return;
                }
                
                // 用户未签到，执行迟到保护
                Log.d(TAG, "用户未签到，执行迟到保护...");
                
                // 取消当前预约
                SeatReservation.ReserveResult cancelResult = reservation.cancelReservation(uuid);
                if (!cancelResult.success) {
                    Log.e(TAG, "取消预约失败: " + cancelResult.message);
                    showResultNotification(false, "迟到保护失败：无法取消原预约");
                    return;
                }
                
                Log.d(TAG, "已取消原预约，准备重新预约...");
                showResultNotification(true, "迟到保护：已取消原预约，正在重新预约...");
                
                // 计算新的开始时间（延后1小时）
                String newStartTime = DateUtils.addHours(
                        DateUtils.formatTimestampToTime(beginTime), 
                        Constants.LATE_PROTECTION_DELAY_HOURS);
                
                // 获取预约设置
                String areaName = prefs.getString(Constants.PREF_TARGET_AREA, null);
                int seatNumber = prefs.getInt(Constants.PREF_TARGET_SEAT, 0);
                String endTime = prefs.getString(Constants.PREF_END_TIME, "22:00");
                
                // 检查剩余时长是否足够
                if (!DateUtils.isValidDuration(newStartTime, endTime + ":00", 2)) {
                    Log.d(TAG, "剩余时间不足2小时，不再重新预约");
                    showResultNotification(true, 
                            "迟到保护：已取消原预约，剩余时间不足2小时，不再重新预约");
                    return;
                }
                
                // 重新预约
                SeatReservation.ReserveResult reserveResult = reservation.reserveTodaySeat(
                        areaName, seatNumber, newStartTime, endTime + ":00");
                
                if (reserveResult.success) {
                    Log.d(TAG, "迟到保护重新预约成功");
                    showResultNotification(true, 
                            "迟到保护成功：已重新预约，新开始时间 " + newStartTime);
                    
                    // 为新预约设置检查任务
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
                    Log.e(TAG, "迟到保护重新预约失败: " + reserveResult.message);
                    showResultNotification(false, 
                            "迟到保护：已取消原预约，但重新预约失败：" + reserveResult.message);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "迟到检查出错: " + e.getMessage(), e);
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