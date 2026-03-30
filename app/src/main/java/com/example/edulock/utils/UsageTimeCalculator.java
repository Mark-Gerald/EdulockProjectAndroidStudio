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

        // 🔥 IMPORTANT FIX: DO NOT count currently open apps
        // Only use data from COMPLETED sessions (apps that were paused)
        // This prevents apps from continuing to accumulate time after closing

        // Clear any unfinished sessions (don't add them)
        appStartTime.clear();

        return appUsageMap;
    }

    public static String formatTime(long millis) {
        long minutes = millis / 60000;
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m";
    }

    /**
     * Get app usage for the PREVIOUS day (for midnight summary notification)
     *
     * Called at midnight to show YESTERDAY's total, not today's (which is 0h 0m)
     */
    public static Map<String, Long> getAppUsagePreviousDay(Context context) {
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

        Calendar calendar = Calendar.getInstance();

        // Go back 1 day
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long startTime = calendar.getTimeInMillis();

        // End time = today at 00:00 (midnight)
        Calendar endCalendar = Calendar.getInstance();
        endCalendar.set(Calendar.HOUR_OF_DAY, 0);
        endCalendar.set(Calendar.MINUTE, 0);
        endCalendar.set(Calendar.SECOND, 0);
        endCalendar.set(Calendar.MILLISECOND, 0);

        long endTime = endCalendar.getTimeInMillis();

        UsageEvents events = usm.queryEvents(startTime, endTime);
        return processEvents(events);
    }

    /**
     * Get total screen time for the PREVIOUS day
     */
    public static long getTotalScreenTimePreviousDay(Context context) {
        Map<String, Long> appUsage = getAppUsagePreviousDay(context);
        long total = 0;
        for (Long time : appUsage.values()) {
            total += time;
        }
        return total;
    }
}
