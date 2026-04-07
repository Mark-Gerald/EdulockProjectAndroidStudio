package com.example.edulock.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.example.edulock.manager.RestrictionManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Detects when restricted apps are launched
 * Sends app switch events to AppMonitoringService for time limit checking
 */
public class AppBlockerAccessibilityService extends AccessibilityService {
    private static final String TAG = "AppBlockerAccessibility";

    private String currentForegroundApp = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RestrictionManager restrictionManager;

    @Override
    public void onCreate() {
        super.onCreate();
        restrictionManager = new RestrictionManager(this);
        startMonitoringService();
        Log.d(TAG, "✅ AppBlockerAccessibilityService created");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Only care about window state changes (app switches)
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        String newApp = event.getPackageName().toString();

        if (newApp == null || newApp.isEmpty()) {
            return;
        }

        // If app changed, notify the monitoring service
        if (!newApp.equals(currentForegroundApp)) {
            currentForegroundApp = newApp;
            Log.d(TAG, "📱 App detected: " + newApp);
            notifyAppChanged(newApp);
        }
    }

    /**
     * Start the monitoring service that will handle time limit checking
     */
    private void startMonitoringService() {
        Intent serviceIntent = new Intent(this, AppMonitoringService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Log.d(TAG, "✅ Monitoring service started");
    }

    /**
     * Notify monitoring service of app switch
     */
    private void notifyAppChanged(String packageName) {
        Intent intent = new Intent(this, AppMonitoringService.class);
        intent.setAction("APP_SWITCHED");
        intent.putExtra("package_name", packageName);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        Log.d(TAG, "🔔 Notified service of app switch: " + packageName);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "🔴 AppBlockerAccessibilityService destroyed");
    }
}