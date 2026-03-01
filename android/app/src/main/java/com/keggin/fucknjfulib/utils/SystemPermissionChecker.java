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
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        boolean isHuaweiFamily = manufacturer.contains("huawei") || manufacturer.contains("honor");
        if (isHuaweiFamily) {
            // HarmonyOS/EMUI 下“后台运行/自启动”常无法通过标准 API 精准判断，改为手动检查策略
            return isNotificationPermissionGranted(context)
                    && isExactAlarmPermissionGranted(context);
        }
        return isNotificationPermissionGranted(context)
                && isExactAlarmPermissionGranted(context)
                && isBatteryOptimizationDisabled(context);
    }
    public static void openNotificationSettings(Context context) {
        try {
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
        } catch (Exception ignored) {
            openAppDetailsSettings(context);
        }
    }

    public static void openExactAlarmSettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
        }
        openAppDetailsSettings(context);
    }

    public static void openBatteryOptimizationSettings(Context context) {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        boolean isHuaweiFamily = manufacturer.contains("huawei") || manufacturer.contains("honor");

        // HarmonyOS/EMUI：避免走会弹“没有应用可执行此操作”的隐式 Intent，优先显式组件
        if (isHuaweiFamily) {
            if (tryStartActivityWithPackageExtra(context, "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity")) {
                return;
            }
            if (tryStartActivityWithPackageExtra(context, "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")) {
                return;
            }
            if (tryStartActivityWithPackageExtra(context, "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupAwakedAppListActivity")) {
                return;
            }
            if (tryStartActivityWithPackageExtra(context, "com.huawei.systemmanager",
                    "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")) {
                return;
            }
            if (tryStartActivityWithPackageExtra(context, "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.bootstart.BootStartActivity")) {
                return;
            }
            if (tryStartActivity(context, "com.huawei.systemmanager",
                    "com.huawei.systemmanager.power.ui.HwPowerManagerActivity")) {
                return;
            }
            if (tryStartActivity(context, "com.huawei.systemmanager",
                    "com.huawei.systemmanager.MainActivity")) {
                return;
            }

            // 补充华为 Action 入口（有解析器才会执行，不会再弹“没有应用可执行此操作”）
            if (tryStartActionIntent(context, "huawei.intent.action.HSM_BOOTAPP_MANAGER",
                    "com.huawei.systemmanager", true)) {
                return;
            }
            if (tryStartActionIntent(context, "huawei.intent.action.HSM_PROTECTED_APPS",
                    "com.huawei.systemmanager", true)) {
                return;
            }

            // 兜底1：拉起系统管家首页
            if (tryLaunchPackage(context, "com.huawei.systemmanager")) {
                return;
            }

            // 兜底2：应用详情页（可进入电池/启动管理相关项）
            openAppDetailsSettings(context);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (tryStartIntent(context, intent)) {
                    return;
                }
            } catch (Exception ignored) {
            }

            try {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (tryStartIntent(context, intent)) {
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        try {
            Intent intent = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (tryStartIntent(context, intent)) {
                return;
            }
        } catch (Exception ignored) {
        }

        openAppDetailsSettings(context);
    }

    public static void openAutoStartSettings(Context context) {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        boolean success = false;

        if (manufacturer.contains("xiaomi")) {
            success = tryStartActivity(context, "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity");
            if (!success) {
                success = tryStartActivity(context, "com.miui.securitycenter",
                        "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity");
            }
        } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            success = tryStartActivityWithPackageExtra(context, "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity");
            if (!success) {
                success = tryStartActivityWithPackageExtra(context, "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupAwakedAppListActivity");
            }
            if (!success) {
                success = tryStartActivityWithPackageExtra(context, "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity");
            }
            if (!success) {
                success = tryStartActivityWithPackageExtra(context, "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.bootstart.BootStartActivity");
            }
            if (!success) {
                success = tryStartActionIntent(context, "huawei.intent.action.HSM_BOOTAPP_MANAGER",
                        "com.huawei.systemmanager", true);
            }
            if (!success) {
                success = tryStartActionIntent(context, "huawei.intent.action.HSM_PROTECTED_APPS",
                        "com.huawei.systemmanager", true);
            }
            if (!success) {
                success = tryStartActivityWithPackageExtra(context, "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity");
            }
            if (!success) {
                success = tryStartActivity(context, "com.huawei.systemmanager",
                        "com.huawei.systemmanager.power.ui.HwPowerManagerActivity");
            }
            if (!success) {
                success = tryStartActivity(context, "com.huawei.systemmanager",
                        "com.huawei.systemmanager.MainActivity");
            }
        } else if (manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus")) {
            success = tryStartActivity(context, "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity");
            if (!success) {
                success = tryStartActivity(context, "com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity");
            }
            if (!success) {
                success = tryStartActivity(context, "com.oplus.safecenter",
                        "com.oplus.safecenter.startupapp.StartupAppListActivity");
            }
            if (!success) {
                success = tryStartActivity(context, "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity");
            }
        } else if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            success = tryStartActivity(context, "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity");
            if (!success) {
                success = tryStartActivity(context, "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager");
            }
        } else if (manufacturer.contains("meizu")) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.meizu.safe",
                    "com.meizu.safe.security.SHOW_APPSEC"));
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra("packageName", context.getPackageName());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            success = tryStartIntent(context, intent);
        } else if (manufacturer.contains("samsung")) {
            success = tryStartActivity(context, "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity");
            if (!success) {
                success = tryStartActivity(context, "com.samsung.android.sm_cn",
                        "com.samsung.android.sm.ui.ram.AutoRunActivity");
            }
        } else if (manufacturer.contains("lenovo") || manufacturer.contains("zuk")) {
            success = tryStartActivity(context, "com.lenovo.security",
                    "com.lenovo.security.purebackground.PureBackgroundActivity");
        }

        if (!success) {
            if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
                success = tryLaunchPackage(context, "com.huawei.systemmanager");
            }
        }
        if (!success) {
            openAppDetailsSettings(context);
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
            // 对隐式 Intent 先做可处理性判断，避免弹“没有应用可执行此操作”
            if (intent.getComponent() == null) {
                PackageManager pm = context.getPackageManager();
                if (intent.resolveActivity(pm) == null) {
                    return false;
                }
            }
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean tryStartActivityWithPackageExtra(Context context, String packageName, String activityName) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(packageName, activityName));
            intent.putExtra("packageName", context.getPackageName());
            intent.putExtra("package_name", context.getPackageName());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return tryStartIntent(context, intent);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean tryStartActionIntent(Context context, String action, String pkg, boolean includePackageExtra) {
        try {
            Intent intent = new Intent(action);
            if (pkg != null && !pkg.isEmpty()) {
                intent.setPackage(pkg);
            }
            if (includePackageExtra) {
                intent.putExtra("packageName", context.getPackageName());
                intent.putExtra("package_name", context.getPackageName());
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return tryStartIntent(context, intent);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean tryLaunchPackage(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
            if (launchIntent == null) {
                return false;
            }
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void openAppDetailsSettings(Context context) {
        try {
            Intent appDetails = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            appDetails.setData(Uri.parse("package:" + context.getPackageName()));
            appDetails.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(appDetails);
        } catch (Exception e) {
            try {
                Intent systemSettings = new Intent(Settings.ACTION_SETTINGS);
                systemSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(systemSettings);
            } catch (Exception ignored) {
            }
        }
    }
}