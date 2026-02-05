package com.keggin.fucknjfulib.utils;

import android.Manifest;
import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * 系统权限检查工具类
 * 用于检查自动预约功能所需的各项系统权限和设置
 */
public class SystemPermissionChecker {

    /**
     * 检查通知权限是否已授予
     * Android 13 (API 33) 及以上需要运行时权限
     */
    public static boolean isNotificationPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        // Android 13 以下默认有通知权限
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * 检查精确闹钟权限是否已授予
     * Android 12 (API 31) 及以上需要此权限
     */
    public static boolean isExactAlarmPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                return alarmManager.canScheduleExactAlarms();
            }
            return false;
        }
        // Android 12 以下默认有精确闹钟权限
        return true;
    }

    /**
     * 检查电池优化是否已禁用（即是否在白名单中）
     */
    public static boolean isBatteryOptimizationDisabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                return powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
            }
        }
        return true;
    }

    /**
     * 检查自启动权限（部分厂商特有）
     * 注意：此方法仅能检测部分厂商，无法覆盖所有情况
     */
    public static boolean isAutoStartPermissionGranted(Context context) {
        // 由于自启动权限是厂商特有功能，无法通过标准API检测
        // 这里返回 true，让用户自行检查
        // 实际应用中可以根据厂商（小米、华为、OPPO等）进行特定检测
        return true;
    }

    /**
     * 检查所有必需的权限是否都已授予
     */
    public static boolean areAllPermissionsGranted(Context context) {
        return isNotificationPermissionGranted(context)
                && isExactAlarmPermissionGranted(context)
                && isBatteryOptimizationDisabled(context);
    }

    /**
     * 打开通知权限设置页面
     */
    public static void openNotificationSettings(Context context) {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        } else {
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * 打开精确闹钟权限设置页面
     */
    public static void openExactAlarmSettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /**
     * 打开电池优化设置页面
     */
    public static void openBatteryOptimizationSettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /**
     * 打开自启动设置页面（尝试跳转到厂商自启动管理页面）
     */
    public static void openAutoStartSettings(Context context) {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        boolean success = false;
        
        // 小米
        if (manufacturer.contains("xiaomi")) {
            success = tryStartActivity(context, "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity");
        }
        // 华为 - 尝试多个可能的路径
        else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            // 方案1: 启动管理
            success = tryStartActivity(context, "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity");
            
            if (!success) {
                // 方案2: 应用启动管理
                success = tryStartActivity(context, "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity");
            }
            
            if (!success) {
                // 方案3: 受保护应用
                success = tryStartActivity(context, "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity");
            }
            
            if (!success) {
                // 方案4: 手机管家主页
                success = tryStartActivity(context, "com.huawei.systemmanager",
                        "com.huawei.systemmanager.MainActivity");
            }
        }
        // OPPO/Realme
        else if (manufacturer.contains("oppo") || manufacturer.contains("realme")) {
            success = tryStartActivity(context, "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity");
            
            if (!success) {
                success = tryStartActivity(context, "com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity");
            }
        }
        // vivo/iQOO
        else if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            success = tryStartActivity(context, "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity");
            
            if (!success) {
                success = tryStartActivity(context, "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager");
            }
        }
        // 魅族
        else if (manufacturer.contains("meizu")) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.meizu.safe",
                    "com.meizu.safe.security.SHOW_APPSEC"));
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra("packageName", context.getPackageName());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            success = tryStartIntent(context, intent);
        }
        // 三星
        else if (manufacturer.contains("samsung")) {
            success = tryStartActivity(context, "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity");
        }
        // 一加
        else if (manufacturer.contains("oneplus")) {
            success = tryStartActivity(context, "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity");
        }
        // 联想/ZUK
        else if (manufacturer.contains("lenovo") || manufacturer.contains("zuk")) {
            success = tryStartActivity(context, "com.lenovo.security",
                    "com.lenovo.security.purebackground.PureBackgroundActivity");
        }
        
        // 如果所有厂商特定方案都失败，打开应用详情页
        if (!success) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                // 最后的降级方案：打开系统设置
                try {
                    Intent intent = new Intent(Settings.ACTION_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception ex) {
                    // 忽略错误
                }
            }
        }
    }
    
    /**
     * 尝试启动指定的Activity
     */
    private static boolean tryStartActivity(Context context, String packageName, String activityName) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(packageName, activityName));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return tryStartIntent(context, intent);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 尝试启动Intent
     */
    private static boolean tryStartIntent(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}