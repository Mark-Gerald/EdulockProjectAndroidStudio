package com.example.edulock.receiver;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.example.edulock.service.AppMonitoringService;

public class ServiceWatcherReceiver extends BroadcastReceiver {
    private static final String TAG = "ServiceWatcher";
    public static final String ACTION_CHECK_SERVICE = "com.example.edulock.CHECK_SERVICE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_CHECK_SERVICE.equals(intent.getAction())) {
            if (!isServiceRunning(context, AppMonitoringService.class)) {
                Log.d(TAG, "Service not running - restarting it");

                SharedPreferences preferences = context.getSharedPreferences("app_restrictions", Context.MODE_PRIVATE);
                int timeLimit = preferences.getInt("selected_time_limit", 5);

                Intent serviceIntent = new Intent(context, AppMonitoringService.class)
                        .putExtra("time_limit_minutes", timeLimit);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
            scheduleServiceCheck(context);
        }
    }

    public static void scheduleServiceCheck(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ServiceWatcherReceiver.class);
        intent.setAction(ACTION_CHECK_SERVICE);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 123, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Check every 15 minutes
        long triggerTime = System.currentTimeMillis() + (15 * 60 * 1000);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
    }

    private boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
