package com.example.edulock.receiver;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.example.edulock.R;
import com.example.edulock.utils.NotificationHelper;

import java.util.Calendar;
import java.util.*;

public class DailySummaryReceiver extends BroadcastReceiver {

    public void onReceive(Context context, Intent intent) {

        UsageStatsManager usm = (UsageStatsManager)
                context.getSystemService(Context.USAGE_STATS_SERVICE);

        long end = System.currentTimeMillis();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);

        long start = cal.getTimeInMillis();

        List<UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, start, end
        );

        long total = 0;
        for (UsageStats stat : stats) {
            total += stat.getTotalTimeInForeground();
        }

        long hours = total / (1000 * 60 * 60);
        long minutes = (total / (1000 * 60)) % 60;

        Notification notification = new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stats)
                .setContentTitle("Daily Screen Time")
                .setContentText("You used your phone for " + hours + "h " + minutes + "m today")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(9999, notification);
    }
}
