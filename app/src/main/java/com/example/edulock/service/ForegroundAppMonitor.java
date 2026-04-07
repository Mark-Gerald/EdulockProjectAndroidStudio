package com.example.edulock.service;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * Monitors which app is currently in foreground
 */
public class ForegroundAppMonitor {
    private static final String TAG = "ForegroundAppMonitor";
    private ActivityManager activityManager;
    private String lastForegroundApp = "";

    public ForegroundAppMonitor(Context context) {
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    }

    /**
     * Get the package name of currently foreground app
     */
    public String getCurrentForegroundApp() {
        try {
            List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty()) {
                String packageName = tasks.get(0).topActivity.getPackageName();

                if (!packageName.equals(lastForegroundApp)) {
                    Log.d(TAG, "App switched to: " + packageName);
                    lastForegroundApp = packageName;
                }

                return packageName;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting foreground app: " + e.getMessage());
        }

        return "";
    }
}