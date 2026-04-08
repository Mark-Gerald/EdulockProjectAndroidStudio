package com.example.edulock.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.edulock.R;
import com.example.edulock.manager.OverlayManager;
import com.example.edulock.manager.RestrictionManager;
import com.example.edulock.ui.acitvity.MainActivity;
import com.example.edulock.ui.acitvity.TimeLimitBlockedActivity;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitoring service that:
 * 1. Receives app switch events from AccessibilityService
 * 2. Checks if app is restricted
 * 3. Checks if time limit exceeded TODAY
 * 4. Blocks app if needed
 * 5. Tracks usage time
 */
public class AppMonitoringService extends Service {
    private static final String TAG = "AppMonitoringService";
    private static final int NOTIFICATION_ID = 123;
    private static final String CHANNEL_ID = "EduLockChannel";

    private RestrictionManager restrictionManager;
    private OverlayManager overlayManager;
    private Handler mainHandler;

    private String currentTrackedApp = "";
    private Handler trackingHandler = new Handler(Looper.getMainLooper());
    private Runnable trackingRunnable;

    private String lastBlockedApp = "";
    private AtomicBoolean isBlockingActive = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🟢 Service created");

        mainHandler = new Handler(Looper.getMainLooper());

        // Initialize managers
        restrictionManager = new RestrictionManager(this);
        overlayManager = new OverlayManager(this);

        // Create notification channel
        createNotificationChannel();

        // Start with initial notification
        startForeground(NOTIFICATION_ID, createNotification());

        Log.d(TAG, "✅ Service initialized");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");

        if (intent == null) {
            Log.d(TAG, "⚠️  Intent is null, re-creating managers");
            restrictionManager = new RestrictionManager(this);
            updateNotification();
            return START_STICKY;
        }

        String action = intent.getAction();
        Log.d(TAG, "Action received: " + (action != null ? action : "null"));

        // Handle UPDATE_RESTRICTIONS - reload data
        if ("UPDATE_RESTRICTIONS".equals(action)) {
            Log.d(TAG, "🔄 UPDATE_RESTRICTIONS received - reloading everything");

            // Create NEW instance to force reload from SharedPreferences
            restrictionManager = new RestrictionManager(this);

            // Log what was loaded
            int appCount = restrictionManager.getRestrictedApps().size();
            int timeLimit = restrictionManager.getTimeLimitMinutes();
            Log.d(TAG, "✅ Reloaded: " + appCount + " apps, " + timeLimit + " min limit");

            // Update notification immediately
            updateNotification();
            return START_STICKY;
        }

        // Handle APP_SWITCHED
        if ("APP_SWITCHED".equals(action)) {
            String packageName = intent.getStringExtra("package_name");
            Log.d(TAG, "📱 APP_SWITCHED: " + packageName);
            handleAppSwitch(packageName);
            return START_STICKY;
        }

        Log.d(TAG, "Unknown action or no action");
        return START_STICKY;
    }

    /**
     * Handle when an app comes to foreground
     */
    /**
     * Handle when an app comes to foreground
     */
    /**
     * Handle when an app comes to foreground
     */
    /**
     * Handle when an app comes to foreground
     */
    private void handleAppSwitch(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            Log.d(TAG, "❌ Package name is null/empty");
            return;
        }

        Log.d(TAG, "═══════════════════════════════════════════");
        Log.d(TAG, "📱 APP SWITCH EVENT");
        Log.d(TAG, "Package: " + packageName);
        Log.d(TAG, "═══════════════════════════════════════════");

        // ✅ IGNORE SYSTEM APPS AND OUR OWN APP DURING APP SWITCH
        if (isSystemOrOwnApp(packageName)) {
            Log.d(TAG, "⏭️  Ignoring system/own app: " + packageName);
            return;
        }

        // ✅ If overlay is currently showing, ignore
        if (isBlockingActive.get()) {
            Log.d(TAG, "⏸��  Overlay is active - ignoring app switch for: " + packageName);
            return;
        }

        // Stop tracking previous app
        stopTrackingUsage();

        // Check if app is restricted
        boolean isRestricted = restrictionManager.isAppRestricted(packageName);
        Log.d(TAG, "🔍 Is restricted? " + isRestricted);

        if (!isRestricted) {
            Log.d(TAG, "✅ App not restricted: " + packageName);
            updateNotification();
            return;
        }

        Log.d(TAG, "🚨 App IS RESTRICTED: " + packageName);

        // Get time info
        long usedSeconds = restrictionManager.getTodayUsageSeconds(packageName);
        int limitSeconds = restrictionManager.getTimeLimitMinutes() * 60;
        boolean timeExceeded = restrictionManager.isTimeExceeded(packageName);

        Log.d(TAG, "⏱️  Used: " + usedSeconds + "s / Limit: " + limitSeconds + "s");
        Log.d(TAG, "⏰ Time exceeded? " + timeExceeded);

        if (timeExceeded) {
            Log.d(TAG, "🚨🚨🚨 TIME LIMIT EXCEEDED - BLOCKING: " + packageName);
            blockApp(packageName);
        } else {
            // Time not exceeded - start tracking
            Log.d(TAG, "▶️  Time not exceeded - starting to track: " + packageName);
            startTrackingUsage(packageName);
        }

        updateNotification();
    }

    private boolean isSystemOrOwnApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return true;
        }

        // Our own app
        if (packageName.equals(getPackageName())) {
            return true;
        }

        // System UI and other system apps
        if (packageName.startsWith("com.android.") ||
                packageName.startsWith("com.miui.") ||
                packageName.startsWith("com.mi.") ||
                packageName.equals("com.android.systemui")) {
            return true;
        }

        return false;
    }

    /**
     * Block the app by showing overlay
     */
    private void blockApp(String packageName) {
        Log.d(TAG, "");
        Log.d(TAG, "╔════════════════════════════════════════╗");
        Log.d(TAG, "║         BLOCKING APP NOW               ║");
        Log.d(TAG, "╚════════════════════════════════════════╝");

        if (packageName == null || packageName.isEmpty()) {
            Log.e(TAG, "❌ Package name is null!");
            return;
        }

        Log.d(TAG, "Target to block: " + packageName);

        // Check if trying to block ourselves
        if (packageName.equals(getPackageName())) {
            Log.e(TAG, "❌ CRITICAL: Cannot block our own app!");
            return;
        }

        isBlockingActive.set(true);
        lastBlockedApp = packageName;

        Log.d(TAG, "✅ SHOWING OVERLAY FOR: " + packageName);

        try {
            // Use OverlayBlockService instead of activity
            Intent overlayIntent = new Intent(this, OverlayBlockService.class);
            overlayIntent.putExtra("package_name", packageName);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(overlayIntent);
            } else {
                startService(overlayIntent);
            }

            Log.d(TAG, "✅ Overlay service started for: " + packageName);

            // Schedule reset after 3 seconds
            scheduleBlockingReset(3000);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting overlay: " + e.getMessage(), e);
            isBlockingActive.set(false);
        }
    }

    private void scheduleBlockingReset(long delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                if (isBlockingActive.get()) {
                    isBlockingActive.set(false);
                    Log.d(TAG, "🔄 Blocking state reset after " + (delayMs/1000) + "s");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Sleep interrupted");
            }
        }).start();
    }

    /**
     * Track usage time for an app session - increments every second
     */
    private void startTrackingUsage(String packageName) {
        // Stop any previous tracking
        stopTrackingUsage();

        currentTrackedApp = packageName;
        Log.d(TAG, "▶️  Starting to track: " + packageName);

        trackingRunnable = new Runnable() {
            @Override
            public void run() {
                // Safety check - only track if this is still the current app
                if (!currentTrackedApp.equals(packageName)) {
                    Log.d(TAG, "App changed, stopping tracking");
                    return;
                }

                // Add 1 second to usage
                restrictionManager.addUsageTime(packageName, 1);

                long usedSeconds = restrictionManager.getTodayUsageSeconds(packageName);
                int limitSeconds = restrictionManager.getTimeLimitMinutes() * 60;

                Log.d(TAG, "⏱️ " + packageName + " | Used: " + usedSeconds + "s / Limit: " + limitSeconds + "s");

                // Check if time exceeded NOW
                if (restrictionManager.isTimeExceeded(packageName)) {
                    Log.d(TAG, "🚨 TIME LIMIT REACHED for: " + packageName);
                    blockApp(packageName);
                    return; // Stop tracking
                }

                // Continue tracking every second
                trackingHandler.postDelayed(this, 1000);
            }
        };

        // Start tracking after 1 second
        trackingHandler.postDelayed(trackingRunnable, 1000);
    }

    private void stopTrackingUsage() {
        if (trackingRunnable != null) {
            trackingHandler.removeCallbacks(trackingRunnable);
            trackingRunnable = null;
            Log.d(TAG, "⏸️  Stopped tracking");
        }
        currentTrackedApp = "";
    }

    /**
     * Update notification with current restrictions status
     */
    private void updateNotification() {
        mainHandler.post(() -> {
            try {
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.notify(NOTIFICATION_ID, createNotification());
                    Log.d(TAG, "📢 Notification updated");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating notification: " + e.getMessage());
            }
        });
    }

    /**
     * Create the persistent notification
     */
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        int appCount = restrictionManager.getRestrictedApps().size();
        int timeLimit = restrictionManager.getTimeLimitMinutes();

        Log.d(TAG, "🔍 Creating notification - appCount=" + appCount + ", timeLimit=" + timeLimit);

        String contentText;
        if (appCount == 0) {
            contentText = "No restrictions active";
        } else if (appCount == 1) {
            contentText = "Monitoring 1 app with " + timeLimit + " min limit";
        } else {
            contentText = "Monitoring " + appCount + " apps with " + timeLimit + " min limit";
        }

        Log.d(TAG, "📢 Notification text: " + contentText);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("EduLock Active")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    /**
     * Create notification channel for Android 8+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "EduLock Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("EduLock app monitoring");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Reset blocking state after a delay
     * This allows the overlay to dismiss and prevents re-blocking
     */
    private void resetBlockingStateAfterDelay() {
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Wait 2 seconds
                isBlockingActive.set(false);
                Log.d(TAG, "🔄 Blocking state reset");
            } catch (InterruptedException e) {
                Log.e(TAG, "Sleep interrupted: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTrackingUsage();
        isBlockingActive.set(false);
        Log.d(TAG, "🔴 Service destroyed");
    }
}