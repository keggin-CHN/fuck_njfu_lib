package com.keggin.fucknjfulib.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.keggin.fucknjfulib.R;
import com.keggin.fucknjfulib.utils.Constants;
import com.keggin.fucknjfulib.utils.DateUtils;
import com.keggin.fucknjfulib.utils.LocalLogManager;
import java.util.Calendar;

/**
 * 常驻前台服务 — 防止后台被系统杀死。
 * 显示下次预约时间，内置定时检测作为第三层保障。
 */
public class KeepAliveService extends Service {
    private static final String TAG = "KeepAliveService";
    private static final String CHANNEL_ID = "keep_alive_channel";
    private static final int NOTIFICATION_ID = 3001;
    private static final long CHECK_INTERVAL_MS = 15 * 60 * 1000; // 15分钟检测一次

    public static final String ACTION_START = "com.keggin.fucknjfulib.KEEP_ALIVE_START";
    public static final String ACTION_STOP = "com.keggin.fucknjfulib.KEEP_ALIVE_STOP";
    public static final String ACTION_UPDATE = "com.keggin.fucknjfulib.KEEP_ALIVE_UPDATE";

    private Handler handler;
    private PowerManager.WakeLock wakeLock;
    private boolean isRunning = false;

    private final Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            checkAndTriggerIfNeeded();
            updateNotification();
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        handler = new Handler(Looper.getMainLooper());
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fucknjfulib:KeepAlive");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            isRunning = false;
            handler.removeCallbacks(checkRunnable);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_UPDATE.equals(action)) {
            updateNotification();
            return START_STICKY;
        }

        // ACTION_START or default
        if (!isRunning) {
            isRunning = true;
            startForeground(NOTIFICATION_ID, buildNotification());
            handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS);
            LocalLogManager.getInstance(this).i(TAG, "保活服务已启动");
        } else {
            updateNotification();
        }

        return START_STICKY;
    }

    private void checkAndTriggerIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(Constants.PREF_AUTO_RESERVE, false)) {
            return;
        }

        long nextReserveTime = prefs.getLong("next_reserve_time", 0);
        long now = System.currentTimeMillis();
        long lastExecuteTime = prefs.getLong("last_reserve_execute_time", 0);

        // 到达执行时间且30分钟内未执行过
        if (nextReserveTime > 0 && now >= nextReserveTime && (now - lastExecuteTime > 30 * 60 * 1000)) {
            LocalLogManager.getInstance(this).i(TAG, "保活服务检测到执行时间已到，触发自动预约");
            try {
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire(5 * 60 * 1000L);
                }
                Intent serviceIntent = new Intent(this, AutoReserveService.class);
                serviceIntent.setAction(AutoReserveService.ACTION_EXECUTE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            } catch (Exception e) {
                LocalLogManager.getInstance(this).e(TAG, "触发自动预约失败: " + e.getMessage());
            } finally {
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
            }
        }
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        boolean autoReserve = prefs.getBoolean(Constants.PREF_AUTO_RESERVE, false);
        boolean preventLate = prefs.getBoolean(Constants.PREF_PREVENT_LATE, false);

        StringBuilder text = new StringBuilder();
        if (autoReserve) {
            long nextTime = prefs.getLong("next_reserve_time", 0);
            if (nextTime > 0) {
                text.append("下次预约: ").append(DateUtils.formatTimestamp(nextTime));
            } else {
                text.append("自动预约已开启");
            }
        }
        if (preventLate) {
            if (text.length() > 0) text.append(" | ");
            text.append("迟到保护已开启");
        }
        if (text.length() == 0) {
            text.append("后台服务运行中");
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("图书馆助手")
                .setContentText(text.toString())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "后台保活服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持应用后台运行，确保定时任务可靠触发");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
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
        isRunning = false;
        handler.removeCallbacks(checkRunnable);
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        // 被系统杀死后尝试重启
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(Constants.PREF_AUTO_RESERVE, false) ||
                prefs.getBoolean(Constants.PREF_PREVENT_LATE, false)) {
            LocalLogManager.getInstance(this).w(TAG, "保活服务被销毁，尝试重启...");
            try {
                Intent restartIntent = new Intent(this, KeepAliveService.class);
                restartIntent.setAction(ACTION_START);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(restartIntent);
                } else {
                    startService(restartIntent);
                }
            } catch (Exception e) {
                LocalLogManager.getInstance(this).e(TAG, "保活服务重启失败: " + e.getMessage());
            }
        }
    }
}
