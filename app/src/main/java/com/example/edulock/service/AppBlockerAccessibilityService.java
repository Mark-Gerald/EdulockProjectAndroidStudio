package com.example.edulock.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.edulock.manager.RestrictionManager;

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
        int eventType = event.getEventType();

        // Only handle window state changes (app switches)
        // Remove TYPE_WINDOW_CONTENT_CHANGED from this filter — it's noisy and not needed for detection
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        String newApp = null;

        if (event.getPackageName() != null) {
            newApp = event.getPackageName().toString();
        }

        // If systemui or null, check root window — landscape games trigger through systemui
        if (newApp == null || newApp.equals("com.android.systemui") || newApp.isEmpty()) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                newApp = root.getPackageName().toString();
                root.recycle();
            }
        }

        if (newApp == null || newApp.isEmpty()) return;
        if (newApp.equals("com.android.systemui")) return; // Still skip if systemui after root check

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