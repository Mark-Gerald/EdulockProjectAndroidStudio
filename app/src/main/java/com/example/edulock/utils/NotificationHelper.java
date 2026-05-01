package com.example.edulock.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

public class NotificationHelper {
    public static final String CHANNEL_ID = "usage_alert_channel";

    public static void createChannel(Context context) {
        // IMPORTANCE_DEFAULT shows in shade without heads-up popup animation.
        // IMPORTANCE_HIGH was causing every notification to animate on screen,
        // which is expensive and was contributing to system lag.
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Usage Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Alerts when app usage exceeds one hour");
        channel.enableVibration(false); // No vibration for routine usage alerts
        channel.setSound(null, null);   // No sound — these are informational only

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}