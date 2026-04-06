package com.example.edulock.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import android.view.View;

public class AppBlockerAccessibilityService extends AccessibilityService {
    private static final String TAG = "AppBlockerAccessibility";
    private static final String PREFS_NAME = "app_restrictions";
    private static final String KEY_RESTRICTED_APPS = "restricted_apps";
    private static final String KEY_TIME_LIMIT = "selected_time_limit";

    private Set<String> restrictedApps = new HashSet<>();
    private String currentForegroundApp = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private HashMap<String, Integer> appLimits = new HashMap<>();

    private View overlayView = null;

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadRestrictions();
        startMonitoringService();

        Log.d(TAG, "AppBlockerAccessibilityService created");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String newApp = event.getPackageName().toString();

            if (newApp != null && !newApp.equals(currentForegroundApp)) {
                currentForegroundApp = newApp;
                notifyAppChanged(currentForegroundApp);
            }
        }
    }

    private void loadRestrictions() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        restrictedApps = new HashSet<>(prefs.getStringSet(KEY_RESTRICTED_APPS, new HashSet<>()));
        Set<String> apps = prefs.getStringSet(KEY_RESTRICTED_APPS, new HashSet<>());
        restrictedApps = new HashSet<>(apps);

        int globalLimit = prefs.getInt(KEY_TIME_LIMIT, 1);

        for (String app : restrictedApps) {
            appLimits.put(app, globalLimit);
        }
        Log.d(TAG, "Loaded " + restrictedApps.size() + " apps");
    }

    private void startMonitoringService() {
        Intent serviceIntent = new Intent(this, AppMonitoringService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void notifyAppChanged(String packageName) {
        Intent intent = new Intent(this, AppMonitoringService.class);
        intent.setAction("APP_SWITCHED");
        intent.putExtra("package_name", packageName);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "AppBlockerAccessibilityService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "AppBlockerAccessibilityService destroyed");
    }
}