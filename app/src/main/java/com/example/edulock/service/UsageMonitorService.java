package com.example.edulock.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.edulock.R;
import com.example.edulock.receiver.DailySummaryReceiver;
import com.example.edulock.utils.NotificationHelper;
import com.example.edulock.utils.UsageTimeCalculator;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class UsageMonitorService extends Service {
    private static final String TAG = "UsageMonitorService";
    private static final int FOREGROUND_NOTIFICATION_ID = 1001;
    private static final int MIDNIGHT_ALARM_ID = 2024;

    // Raised from 60s to 300s (5 minutes).
    // The one-hour milestone doesn't need per-minute precision —
    // checking every 5 min is accurate enough and reduces work by 5x.
    private static final int CHECK_INTERVAL_MS = 5 * 60 * 1000;

    private final Handler handler = new Handler();

    // Key = package name, Value = highest hour milestone already notified.
    // e.g. if value is 2, we've sent the "2 hours" notification and won't
    // send it again until the day resets.
    private final Map<String, Integer> notifiedHours = new HashMap<>();

    private int lastResetDay = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "UsageMonitorService created");

        NotificationHelper.createChannel(this);
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
        scheduleMidnightSummary();
        startMonitoring();
    }

    private void startMonitoring() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    checkUsage();
                } catch (Exception e) {
                    Log.e(TAG, "Error checking usage", e);
                }
                handler.postDelayed(this, CHECK_INTERVAL_MS);
            }
        });
    }

    /**
     * Core check: for each app used today, fire a notification only when
     * the user crosses a new whole-hour milestone they haven't been told
     * about yet today.
     *
     * Example timeline for YouTube:
     *   - 58 min  → no notification (< 1 hour)
     *   - 62 min  → notify "1 hour", store notifiedHours["youtube"] = 1
     *   - 90 min  → already notified hour 1, skip
     *   - 122 min → notify "2 hours", store notifiedHours["youtube"] = 2
     */
    private void checkUsage() {
        Log.d(TAG, "Checking app usage...");

        // Reset notification history at the start of each new day
        int currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        if (lastResetDay != currentDay) {
            notifiedHours.clear();
            lastResetDay = currentDay;
            Log.d(TAG, "New day — notification history cleared");
        }

        Map<String, Long> appUsageMap = UsageTimeCalculator.getAppUsageToday(this);

        for (Map.Entry<String, Long> entry : appUsageMap.entrySet()) {
            String packageName = entry.getKey();
            long usageMillis   = entry.getValue();

            // How many complete hours has the user spent in this app today?
            int hoursCompleted = (int) (usageMillis / (1000L * 60 * 60));

            // Only act at the 1-hour milestone and above
            if (hoursCompleted < 1) continue;

            int lastNotified = notifiedHours.getOrDefault(packageName, 0);

            // Only notify if we've crossed a NEW hour milestone since last check
            if (hoursCompleted > lastNotified) {
                notifiedHours.put(packageName, hoursCompleted);
                sendUsageNotification(packageName, hoursCompleted);
                Log.d(TAG, packageName + " reached " + hoursCompleted + " hour(s) — notifying");
            }
            // Otherwise: same milestone as before, do nothing
        }
    }

    /**
     * Sends the one-hour usage warning notification.
     * Uses the package hash as notification ID so each app has its own
     * persistent notification that gets replaced (not stacked) when updated.
     */
    private void sendUsageNotification(String packageName, int hours) {
        String appName = getAppName(packageName);
        int notifId    = packageName.hashCode(); // Stable ID per app

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Usage Reminder")
                .setContentText("You've used " + appName + " for " + hours
                        + (hours == 1 ? " hour" : " hours") + " today")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager != null) {
            // cancel() + notify() ensures the updated count replaces the old
            // notification rather than stacking a second one
            manager.cancel(notifId);
            manager.notify(notifId, builder.build());
        }

        Log.d(TAG, "Sent usage notification for " + appName + " (" + hours + "h)");
    }

    private void scheduleMidnightSummary() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, DailySummaryReceiver.class);
        intent.setAction("com.example.edulock.DAILY_SUMMARY");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                MIDNIGHT_ALARM_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            try {
                alarmManager.cancel(pendingIntent); // Prevent duplicate alarms
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
                Log.d(TAG, "Midnight summary scheduled for: " + calendar.getTime());
            } catch (Exception e) {
                Log.e(TAG, "Error scheduling midnight alarm", e);
            }
        }
    }

    private String getAppName(String packageName) {
        try {
            return getPackageManager()
                    .getApplicationLabel(
                            getPackageManager().getApplicationInfo(packageName, 0))
                    .toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    private Notification createForegroundNotification() {
        return new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
                .setContentTitle("EduLock Running")
                .setContentText("Monitoring app usage...")
                .setSmallIcon(R.drawable.ic_timer)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "UsageMonitorService destroyed");
    }
}