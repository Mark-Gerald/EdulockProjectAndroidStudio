package com.example.edulock.service;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.edulock.R;
import com.example.edulock.manager.OverlayManager;
import com.example.edulock.manager.RestrictionManager;
import com.example.edulock.ui.acitvity.MainActivity;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core service for monitoring app usage and blocking restricted apps
 *
 * Architecture:
 * - Uses RestrictionManager for all data/logic
 * - Uses OverlayManager for blocking display
 * - Tracks active app via ForegroundAppMonitor
 * - Updates notification in real-time
 * - Resets usage daily at midnight
 */
public class AppMonitoringService extends Service {
    private static final String TAG = "AppMonitoringService";
    private static final int NOTIFICATION_ID = 123;
    private static final String CHANNEL_ID = "EduLockChannel";
    private static final long CHECK_INTERVAL_SECONDS = 1;

    private RestrictionManager restrictionManager;
    private OverlayManager overlayManager;
    private ForegroundAppMonitor foregroundMonitor;

    private ScheduledExecutorService scheduler;
    private Handler mainHandler;

    // Track current session time for each app
    private Map<String, Long> currentSessionStart = new HashMap<>();
    private Map<String, Long> sessionUsageTime = new HashMap<>();

    private AtomicBoolean isMonitoring = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🟢 Service created");

        mainHandler = new Handler(Looper.getMainLooper());

        // Initialize managers
        restrictionManager = new RestrictionManager(this);
        overlayManager = new OverlayManager(this);
        foregroundMonitor = new ForegroundAppMonitor(this);

        // Create notification channel
        createNotificationChannel();

        // Start with initial notification
        startForeground(NOTIFICATION_ID, createNotification());

        // Verify permissions
        verifyPermissions();

        // Start monitoring
        startMonitoring();

        // Register for screen on/off to pause/resume monitoring
        registerScreenStateReceiver();

        Log.d(TAG, "✅ Service initialized");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");

        if (intent != null) {
            String action = intent.getAction();

            if ("UPDATE_RESTRICTIONS".equals(action)) {
                Log.d(TAG, "🔄 Received UPDATE_RESTRICTIONS");
                handleRestrictionsUpdate();
            } else if ("CHECK_USAGE".equals(action)) {
                checkAndUpdateNotification();
            }
        }

        // Ensure notification is always up to date
        updateNotification();

        return START_STICKY;
    }

    /**
     * Handle when user saves new restrictions
     */
    private void handleRestrictionsUpdate() {
        // Reload restrictions from SharedPreferences
        restrictionManager = new RestrictionManager(this);

        // Clear all session tracking to start fresh
        currentSessionStart.clear();
        sessionUsageTime.clear();

        Log.d(TAG, "✅ Restrictions reloaded and sessions reset");
        updateNotification();
    }

    /**
     * Start the monitoring loop
     */
    private void startMonitoring() {
        if (isMonitoring.getAndSet(true)) {
            return; // Already running
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AppMonitoring");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                this::monitorAppUsage,
                0,
                CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        Log.d(TAG, "✅ Monitoring started");
    }

    /**
     * Main monitoring loop - called every second
     */
    private void monitorAppUsage() {
        try {
            String currentApp = foregroundMonitor.getCurrentForegroundApp();

            if (currentApp == null || currentApp.isEmpty()) {
                return;
            }

            // Check if this app is restricted
            if (!restrictionManager.isAppRestricted(currentApp)) {
                // App not restricted - clear its session
                currentSessionStart.remove(currentApp);
                sessionUsageTime.remove(currentApp);
                return;
            }

            // App is restricted - track its session
            long now = SystemClock.elapsedRealtime();

            if (!currentSessionStart.containsKey(currentApp)) {
                // New session started
                currentSessionStart.put(currentApp, now);
                sessionUsageTime.put(currentApp, 0L);
                Log.d(TAG, "▶️  Session started: " + currentApp);
                return;
            }

            // Update session elapsed time
            long sessionStart = currentSessionStart.get(currentApp);
            long elapsedSeconds = (now - sessionStart) / 1000;
            sessionUsageTime.put(currentApp, elapsedSeconds);

            // Add to daily usage (in 1-second increments)
            restrictionManager.addUsageTime(currentApp, 1);

            long totalDailyUsage = restrictionManager.getTodayUsageSeconds(currentApp);
            int timeLimitSeconds = restrictionManager.getTimeLimitMinutes() * 60;

            Log.d(TAG, "⏱️  " + currentApp + ": Session=" + elapsedSeconds + "s, Daily=" + totalDailyUsage + "s, Limit=" + timeLimitSeconds + "s");

            // Check if time exceeded
            if (restrictionManager.isTimeExceeded(currentApp)) {
                Log.d(TAG, "🚨 TIME LIMIT EXCEEDED: " + currentApp);
                overlayManager.showBlockingOverlay(currentApp);

                // Clear session so it doesn't keep showing overlay
                currentSessionStart.remove(currentApp);
                sessionUsageTime.remove(currentApp);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in monitoring loop: " + e.getMessage(), e);
        }
    }

    /**
     * Update notification with current status
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

        Set<String> restrictedApps = restrictionManager.getRestrictedApps();
        int appCount = restrictedApps.size();
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

    /**
     * Verify required permissions
     */
    private void verifyPermissions() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());

        if (mode != AppOpsManager.MODE_ALLOWED) {
            Log.w(TAG, "⚠️  Usage stats permission not granted");
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "⚠️  Overlay permission not granted");
        }
    }

    /**
     * Register receiver for screen on/off to pause/resume monitoring
     */
    private void registerScreenStateReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(screenReceiver, filter);
            }
        }
    }

    /**
     * Handle screen on/off
     */
    private BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                Log.d(TAG, "📵 Screen off - pausing session tracking");
                currentSessionStart.clear();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                Log.d(TAG, "📱 Screen on - resuming monitoring");
            }
        }
    };

    /**
     * Check usage and update notification without full reload
     */
    private void checkAndUpdateNotification() {
        updateNotification();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver: " + e.getMessage());
        }

        isMonitoring.set(false);
        Log.d(TAG, "🔴 Service destroyed");
    }
}