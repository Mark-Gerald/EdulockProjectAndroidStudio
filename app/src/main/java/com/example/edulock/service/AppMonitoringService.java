package com.example.edulock.service;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
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
        if (intent == null) {
            return START_STICKY;
        }

        String action = intent.getAction();

        if ("APP_SWITCHED".equals(action)) {
            // App was switched - check if we should block it
            String packageName = intent.getStringExtra("package_name");
            handleAppSwitch(packageName);
        }
        else if ("UPDATE_RESTRICTIONS".equals(action)) {
            // User saved new restrictions
            Log.d(TAG, "🔄 Restrictions updated");
            restrictionManager = new RestrictionManager(this);
            updateNotification();
        }

        return START_STICKY;
    }

    /**
     * Handle when an app comes to foreground
     */
    private void handleAppSwitch(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;

        Log.d(TAG, "📱 App switched: " + packageName);

        // Stop tracking previous app
        stopTrackingUsage();
        isBlockingActive.set(false); // Reset blocking state on app switch

        if (!restrictionManager.isAppRestricted(packageName)) {
            Log.d(TAG, "✅ App not restricted: " + packageName);
            updateNotification();
            return;
        }

        Log.d(TAG, "🚨 App IS RESTRICTED: " + packageName);

        if (restrictionManager.isTimeExceeded(packageName)) {
            Log.d(TAG, "⏰ TIME LIMIT EXCEEDED for: " + packageName);
            blockApp(packageName);
        } else {
            long usedSeconds = restrictionManager.getTodayUsageSeconds(packageName);
            int limitSeconds = restrictionManager.getTimeLimitMinutes() * 60;
            Log.d(TAG, "⏱️ " + packageName + ": Used " + usedSeconds + "s of " + limitSeconds + "s");
            startTrackingUsage(packageName);
        }

        updateNotification();
    }

    /**
     * Block the app by showing overlay
     */
    private void blockApp(String packageName) {
        if (packageName.equals(lastBlockedApp) && isBlockingActive.get()) {
            Log.d(TAG, "⏭️  Already blocking this app");
            return;
        }

        lastBlockedApp = packageName;
        isBlockingActive.set(true);

        Log.d(TAG, "🛑 BLOCKING APP: " + packageName);

        // Show overlay using the same mechanism as teacher control
        overlayManager.showBlockingOverlay(packageName);
    }

    /**
     * Track usage time for an app session
     * This runs while the app is in foreground
     */
    private void startTrackingUsage(String packageName) {
        // Stop any previous tracking
        stopTrackingUsage();

        currentTrackedApp = packageName;
        trackingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!currentTrackedApp.equals(packageName)) return;

                restrictionManager.addUsageTime(packageName, 1);
                Log.d(TAG, "⏱️ Tracking: " + packageName + " | Used: " + restrictionManager.getTodayUsageSeconds(packageName) + "s");

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
        trackingHandler.postDelayed(trackingRunnable, 1000);
    }

    private void stopTrackingUsage() {
        if (trackingRunnable != null) {
            trackingHandler.removeCallbacks(trackingRunnable);
            trackingRunnable = null;
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

        String contentText;
        if (appCount == 0) {
            contentText = "No restrictions active";
        } else if (appCount == 1) {
            contentText = "Monitoring 1 app with " + timeLimit + " min limit";
        } else {
            contentText = "Monitoring " + appCount + " apps with " + timeLimit + " min limit";
        }

        Log.d(TAG, "📢 Notification: " + contentText);

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

    public static class ServiceRestartReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("ServiceRestartReceiver", "📡 Received: " + intent.getAction());
            Intent serviceIntent = new Intent(context, AppMonitoringService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}