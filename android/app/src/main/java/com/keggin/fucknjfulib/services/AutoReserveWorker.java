package com.keggin.fucknjfulib.services;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.keggin.fucknjfulib.utils.Constants;
import com.keggin.fucknjfulib.utils.LocalLogManager;
public class AutoReserveWorker extends Worker {
    private static final String TAG = "AutoReserveWorker";
    public static final String WORK_NAME = "auto_reserve_work";
    public AutoReserveWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }
    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        LocalLogManager.getInstance(context).i(TAG, "WorkManager 触发自动预约");
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(Constants.PREF_AUTO_RESERVE, false)) {
            LocalLogManager.getInstance(context).i(TAG, "自动预约未启用，WorkManager 退出");
            return Result.success();
        }
        long lastExecuteTime = prefs.getLong("last_reserve_execute_time", 0);
        long now = System.currentTimeMillis();
        if (now - lastExecuteTime < 30 * 60 * 1000) {
            LocalLogManager.getInstance(context).i(TAG, "30分钟内已执行过自动预约，跳过 WorkManager 触发");
            return Result.success();
        }
        try {
            Intent serviceIntent = new Intent(context, AutoReserveService.class);
            serviceIntent.setAction(AutoReserveService.ACTION_EXECUTE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            LocalLogManager.getInstance(context).i(TAG, "WorkManager 已触发 AutoReserveService");
        } catch (Exception e) {
            LocalLogManager.getInstance(context).e(TAG, "WorkManager 触发服务失败: " + e.getMessage());
            return Result.retry();
        }
        return Result.success();
    }
}
