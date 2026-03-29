package com.example.edulock.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

public class NotificationHelper {
    public static final String CHANNEL_ID = "usage_alert_channel";

    public static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Usage Alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Alerts when app usage exceeds limits");

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }
}
