package com.example.edulock.receiver;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.edulock.R;
import com.example.edulock.utils.NotificationHelper;
import com.example.edulock.utils.UsageTimeCalculator;

/**
 * DAILY SUMMARY RECEIVER
 *
 * Triggered by AlarmManager at midnight (00:00)
 * Sends notification showing YESTERDAY's total screen time (not today's which is 0h)
 */
public class DailySummaryReceiver extends BroadcastReceiver {
    private static final String TAG = "DailySummaryReceiver";
    private static final int SUMMARY_NOTIFICATION_ID = 9999;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Daily summary triggered at midnight");

        try {
            // 🔥 KEY FIX: Get PREVIOUS day's data, not today's!
            // (Today is 0h 0m because it just started)
            long totalMillis = UsageTimeCalculator.getTotalScreenTimePreviousDay(context);

            // Convert to hours and minutes
            String formattedTime = UsageTimeCalculator.formatTime(totalMillis);

            Log.d(TAG, "Total screen time YESTERDAY: " + formattedTime);

            // Create and send notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stats)
                    .setContentTitle("Daily Screen Time Summary")
                    .setContentText("Total Screen time of the day: " + formattedTime)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true); // User can dismiss

            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            manager.notify(SUMMARY_NOTIFICATION_ID, builder.build());

        } catch (Exception e) {
            Log.e(TAG, "Error sending daily summary", e);
        }
    }
}