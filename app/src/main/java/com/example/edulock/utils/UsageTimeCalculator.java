package com.example.edulock.utils;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class UsageTimeCalculator {
    public static Map<String, Long> getAppUsageToday(Context context) {
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        UsageEvents events = usm.queryEvents(startTime, endTime);
        return processEvents(events);
    }

    public static long getTotalScreenTimeToday(Context context) {
        Map<String, Long> appUsage = getAppUsageToday(context);
        long total = 0;
        for (Long time : appUsage.values()) {
            total += time;
        }
        return total;
    }

    public static long getAppUsageTime(Context context, String packageName) {
        Map<String, Long> appUsage = getAppUsageToday(context);
        return appUsage.getOrDefault(packageName, 0L);
    }

    private static Map<String, Long> processEvents(UsageEvents events) {
        Map<String, Long> appUsageMap = new HashMap<>();
        Map<String, Long> appStartTime = new HashMap<>();

        UsageEvents.Event event = new UsageEvents.Event();

        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            String packageName = event.getPackageName();

            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                appStartTime.put(packageName, event.getTimeStamp());
            } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED) {
                Long start = appStartTime.get(packageName);
                if (start != null) {
                    long duration = event.getTimeStamp() - start;
                    appUsageMap.put(packageName, appUsageMap.getOrDefault(packageName, 0L) + duration);
                    appStartTime.remove(packageName);
                }
            }
        }

        long now = System.currentTimeMillis();
        for (String pkg : new HashMap<>(appStartTime).keySet()) {
            Long start = appStartTime.get(pkg);
            if (start != null) {
                long duration = now - start;
                appUsageMap.put(pkg, appUsageMap.getOrDefault(pkg, 0L) + duration);
            }
        }
        return appUsageMap;
    }

    public static String formatTime(long millis) {
        long minutes = millis / 60000;
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m";
    }
}
