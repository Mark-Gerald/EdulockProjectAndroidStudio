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
    private static final int CHECK_INTERVAL_SECONDS = 60; // Check every 60 seconds
    private static final int MIDNIGHT_ALARM_ID = 2024;

    private Handler handler = new Handler();

    // Track which hours we've already notified about
    // Key = package name, Value = hours notified
    private Map<String, Integer> notifiedHours = new HashMap<>();

    // Track what day we're on (to reset notifications at midnight)
    private int lastResetDay = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "UsageMonitorService created");

        NotificationHelper.createChannel(this);

        // Start as FOREGROUND SERVICE - this keeps it running even if app is killed
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());

        // Schedule midnight notification
        scheduleMidnightSummary();

        // Start checking usage
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

                // Schedule next check in 60 seconds
                handler.postDelayed(this, CHECK_INTERVAL_SECONDS * 1000);
            }
        });
    }

    /**
     * CHECK USAGE - Main logic
     *
     * This method:
     * 1. Gets today's app usage using our centralized calculator
     * 2. For each app used >= 1 hour:
     *    - Check if we've already notified for this hour
     *    - If not, send notification and mark hour as notified
     */
    private void checkUsage() {
        Log.d(TAG, "Checking app usage...");

        // Get current day (resets notifications at midnight)
        Calendar cal = Calendar.getInstance();
        int currentDay = cal.get(Calendar.DAY_OF_YEAR);

        if (lastResetDay != currentDay) {
            notifiedHours.clear(); // Clear notification history at new day
            lastResetDay = currentDay;
            Log.d(TAG, "New day detected - resetting notification history");
        }

        // Get all app usage times using our centralized calculator
        Map<String, Long> appUsageMap = UsageTimeCalculator.getAppUsageToday(this);

        // Check each app
        for (Map.Entry<String, Long> entry : appUsageMap.entrySet()) {
            String packageName = entry.getKey();
            long usageMillis = entry.getValue();

            // Convert to hours (integer)
            int hours = (int) (usageMillis / (1000 * 60 * 60));

            // Only notify if >= 1 hour
            if (hours >= 1) {
                // Get last hour we notified about for this app
                int lastNotifiedHour = notifiedHours.getOrDefault(packageName, 0);

                // If they've used MORE hours since last notification, send new one
                if (hours > lastNotifiedHour) {
                    notifiedHours.put(packageName, hours);
                    sendUsageNotification(packageName, hours);
                    Log.d(TAG, packageName + " exceeded " + hours + " hour(s)");
                }
            }
        }
    }

    /**
     * SEND USAGE NOTIFICATION
     *
     * Displays: "You've used [AppName] for X hour(s)"
     */
    private void sendUsageNotification(String packageName, int hours) {
        String appName = getAppName(packageName);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Take a Break!")
                .setContentText("You've used " + appName + " for " + hours + " hour(s)")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true) // Allows user to dismiss
                .setOnlyAlertOnce(true); // Only alert once per notification

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Use package name hash as notification ID so each app gets its own notification
        manager.notify(packageName.hashCode(), builder.build());

        Log.d(TAG, "Sent notification for " + appName);
    }

    /**
     * SCHEDULE MIDNIGHT SUMMARY
     *
     * This sets up an AlarmManager to send a daily summary at midnight
     */
    private void scheduleMidnightSummary() {
        Calendar calendar = Calendar.getInstance();

        // Set to TOMORROW at midnight (00:00)
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, DailySummaryReceiver.class);
        intent.setAction("com.example.edulock.DAILY_SUMMARY"); // Add action to identify this alarm

        // Use SAME request code so it doesn't create duplicates
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                MIDNIGHT_ALARM_ID,  // FIXED: Use constant instead of 0
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            try {
                // Cancel any existing alarm first
                alarmManager.cancel(pendingIntent);

                // Set alarm to trigger at midnight and repeat every 24 hours
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
                Log.d(TAG, "Scheduled daily summary for midnight: " + calendar.getTime());
            } catch (Exception e) {
                Log.e(TAG, "Error scheduling midnight alarm", e);
            }
        }
    }

    /**
     * GET APP NAME
     * Converts package name (e.g., "com.example.game") to user-friendly name
     */
    private String getAppName(String packageName) {
        try {
            return getPackageManager()
                    .getApplicationLabel(
                            getPackageManager().getApplicationInfo(packageName, 0)
                    ).toString();
        } catch (Exception e) {
            return packageName; // Fall back to package name if error
        }
    }

    /**
     * FOREGROUND NOTIFICATION
     * Shows persistent notification that keeps service alive
     */
    private Notification createForegroundNotification() {
        return new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
                .setContentTitle("EduLock Running")
                .setContentText("Monitoring app usage...")
                .setSmallIcon(R.drawable.ic_timer)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true) // Persistent - cannot be swiped away
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");
        return START_STICKY; // Restarts service if killed
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        handler.removeCallbacksAndMessages(null);
    }
}