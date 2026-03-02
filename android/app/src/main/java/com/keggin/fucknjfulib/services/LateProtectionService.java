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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class LateProtectionService extends Service {
    private static final String TAG = "LateProtectionService";
    private static final String CHANNEL_ID = "late_protection_channel";
    private static final int NOTIFICATION_ID = 2001;
    private static final int ALARM_REQUEST_CODE = 2001;
    public static final String ACTION_CHECK = "com.keggin.fucknjfulib.ACTION_LATE_CHECK";
    public static final String ACTION_SCHEDULE = "com.keggin.fucknjfulib.ACTION_LATE_SCHEDULE";
    public static final String ACTION_CANCEL = "com.keggin.fucknjfulib.ACTION_LATE_CANCEL";
    public static final String EXTRA_RESERVATION_UUID = "reservation_uuid";
    public static final String EXTRA_BEGIN_TIME = "begin_time";
    private static final String PREF_KEY_LATE_SCHEDULED_UUIDS = "late_scheduled_uuids";
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
        // 兜底：只要通过 startForegroundService 拉起，先立刻进入前台，避免 5 秒超时崩溃
        startForeground(NOTIFICATION_ID, createServiceNotification("迟到保护服务运行中..."));

        String action = intent != null ? intent.getAction() : null;
        LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "收到动作: " + action);

        if (ACTION_SCHEDULE.equals(action)) {
            scheduleChecksForUpcomingReservations(startId);
        } else if (ACTION_CHECK.equals(action)) {
            String uuid = intent.getStringExtra(EXTRA_RESERVATION_UUID);
            long beginTime = intent.getLongExtra(EXTRA_BEGIN_TIME, 0);
            executeLateCheck(uuid, beginTime, startId);
        } else if (ACTION_CANCEL.equals(action)) {
            cancelScheduledChecks();
            showResultNotification(true, "迟到保护已关闭，已取消所有检查任务");
            stopForeground(true);
            stopSelf(startId);
        } else {
            // 未知/空 action：快速收尾，避免服务悬挂
            LocalLogManager.getInstance(LateProtectionService.this).w(TAG, "未知动作，结束服务: " + action);
            stopForeground(true);
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }
    private void scheduleChecksForUpcomingReservations(int startId) {
        executor.execute(() -> {
            try {
                cancelScheduledChecks();
                AuthManager authManager = AuthManager.getInstance(this);
                SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
                if (!prefs.getBoolean(Constants.PREF_PREVENT_LATE, false)) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "迟到保护未启用");
                    return;
                }
                if (!authManager.isAuthenticated()) {
                    if (!authManager.authenticate()) {
                        LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "认证失败，无法检查预约");
                        showResultNotification(false, "迟到保护任务安排失败：认证失败");
                        return;
                    }
                }
                SeatReservation reservation = new SeatReservation(authManager);
                List<SeatReservation.ReservationInfo> upcomingReservations =
                        reservation.getTodayReservations();
                if (upcomingReservations.isEmpty()) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "今天/明天无可检查预约");
                    showResultNotification(true, "迟到保护已开启，今天/明天无可安排检查的预约");
                    return;
                }
                int scheduledCount = 0;
                for (SeatReservation.ReservationInfo info : upcomingReservations) {
                    if (scheduleCheckForReservation(info)) {
                        scheduledCount++;
                    }
                }
                if (scheduledCount > 0) {
                    showResultNotification(true, "迟到保护已安排 " + scheduledCount + " 个检查任务");
                } else {
                    showResultNotification(true, "迟到保护已开启，但无可用检查任务");
                }
            } catch (Exception e) {
                LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "设置迟到检查任务出错: " + e.getMessage(), e);
                showResultNotification(false, "设置迟到保护任务失败: " + e.getMessage());
            } finally {
                stopForeground(true);
                stopSelf(startId);
            }
        });
    }

    private boolean scheduleCheckForReservation(SeatReservation.ReservationInfo info) {
        if (info == null || info.uuid == null || info.uuid.trim().isEmpty()) {
            LocalLogManager.getInstance(LateProtectionService.this).w(TAG, "预约信息缺少 uuid，跳过迟到检查调度");
            return false;
        }
        if (info.beginTime <= 0) {
            LocalLogManager.getInstance(LateProtectionService.this).w(TAG, "预约 " + info.uuid + " 开始时间无效，跳过迟到检查调度");
            return false;
        }
        String today = DateUtils.getTodayDate();
        String reservationDate = (info.onDate != null && !info.onDate.trim().isEmpty())
                ? info.onDate
                : formatDateFromMillis(info.beginTime);
        if (!today.equals(reservationDate)) {
            LocalLogManager.getInstance(LateProtectionService.this).i(TAG,
                    "跳过非今日预约迟到检查调度: " + info.uuid + " onDate=" + reservationDate);
            return false;
        }

        long checkTime = info.beginTime - Constants.LATE_CHECK_MINUTES_BEFORE * 60 * 1000;
        long now = System.currentTimeMillis();
        if (checkTime <= now) {
            LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "预约 " + info.uuid + " 的检查时间已过");
            return false;
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
        rememberScheduledUuid(info.uuid);
        return true;
    }
    private void executeLateCheck(String uuid, long beginTime, int startId) {
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
                    if (info != null && info.uuid != null && info.uuid.equals(uuid)) {
                        targetResv = info;
                        break;
                    }
                }
                if (targetResv == null) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "预约不存在或已被取消");
                    return;
                }
                String targetDate = (targetResv.onDate != null && !targetResv.onDate.trim().isEmpty())
                        ? targetResv.onDate
                        : formatDateFromMillis(beginTime);
                if (!DateUtils.getTodayDate().equals(targetDate)) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG,
                            "跳过非今日预约迟到保护执行: " + uuid + " onDate=" + targetDate);
                    return;
                }

                String status = targetResv.statusName;
                if (status != null && (status.contains("使用") || status.contains("签到"))) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "用户已签到，无需保护");
                    showResultNotification(true, "您已签到，无需迟到保护");
                    return;
                }
                LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "用户未签到，执行迟到保护...");

                String reservationDate = (targetResv.onDate != null && !targetResv.onDate.trim().isEmpty())
                        ? targetResv.onDate
                        : formatDateFromMillis(beginTime);
                String sourceStartTime = (targetResv.startTime != null && !targetResv.startTime.trim().isEmpty())
                        ? targetResv.startTime
                        : DateUtils.formatTimestampToTime(beginTime);
                String newStartTime = DateUtils.addHours(sourceStartTime, Constants.LATE_PROTECTION_DELAY_HOURS);
                String normalizedStartTime = DateUtils.normalizeTimeFormat(newStartTime);

                String areaName = resolveAreaName(targetResv, prefs);
                int seatNumber = resolveSeatNumber(targetResv, prefs);
                if (areaName == null || areaName.trim().isEmpty() || seatNumber <= 0) {
                    LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "无法解析原预约座位信息，取消重约流程");
                    showResultNotification(false, "迟到保护失败：无法解析原预约座位信息");
                    return;
                }

                String closeTime = DateUtils.getEndTimeWithoutSeconds(reservationDate);
                String sourceEndTime = (targetResv.endTime != null && !targetResv.endTime.trim().isEmpty())
                        ? targetResv.endTime
                        : prefs.getString(Constants.PREF_END_TIME, Constants.DEFAULT_END_TIME);
                if (sourceEndTime == null || sourceEndTime.trim().isEmpty()) {
                    sourceEndTime = closeTime;
                }
                String endTime = clampEndTime(sourceEndTime, closeTime);
                String normalizedEndTime = DateUtils.normalizeTimeFormat(endTime);

                boolean canRebook = DateUtils.isValidDuration(normalizedStartTime, normalizedEndTime, 2);

                SeatReservation.ReserveResult cancelResult = reservation.cancelReservation(uuid);
                if (!cancelResult.success) {
                    LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "取消预约失败: " + cancelResult.message);
                    showResultNotification(false, "迟到保护失败：无法取消原预约");
                    return;
                }

                if (!canRebook) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "剩余时间不足2小时，已取消原预约，不再重约");
                    showResultNotification(true, "迟到保护：已取消原预约，剩余时长不足2小时，不再重约");
                    return;
                }

                LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "已取消原预约，准备重新预约...");
                SeatReservation.ReserveResult reserveResult = reservation.reserveSeat(
                        areaName, seatNumber, reservationDate, normalizedStartTime, endTime);
                if (reserveResult.success) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "迟到保护重新预约成功");
                    showResultNotification(true,
                            "迟到保护成功：已重约 " + areaName + " " + seatNumber + "号，开始时间 " + normalizedStartTime);
                    Calendar cal = DateUtils.parseTimeToCalendar(
                            reservationDate, normalizedStartTime);
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
                            "迟到保护：已取消原预约，但重约失败：" + reserveResult.message);
                }
            } catch (Exception e) {
                LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "迟到检查出错: " + e.getMessage(), e);
                showResultNotification(false, "迟到保护出错: " + e.getMessage());
            } finally {
                if (uuid != null && !uuid.trim().isEmpty()) {
                    removeScheduledUuid(uuid);
                }
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                stopForeground(true);
                stopSelf(startId);
            }
        });
    }
    private String clampEndTime(String endTime, String closeTime) {
        Integer endMinutes = parseTimeToMinutes(endTime);
        Integer closeMinutes = parseTimeToMinutes(closeTime);
        if (endMinutes == null || closeMinutes == null) {
            return endTime;
        }
        return endMinutes > closeMinutes ? closeTime : endTime;
    }

    @Nullable
    private Integer parseTimeToMinutes(String hhmm) {
        if (hhmm == null)
            return null;
        String[] parts = hhmm.trim().split(":");
        if (parts.length < 2)
            return null;
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            return h * 60 + m;
        } catch (Exception e) {
            return null;
        }
    }

    private String formatDateFromMillis(long timestamp) {
        if (timestamp <= 0) {
            return DateUtils.getTodayDate();
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }

    @Nullable
    private String resolveAreaName(SeatReservation.ReservationInfo info, SharedPreferences prefs) {
        if (info != null && info.areaName != null && !info.areaName.trim().isEmpty()) {
            return info.areaName;
        }
        if (info != null && info.devId > 0) {
            String[] areaAndSeat = Constants.getAreaAndSeatNumber(info.devId);
            if (areaAndSeat != null && areaAndSeat.length >= 1 && areaAndSeat[0] != null && !areaAndSeat[0].trim().isEmpty()) {
                return areaAndSeat[0];
            }
        }
        return prefs.getString(Constants.PREF_TARGET_AREA, null);
    }

    private int resolveSeatNumber(SeatReservation.ReservationInfo info, SharedPreferences prefs) {
        if (info != null) {
            Integer fromLabel = tryParsePositiveInt(info.seatLabel);
            if (fromLabel != null) {
                return fromLabel;
            }
            if (info.devId > 0) {
                String[] areaAndSeat = Constants.getAreaAndSeatNumber(info.devId);
                if (areaAndSeat != null && areaAndSeat.length >= 2) {
                    Integer fromDev = tryParsePositiveInt(areaAndSeat[1]);
                    if (fromDev != null) {
                        return fromDev;
                    }
                }
            }
        }
        int fromPrefs = prefs.getInt(Constants.PREF_TARGET_SEAT, 0);
        return fromPrefs > 0 ? fromPrefs : 0;
    }

    @Nullable
    private Integer tryParsePositiveInt(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void cancelScheduledChecks() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        Set<String> uuids = prefs.getStringSet(PREF_KEY_LATE_SCHEDULED_UUIDS, null);
        if (uuids == null || uuids.isEmpty()) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        for (String uuid : new HashSet<>(uuids)) {
            if (uuid == null || uuid.trim().isEmpty()) {
                continue;
            }
            Intent intent = new Intent(this, LateProtectionReceiver.class);
            intent.setAction(ACTION_CHECK);
            int requestCode = ALARM_REQUEST_CODE + uuid.hashCode();
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.cancel(pendingIntent);
        }
        prefs.edit().remove(PREF_KEY_LATE_SCHEDULED_UUIDS).apply();
        LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "已取消所有迟到保护检查任务");
    }

    private void rememberScheduledUuid(String uuid) {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        Set<String> oldSet = prefs.getStringSet(PREF_KEY_LATE_SCHEDULED_UUIDS, null);
        Set<String> newSet = oldSet == null ? new HashSet<>() : new HashSet<>(oldSet);
        newSet.add(uuid);
        prefs.edit().putStringSet(PREF_KEY_LATE_SCHEDULED_UUIDS, newSet).apply();
    }

    private void removeScheduledUuid(String uuid) {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        Set<String> oldSet = prefs.getStringSet(PREF_KEY_LATE_SCHEDULED_UUIDS, null);
        if (oldSet == null || oldSet.isEmpty()) {
            return;
        }
        Set<String> newSet = new HashSet<>(oldSet);
        if (newSet.remove(uuid)) {
            if (newSet.isEmpty()) {
                prefs.edit().remove(PREF_KEY_LATE_SCHEDULED_UUIDS).apply();
            } else {
                prefs.edit().putStringSet(PREF_KEY_LATE_SCHEDULED_UUIDS, newSet).apply();
            }
        }
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
    private Notification createServiceNotification(String contentText) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("迟到保护")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private Notification createCheckingNotification() {
        return createServiceNotification("正在检查签到状态...");
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