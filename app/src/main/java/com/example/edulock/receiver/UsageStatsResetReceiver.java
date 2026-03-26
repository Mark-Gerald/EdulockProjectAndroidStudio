package com.example.edulock.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.example.edulock.service.AppMonitoringService;

public class UsageStatsResetReceiver extends BroadcastReceiver {
    public static final String ACTION_RESET_STATS = "com.example.edulock.RESET_STATS";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            if (action.equals(ACTION_RESET_STATS)) {
                // Reset usage stats
                SharedPreferences preferences = context.getSharedPreferences("app_restrictions", Context.MODE_PRIVATE);
                int timeLimit = preferences.getInt("selected_time_limit", 5);

                // Start the monitoring service
                Intent serviceIntent = new Intent(context, AppMonitoringService.class)
                        .putExtra("time_limit_minutes", timeLimit);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                // Device has rebooted, restart our service
                SharedPreferences preferences = context.getSharedPreferences("app_restrictions", Context.MODE_PRIVATE);
                int timeLimit = preferences.getInt("selected_time_limit", 5);

                // Start the monitoring service after boot
                Intent serviceIntent = new Intent(context, AppMonitoringService.class)
                        .putExtra("time_limit_minutes", timeLimit);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }
}