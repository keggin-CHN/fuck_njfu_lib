package com.keggin.fucknjfulib.services;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
public class LateProtectionReceiver extends BroadcastReceiver {
    private static final String TAG = "LateProtectionReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "收到广播: " + intent.getAction());
        String action = intent.getAction();
        if (LateProtectionService.ACTION_CHECK.equals(action)) {
            Intent serviceIntent = new Intent(context, LateProtectionService.class);
            serviceIntent.setAction(LateProtectionService.ACTION_CHECK);
            serviceIntent.putExtra(LateProtectionService.EXTRA_RESERVATION_UUID,
                    intent.getStringExtra(LateProtectionService.EXTRA_RESERVATION_UUID));
            serviceIntent.putExtra(LateProtectionService.EXTRA_BEGIN_TIME,
                    intent.getLongExtra(LateProtectionService.EXTRA_BEGIN_TIME, 0));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}