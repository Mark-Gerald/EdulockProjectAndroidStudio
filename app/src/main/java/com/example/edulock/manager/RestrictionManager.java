package com.example.edulock.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages app restrictions and usage tracking with daily reset functionality
 *
 * Responsibilities:
 * - Store/retrieve restricted apps and their time limits
 * - Track daily usage time per app
 * - Handle daily reset at midnight
 * - Provide restriction status queries
 */
public class RestrictionManager {
    private static final String TAG = "RestrictionManager";
    private static final String PREFS_NAME = "app_restrictions";

    // Preference keys for restrictions (permanent settings)
    private static final String KEY_RESTRICTED_APPS = "restricted_apps";
    private static final String KEY_TIME_LIMIT = "selected_time_limit";

    // Preference keys for daily usage tracking (resets at midnight)
    private static final String KEY_USAGE_PREFIX = "usage_";
    private static final String KEY_LAST_RESET_DATE = "last_reset_date";

    private SharedPreferences prefs;
    private Context context;

    public RestrictionManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Check if we need to reset daily usage
        checkAndResetDaily();
    }

    // ==================== RESTRICTION MANAGEMENT ====================

    /**
     * Get all restricted apps
     */
    public Set<String> getRestrictedApps() {
        return new HashSet<>(prefs.getStringSet(KEY_RESTRICTED_APPS, new HashSet<>()));
    }

    /**
     * Get time limit in minutes for all apps
     */
    public int getTimeLimitSeconds() {
        return prefs.getInt(KEY_TIME_LIMIT, 0);  // This is already seconds — no conversion needed
    }

    /**
     * Check if an app is restricted
     */
    /**
     * Check if an app is restricted
     */
    public boolean isAppRestricted(String packageName) {
        // Never block our own app
        if (packageName.equals(context.getPackageName())) {
            return false;
        }

        return getRestrictedApps().contains(packageName);
    }

    /**
     * Save restrictions from TimeLimitActivity
     * @param apps List of package names to restrict
     * @param timeLimitMinutes Time limit in minutes
     */

    // ==================== USAGE TRACKING ====================

    /**
     * Get today's usage time for an app in seconds
     */
    public long getTodayUsageSeconds(String packageName) {
        String key = KEY_USAGE_PREFIX + packageName;
        return prefs.getLong(key, 0L);
    }

    /**
     * Add elapsed time to app's daily usage
     */
    public void addUsageTime(String packageName, long elapsedSeconds) {
        String key = KEY_USAGE_PREFIX + packageName;
        long currentUsage = getTodayUsageSeconds(packageName);
        long newUsage = currentUsage + elapsedSeconds;

        prefs.edit().putLong(key, newUsage).apply();

        Log.d(TAG, "⏱️  " + packageName + " usage: " + currentUsage + "s → " + newUsage + "s");
    }

    /**
     * Check if app has exceeded its time limit
     */
    public boolean isTimeExceeded(String packageName) {
        if (!isAppRestricted(packageName)) return false;
        int limitSeconds = getTimeLimitSeconds();
        if (limitSeconds == 0) return false; // No limit set
        long usageSeconds = getTodayUsageSeconds(packageName);
        boolean exceeded = usageSeconds >= limitSeconds;
        if (exceeded) {
            Log.d(TAG, "🚨 TIME EXCEEDED: " + packageName + " (" + usageSeconds + "s >= " + limitSeconds + "s)");
        }
        return exceeded;
    }

    /**
     * Reset usage for a specific app
     */
    public void resetAppUsage(String packageName) {
        String key = KEY_USAGE_PREFIX + packageName;
        prefs.edit().remove(key).apply();
        Log.d(TAG, "🔄 Reset usage for: " + packageName);
    }

    /**
     * Get all usage data for the day (for debugging/display)
     */
    public Map<String, Long> getAllDailyUsage() {
        Map<String, Long> usageMap = new HashMap<>();
        Map<String, ?> allPrefs = prefs.getAll();

        for (String key : allPrefs.keySet()) {
            if (key.startsWith(KEY_USAGE_PREFIX)) {
                String packageName = key.replace(KEY_USAGE_PREFIX, "");
                long usageSeconds = prefs.getLong(key, 0L);
                usageMap.put(packageName, usageSeconds);
            }
        }

        return usageMap;
    }

    // ==================== DAILY RESET ====================

    /**
     * Check if it's a new day and reset all usage if needed
     */
    private void checkAndResetDaily() {
        String today = getTodayDateString();
        String lastResetDate = prefs.getString(KEY_LAST_RESET_DATE, "");

        if (!today.equals(lastResetDate)) {
            Log.d(TAG, "🌅 New day detected! Resetting all daily usage...");
            resetAllDailyUsage();
            prefs.edit().putString(KEY_LAST_RESET_DATE, today).apply();
        }
    }

    /**
     * Reset all apps' daily usage (called at midnight)
     */
    private void resetAllDailyUsage() {
        SharedPreferences.Editor editor = prefs.edit();
        Map<String, ?> allPrefs = prefs.getAll();

        for (String key : allPrefs.keySet()) {
            if (key.startsWith(KEY_USAGE_PREFIX)) {
                editor.remove(key);
            }
        }

        editor.apply();
        Log.d(TAG, "✅ All daily usage reset");
    }

    /**
     * Get today's date as string (YYYY-MM-DD)
     */
    private String getTodayDateString() {
        Calendar calendar = Calendar.getInstance();
        return String.format("%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * Get seconds until midnight (for scheduling reset)
     */
    public long getSecondsUntilMidnight() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        long now = System.currentTimeMillis();
        long midnight = calendar.getTimeInMillis();

        return (midnight - now) / 1000;
    }

    /**
     * Force reset (for testing)
     */
    public void forceResetAllUsage() {
        resetAllDailyUsage();
        prefs.edit().remove(KEY_LAST_RESET_DATE).apply();
        Log.d(TAG, "🔄 FORCE RESET all usage");
    }
}