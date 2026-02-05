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
import com.keggin.fucknjfulib.reservation.AutoFinder;
import com.keggin.fucknjfulib.reservation.SeatReservation;
import com.keggin.fucknjfulib.utils.Constants;
import com.keggin.fucknjfulib.utils.DateUtils;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 自动预约前台服务
 * 每天定时自动预约明天的座位
 */
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
        
        // 获取 WakeLock 防止 CPU 休眠
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fucknjfulib:AutoReserve");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        
        String action = intent.getAction();
        Log.d(TAG, "收到动作: " + action);
        
        if (ACTION_SCHEDULE.equals(action)) {
            // 设置定时任务
            scheduleAutoReserve();
            showScheduledNotification();
        } else if (ACTION_EXECUTE.equals(action)) {
            // 执行预约
            startForeground(NOTIFICATION_ID, createExecutingNotification());
            executeAutoReserve();
        } else if (ACTION_CANCEL.equals(action)) {
            // 取消定时任务
            cancelAutoReserve();
            stopSelf();
        }
        
        return START_NOT_STICKY;
    }
    
    /**
     * 设置每日定时任务
     */
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
        
        // 计算下次执行时间
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Constants.DEFAULT_RESERVE_HOUR);
        calendar.set(Calendar.MINUTE, Constants.DEFAULT_RESERVE_MINUTE);
        calendar.set(Calendar.SECOND, Constants.DEFAULT_RESERVE_SECOND);
        calendar.set(Calendar.MILLISECOND, 0);
        
        // 如果今天的时间已过，设为明天
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        Log.d(TAG, "设置定时预约: " + calendar.getTime());
        
        // 设置精确闹钟（需要 SCHEDULE_EXACT_ALARM 权限）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            } else {
                // 降级使用非精确闹钟
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
        
        // 保存状态
        getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
                .edit()
                .putLong("next_reserve_time", calendar.getTimeInMillis())
                .apply();
    }
    
    /**
     * 执行自动预约
     */
    private void executeAutoReserve() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(5 * 60 * 1000L); // 最多5分钟
        }
        
        executor.execute(() -> {
            try {
                Log.d(TAG, "开始执行自动预约...");
                
                AuthManager authManager = AuthManager.getInstance(this);
                // 使用与设置页一致的明文首选项，避免键不一致导致读不到配置
                SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
                
                // 检查是否启用了自动预约
                if (!prefs.getBoolean(Constants.PREF_AUTO_RESERVE, false)) {
                    Log.d(TAG, "自动预约未启用");
                    showResultNotification(false, "自动预约未启用");
                    return;
                }
                
                // 获取预约设置
                String areaName = prefs.getString(Constants.PREF_TARGET_AREA, null);
                int seatNumber = prefs.getInt(Constants.PREF_TARGET_SEAT, 0);
                String startTime = prefs.getString(Constants.PREF_START_TIME, "09:30");
                String endTime = prefs.getString(Constants.PREF_END_TIME, "22:00");
                boolean autoFindSeat = prefs.getBoolean(Constants.PREF_AUTO_FIND_SEAT, false);
                
                if (areaName == null || seatNumber <= 0) {
                    Log.e(TAG, "预约设置不完整");
                    showResultNotification(false, "请先设置预约信息");
                    return;
                }
                
                // 执行认证
                // 执行认证
                Log.d(TAG, "正在认证...");
                // 强制重新认证，确保 Cookie 是新的
                if (!authManager.refreshAuth()) {
                    Log.e(TAG, "认证失败: " + authManager.getErrorMessage());
                    showResultNotification(false, "认证失败: " + authManager.getErrorMessage());
                    return;
                }
                // 执行预约
                String tomorrow = DateUtils.getTomorrowDate();
                SeatReservation.ReserveResult result;
                
                if (autoFindSeat) {
                    // 启用自动寻座
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
                    // 普通预约
                    SeatReservation reservation = new SeatReservation(authManager);
                    result = reservation.reserveSeat(areaName, seatNumber, tomorrow, startTime, endTime);
                    showResultNotification(result.success, result.message);
                }
                
                // 设置下一次定时
                scheduleAutoReserve();
                
            } catch (Exception e) {
                Log.e(TAG, "自动预约出错: " + e.getMessage(), e);
                showResultNotification(false, "自动预约出错: " + e.getMessage());
            } finally {
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                stopForeground(true);
            }
        });
    }
    
    /**
     * 取消定时任务
     */
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
        
        Log.d(TAG, "自动预约已取消");
    }
    
    /**
     * 创建通知渠道
     */
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