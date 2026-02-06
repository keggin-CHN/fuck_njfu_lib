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
public class SystemPermissionChecker {
    public static boolean isNotificationPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }
    public static boolean isExactAlarmPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                return alarmManager.canScheduleExactAlarms();
            }
            return false;
        }
        return true;
    }
    public static boolean isBatteryOptimizationDisabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                return powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
            }
        }
        return true;
    }
    public static boolean isAutoStartPermissionGranted(Context context) {
        return true;
    }
    public static boolean areAllPermissionsGranted(Context context) {
        return isNotificationPermissionGranted(context)
                && isExactAlarmPermissionGranted(context)
                && isBatteryOptimizationDisabled(context);
    }
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
    public static void openExactAlarmSettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
    public static void openBatteryOptimizationSettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
    public static void openAutoStartSettings(Context context) {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        boolean success = false;
        if (manufacturer.contains("xiaomi")) {
            success = tryStartActivity(context, "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity");
        }
        else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            success = tryStartActivity(context, "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity");
            if (!success) {
                success = tryStartActivity(context, "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity");
            }
            if (!success) {
                success = tryStartActivity(context, "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity");
            }
            if (!success) {
                success = tryStartActivity(context, "com.huawei.systemmanager",
                        "com.huawei.systemmanager.MainActivity");
            }
        }
        else if (manufacturer.contains("oppo") || manufacturer.contains("realme")) {
            success = tryStartActivity(context, "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity");
            if (!success) {
                success = tryStartActivity(context, "com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity");
            }
        }
        else if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            success = tryStartActivity(context, "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity");
            if (!success) {
                success = tryStartActivity(context, "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager");
            }
        }
        else if (manufacturer.contains("meizu")) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.meizu.safe",
                    "com.meizu.safe.security.SHOW_APPSEC"));
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra("packageName", context.getPackageName());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            success = tryStartIntent(context, intent);
        }
        else if (manufacturer.contains("samsung")) {
            success = tryStartActivity(context, "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity");
        }
        else if (manufacturer.contains("oneplus")) {
            success = tryStartActivity(context, "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity");
        }
        else if (manufacturer.contains("lenovo") || manufacturer.contains("zuk")) {
            success = tryStartActivity(context, "com.lenovo.security",
                    "com.lenovo.security.purebackground.PureBackgroundActivity");
        }
        if (!success) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception ex) {
                }
            }
        }
    }
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
    private static boolean tryStartIntent(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}