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
        if (wakeLock != null) {
            wakeLock.acquire(3 * 60 * 1000L);
        }
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
                    if (!authManager.authenticate(null)) {
                        LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "认证失败，无法检查预约");
                        showResultNotification(false, "迟到保护任务安排失败：认证失败");
                        return;
                    }
                }
                SeatReservation reservation = new SeatReservation(authManager);
                List<SeatReservation.ReservationInfo> upcomingReservations =
                        reservation.getTodayReservations();
                if (upcomingReservations.isEmpty()) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "今天无可检查预约");
                    showResultNotification(true, "迟到保护已开启，今天无可安排检查的预约");
                    return;
                }
                int scheduledCount = 0;
                StringBuilder detailBuilder = new StringBuilder();
                int detailShown = 0;
                for (SeatReservation.ReservationInfo info : upcomingReservations) {
                    if (scheduleCheckForReservation(info)) {
                        scheduledCount++;
                        if (detailShown < 3) {
                            if (detailBuilder.length() > 0) {
                                detailBuilder.append("\n\n");
                            }
                            detailBuilder.append(buildScheduleDetail(info));
                            detailShown++;
                        }
                    }
                }
                if (scheduledCount > 0) {
                    if (scheduledCount > detailShown) {
                        if (detailBuilder.length() > 0) {
                            detailBuilder.append("\n\n");
                        }
                        detailBuilder.append("其余 ").append(scheduledCount - detailShown).append(" 个任务已安排");
                    }
                    showResultNotification(true, "迟到保护已安排 " + scheduledCount + " 个检查任务\n" + detailBuilder);
                } else {
                    showResultNotification(true, "迟到保护已开启，但无可用检查任务");
                }
            } catch (Exception e) {
                LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "设置迟到检查任务出错: " + e.getMessage(), e);
                showResultNotification(false, "设置迟到保护任务失败: " + e.getMessage());
            } finally {
                if (wakeLock != null && wakeLock.isHeld()) {
                    try { wakeLock.release(); } catch (Exception ignored) {}
                }
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

        long checkTime = info.beginTime - Constants.LATE_CHECK_MINUTES_BEFORE * 60L * 1000L;
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
        if (wakeLock != null) {
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
                    if (!authManager.authenticate(null)) {
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

                String reservationDate = (targetResv.onDate != null && !targetResv.onDate.trim().isEmpty())
                        ? targetResv.onDate
                        : formatDateFromMillis(beginTime);
                String sourceStartTime = (targetResv.startTime != null && !targetResv.startTime.trim().isEmpty())
                        ? targetResv.startTime
                        : DateUtils.formatTimestampToTime(beginTime);
                String sourceEndTime = (targetResv.endTime != null && !targetResv.endTime.trim().isEmpty())
                        ? targetResv.endTime
                        : null;
                String sourceEndDisplay = (sourceEndTime != null && !sourceEndTime.trim().isEmpty())
                        ? sourceEndTime
                        : "未知结束";
                String originalSeatDesc = (targetResv.seatName != null && !targetResv.seatName.trim().isEmpty())
                        ? targetResv.seatName
                        : "未知座位";
                String originalDetail = "原预约：" + originalSeatDesc
                        + "\n日期：" + reservationDate + "  时段：" + sourceStartTime + " - " + sourceEndDisplay;

                String status = targetResv.statusName;
                if (status != null && (status.contains("使用") || status.contains("签到"))) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "用户已签到，无需保护");
                    showResultNotification(true, "您已签到，无需迟到保护\n" + originalDetail);
                    return;
                }
                LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "用户未签到，执行迟到保护...");

                String newStartTime = DateUtils.addHours(sourceStartTime, Constants.LATE_PROTECTION_DELAY_HOURS);
                String normalizedStartTime = DateUtils.normalizeTimeFormat(newStartTime);

                String areaName = resolveAreaName(targetResv, prefs);
                int seatNumber = resolveSeatNumber(targetResv, prefs);
                if (areaName == null || areaName.trim().isEmpty() || seatNumber <= 0) {
                    LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "无法解析原预约座位信息，取消重约流程");
                    showResultNotification(false, "迟到保护失败：无法解析原预约座位信息\n" + originalDetail);
                    return;
                }
                if ("未知座位".equals(originalSeatDesc)) {
                    originalSeatDesc = areaName + " " + seatNumber + "号";
                }

                String closeTime = DateUtils.getEndTimeWithoutSeconds(reservationDate);
                if (sourceEndTime == null || sourceEndTime.trim().isEmpty()) {
                    sourceEndTime = prefs.getString(Constants.PREF_END_TIME, Constants.DEFAULT_END_TIME);
                }
                if (sourceEndTime == null || sourceEndTime.trim().isEmpty()) {
                    sourceEndTime = closeTime;
                }
                String endTime = clampEndTime(sourceEndTime, closeTime);
                String normalizedEndTime = DateUtils.normalizeTimeFormat(endTime);
                originalDetail = "原预约：" + originalSeatDesc
                        + "\n日期：" + reservationDate + "  时段：" + sourceStartTime + " - " + sourceEndTime;

                boolean canRebook = DateUtils.isValidDuration(normalizedStartTime, normalizedEndTime, 2);

                SeatReservation.ReserveResult cancelResult = reservation.cancelReservation(uuid);
                if (!cancelResult.success) {
                    LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "取消预约失败: " + cancelResult.message);
                    showResultNotification(false, "迟到保护失败：无法取消原预约\n" + originalDetail);
                    return;
                }

                if (!canRebook) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "剩余时间不足2小时，已取消原预约，不再重约");
                    showResultNotification(true,
                            "迟到保护已执行：已取消原预约，不再重约（剩余时长不足2小时）\n" + originalDetail);
                    return;
                }

                LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "已取消原预约，准备重新预约...");
                SeatReservation.ReserveResult reserveResult = reservation.reserveSeat(
                        areaName, seatNumber, reservationDate, normalizedStartTime, endTime);
                if (reserveResult.success) {
                    LocalLogManager.getInstance(LateProtectionService.this).i(TAG, "迟到保护重新预约成功");
                    String newDetail = "新预约：" + areaName + " " + seatNumber + "号"
                            + "\n日期：" + reservationDate + "  时段：" + normalizedStartTime + " - " + normalizedEndTime;
                    showResultNotification(true,
                            "迟到保护成功：已完成改约\n" + originalDetail + "\n" + newDetail);
                    Calendar cal = DateUtils.parseTimeToCalendar(
                            reservationDate, normalizedStartTime);
                    if (cal != null) {
                        SeatReservation.ReservationInfo newInfo = new SeatReservation.ReservationInfo();
                        newInfo.uuid = reserveResult.uuid;
                        newInfo.beginTime = cal.getTimeInMillis();
                        newInfo.seatName = areaName + " " + seatNumber + "号";
                        newInfo.onDate = reservationDate;
                        newInfo.startTime = normalizedStartTime;
                        newInfo.endTime = normalizedEndTime;
                        scheduleCheckForReservation(newInfo);
                    }
                } else {
                    LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "迟到保护重新预约失败: " + reserveResult.message);
                    showResultNotification(false,
                            "迟到保护：已取消原预约，但重约失败\n"
                                    + originalDetail
                                    + "\n目标改约：" + areaName + " " + seatNumber + "号 "
                                    + normalizedStartTime + " - " + normalizedEndTime
                                    + "\n原因：" + reserveResult.message);
                }
            } catch (Exception e) {
                LocalLogManager.getInstance(LateProtectionService.this).e(TAG, "迟到检查出错: " + e.getMessage(), e);
                showResultNotification(false, "迟到保护出错: " + e.getMessage());
            } finally {
                if (uuid != null && !uuid.trim().isEmpty()) {
                    removeScheduledUuid(uuid);
                }
                if (wakeLock != null && wakeLock.isHeld()) {
                    try { wakeLock.release(); } catch (Exception ignored) {}
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

    private String buildScheduleDetail(SeatReservation.ReservationInfo info) {
        if (info == null) {
            return "• 未知预约";
        }
        String reservationDate = (info.onDate != null && !info.onDate.trim().isEmpty())
                ? info.onDate
                : formatDateFromMillis(info.beginTime);
        String startTime = (info.startTime != null && !info.startTime.trim().isEmpty())
                ? info.startTime
                : DateUtils.formatTimestampToTime(info.beginTime);
        String endTime = (info.endTime != null && !info.endTime.trim().isEmpty())
                ? info.endTime
                : "未知结束";
        String seatDesc = (info.seatName != null && !info.seatName.trim().isEmpty())
                ? info.seatName
                : "未知座位";
        long checkTime = info.beginTime - Constants.LATE_CHECK_MINUTES_BEFORE * 60L * 1000L;
        String checkTimeText = DateUtils.formatTimestamp(checkTime);
        return "• " + seatDesc
                + "\n日期：" + reservationDate + "  时段：" + startTime + " - " + endTime
                + "\n检查时间：" + checkTimeText;
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
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
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
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
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