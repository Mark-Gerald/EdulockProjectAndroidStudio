package com.example.edulock.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.example.edulock.R;
import com.example.edulock.utils.NotificationHelper;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsageMonitorService extends Service {
     final Handler handler = new Handler();
     final private Map<String, Integer> notifiedHours = new HashMap<>();

    @Override
    public void onCreate() {

        sendNotification("TEST_APP", 1);

        Notification notification = new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
                .setContentTitle("EduLock Running")
                .setContentText("Monitoring app usage...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();

        startForeground(1, notification);

        super.onCreate();
        android.util.Log.d("UsageService", "Service started");
        NotificationHelper.createChannel(this);
        startMonitoring();
    }

    private void startMonitoring() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkUsage();
                handler.postDelayed(this, 60000);
            }
        }, 60000);
    }

    private void checkUsage() {
        android.util.Log.d("UsageCheck", "Checking usage...");

        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);

        long end = System.currentTimeMillis();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);

        long start = cal.getTimeInMillis();

        List<UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, start, end
        );

        for (UsageStats usage : stats) {
            long time = usage.getTotalTimeInForeground();

            int hours = (int) (time / (1000 * 60 * 60));

            if (hours >= 1) {
                String pkg = usage.getPackageName();

                int lastNotified = notifiedHours.getOrDefault(pkg, 0);

                if (hours > lastNotified) {
                    notifiedHours.put(pkg, hours);
                    sendNotification(pkg, hours);
                }
            }
            android.util.Log.d("UsageCheck", usage.getPackageName() + " time: " + usage.getTotalTimeInForeground());
        }
    }

    private String getAppName(String packageName) {
        PackageManager pm = getPackageManager();
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private void sendNotification(String packageName, int hours) {
        String appName = getAppName(packageName);

        Notification notification = new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Take a Break!")
                .setContentText("You've used " + appName + " for " + hours + " hour(s)")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(packageName.hashCode(), notification);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
