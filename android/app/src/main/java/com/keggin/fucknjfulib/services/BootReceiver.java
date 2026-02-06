package com.keggin.fucknjfulib.services;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import com.keggin.fucknjfulib.utils.Constants;
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "设备启动完成，检查是否需要恢复定时任务");
            SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
            boolean autoReserve = prefs.getBoolean(Constants.PREF_AUTO_RESERVE, false);
            if (autoReserve) {
                Log.d(TAG, "恢复自动预约定时任务");
                Intent serviceIntent = new Intent(context, AutoReserveService.class);
                serviceIntent.setAction(AutoReserveService.ACTION_SCHEDULE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
            boolean preventLate = prefs.getBoolean(Constants.PREF_PREVENT_LATE, false);
            if (preventLate) {
                Log.d(TAG, "恢复迟到保护检查任务");
                Intent serviceIntent = new Intent(context, LateProtectionService.class);
                serviceIntent.setAction(LateProtectionService.ACTION_SCHEDULE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }
}