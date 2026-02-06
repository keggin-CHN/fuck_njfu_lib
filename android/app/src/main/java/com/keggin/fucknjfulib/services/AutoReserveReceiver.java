package com.keggin.fucknjfulib.services;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
public class AutoReserveReceiver extends BroadcastReceiver {
    private static final String TAG = "AutoReserveReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "收到广播: " + intent.getAction());
        String action = intent.getAction();
        if (AutoReserveService.ACTION_EXECUTE.equals(action)) {
            Intent serviceIntent = new Intent(context, AutoReserveService.class);
            serviceIntent.setAction(AutoReserveService.ACTION_EXECUTE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Intent serviceIntent = new Intent(context, AutoReserveService.class);
            serviceIntent.setAction(AutoReserveService.ACTION_SCHEDULE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}