package com.example.edulock.service;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import android.view.View;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.edulock.ui.acitvity.MainActivity;
import com.example.edulock.ui.acitvity.OverlayBlockedActivity;
import com.example.edulock.R;
import com.example.edulock.receiver.UsageStatsResetReceiver;

public class AppMonitoringService extends Service {
    private static final String TAG = "AppMonitoringService";
    private static final int NOTIFICATION_ID = 123;
    private static final String CHANNEL_ID = "EduLockChannel";
    private static final String PREFS_NAME = "app_restrictions";
    private static final String KEY_RESTRICTED_APPS = "restricted_apps";
    private static final String KEY_TIME_LIMIT = "selected_time_limit";
    private static final int DEFAULT_TIME_LIMIT = 1;

    // Atomic boolean for overlay state to avoid synchronization blocks
    private final AtomicBoolean isOverlayShowing = new AtomicBoolean(false);

    // ConcurrentHashMap to avoid synchronization blocks
    private final Map<String, Long> appUsageTimes = new ConcurrentHashMap<>();
    private Set<String> restrictedApps = new HashSet<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService scheduler;

    private UsageStatsManager usageStatsManager;
    private int timeLimit;
    private WindowManager windowManager;
    private View overlayView;

    // Use volatile for thread visibility without synchronization
    private volatile long lastCheckTime;
    private volatile String lastForegroundApp = "";


    private static final long CHECK_INTERVAL_SECONDS = 1;
    private static final long OVERLAY_SHOW_DELAY_MS = 500;

    // Background monitoring task
    private final Runnable monitorTask = new Runnable() {
        @Override
        public void run() {
            try {
                if (!isOverlayShowing.get()) {
                    String currentApp = lastForegroundApp;
                    Log.d(TAG, "Current foreground app: " + currentApp);

                    if (!currentApp.isEmpty() && !currentApp.equals(getPackageName())) {
                        if (restrictedApps.contains(currentApp)) {
                            Log.d(TAG, "Monitoring restricted app: " + currentApp);
                            processAppUsage(currentApp);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in monitor task", e);
            }
        }
    };

    private void processAppUsage(String packageName) {
        if (packageName.isEmpty() || packageName.equals(getPackageName())) {
            return;
        }

        long currentTime = SystemClock.elapsedRealtime();

        // Initialize usage time if needed
        if (!appUsageTimes.containsKey(packageName)) {
            appUsageTimes.put(packageName, 0L);
            lastCheckTime = currentTime;
            return;
        }

        // Ensure minimum time between checks
        if (lastCheckTime > 0) {
            long elapsedTime = (currentTime - lastCheckTime) / 1000;
            if (elapsedTime > 0) {
                long newUsage = appUsageTimes.getOrDefault(packageName, 0L) + elapsedTime;
                appUsageTimes.put(packageName, newUsage);

                // Check if time limit exceeded
                Log.d(TAG, "Usage: " + newUsage + " seconds");
                if (newUsage >= timeLimit * 60) {
                    if (isOverlayShowing.compareAndSet(false, true)) {
                        mainHandler.post(() -> {
                            try {
                                showBlockingOverlay(packageName);
                            } catch (Exception e) {
                                Log.e(TAG, "Error showing overlay: " + e.getMessage());
                                isOverlayShowing.set(false);
                            }
                        });
                    }
                }
            }
        }
        lastCheckTime = currentTime;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AppMonitoringService onCreate called");

        // Start foreground immediately with a temporary notification
        createNotificationChannel();

        try {
            // Initialize core components first
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);

            // Load settings and verify permissions
            loadRestrictions();
            if (!verifyAllPermissions()) {
                Log.e(TAG, "Critical permissions missing");
                stopSelf();
                return;
            }

            // Initialize monitoring
            resetUsageTimes();
            initializeScheduler();
            scheduleMidnightReset();

            // Update notification with actual content
            updateForegroundNotification();

            Log.d(TAG, "Service initialization complete");
        } catch (Exception e) {
            Log.e(TAG, "Fatal error in onCreate", e);
            stopSelf();
        }
    }

    private void initializeScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(monitorTask, 0, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        Log.d(TAG, "Scheduler initialized");
    }

    private Notification createInitialNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("EduLock Starting")
                .setContentText("Initializing service...")
                .setSmallIcon(R.drawable.ic_timer)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build();
    }

    private boolean verifyAllPermissions() {
        // Check for usage stats permission
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        boolean hasUsageStats = mode == AppOpsManager.MODE_ALLOWED;

        // Check for overlay permission
        boolean canDrawOverlays = Settings.canDrawOverlays(this);

        // Check alarm permission for Android 12+
        boolean hasAlarmPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            hasAlarmPermission = alarmManager.canScheduleExactAlarms();
        }

        Log.d(TAG, "Permissions: Usage Stats=" + hasUsageStats +
                ", Draw Overlays=" + canDrawOverlays +
                ", Alarms=" + hasAlarmPermission);

        if (!hasUsageStats) {
            showPermissionRequest(Settings.ACTION_USAGE_ACCESS_SETTINGS,
                    R.string.grant_usage_stats_permission);
        }

        if (!canDrawOverlays) {
            showPermissionRequest(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    R.string.grant_overlay_permission);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasAlarmPermission) {
            showPermissionRequest(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    R.string.grant_alarm_permission);
        }

        return hasUsageStats && canDrawOverlays && hasAlarmPermission;
    }

    private void updateForegroundNotification() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createNotification());
        }
    }

    public void updateRestrictions() {
        Log.d(TAG, "🔥 updateRestrictions CALLED");

        loadRestrictions();

        Log.d(TAG, "Restricted apps count: " + restrictedApps.size());
        Log.d(TAG, "Time limit: " + timeLimit);

        // 🔥 RESET EVERYTHING IMMEDIATELY
        appUsageTimes.clear();
        lastCheckTime = 0;

        // 🔥 FORCE notification update NOW
        mainHandler.post(this::updateForegroundNotification);
    }

    @SuppressLint({"ForegroundServiceType", "MissingPermission"})
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && "APP_SWITCHED".equals(intent.getAction())) {
            String packageName = intent.getStringExtra("package_name");

            if (packageName != null) {
                lastForegroundApp = packageName;

                // RESET TIMER for new app
                if (!appUsageTimes.containsKey(packageName)) {
                    appUsageTimes.put(packageName, 0L);
                }

                lastCheckTime = SystemClock.elapsedRealtime();

                Log.d(TAG, "Switched to: " + packageName);
            }
        }

        if (intent != null && "UPDATE_RESTRICTIONS".equals(intent.getAction())) {
            updateRestrictions();
        }

        Log.d(TAG, "onStartCommand called with startId: " + startId);

        // Ensure foreground is started immediately
        startForeground(NOTIFICATION_ID, createNotification());

        try {
            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(monitorTask, 0, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
            }
            updateForegroundNotification();
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand: " + e.getMessage(), e);
        }

        return START_STICKY;
    }

    private void resetUsageTimes() {
        appUsageTimes.clear();
        Log.d(TAG, "App usage times reset");
        lastCheckTime = 0;
    }

    private void showPermissionRequest(String action, int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(action);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void loadRestrictions() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        restrictedApps = new HashSet<>(prefs.getStringSet(KEY_RESTRICTED_APPS, new HashSet<>()));
        timeLimit = prefs.getInt(KEY_TIME_LIMIT, 1);

        Log.d("SERVICE_DEBUG", "LOADED APPS: " + restrictedApps);
        Log.d("SERVICE_DEBUG", "COUNT: " + restrictedApps.size());
    }

    private void showBlockingOverlay(String packageName) {
        if (isOverlayShowing.compareAndSet(false, true)) {
            // Delay overlay show to prevent UI thread overload
            mainHandler.postDelayed(() -> {
                try {
                    Intent overlayIntent = new Intent(this, OverlayBlockedActivity.class);
                    overlayIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                    Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(overlayIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to show overlay: " + e.getMessage());
                    isOverlayShowing.set(false);
                }
            }, OVERLAY_SHOW_DELAY_MS);
        }

        Log.d(TAG, "🔥 LOADED APPS: " + restrictedApps);
    }

    private void scheduleMidnightReset() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.add(Calendar.DAY_OF_YEAR, 1);

        Intent intent = new Intent(this, UsageStatsResetReceiver.class);
        intent.setAction(UsageStatsResetReceiver.ACTION_RESET_STATS);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                pendingIntent
        );
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.edulock_service_name),
                        NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription(getString(R.string.edulock_service_description));
                channel.setShowBadge(true);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(channel);
                    Log.d(TAG, "Notification channel created successfully");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating notification channel: " + e.getMessage(), e);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        int appCount = restrictedApps.size();

        String text;
        if (appCount == 1) {
            text = "Monitoring 1 app with " + timeLimit + " min limit";
        } else {
            text = "Monitoring " + appCount + " apps with " + timeLimit + " min limit";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.edulock_active))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        return builder.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // No manual GC calls - let system handle memory
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Clean shutdown
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        // Clear any pending tasks
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }

        Log.d(TAG, "Service destroyed");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Don't recreate the overlay here - it's expensive
    }


    public static class ServiceRestartReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent != null ? intent.getAction() : null;
            if (action != null && (
                    action.equals("android.intent.action.BOOT_COMPLETED") ||
                            action.equals("com.example.edulock.RESTART_SERVICE"))) {

                Intent serviceIntent = new Intent(context, AppMonitoringService.class);
                serviceIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "Task removed, scheduling restart");

        Intent restartServiceIntent = new Intent(getApplicationContext(), getClass());
        restartServiceIntent.setPackage(getPackageName());

        PendingIntent restartServicePendingIntent = PendingIntent.getService(
                getApplicationContext(), 1, restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmService = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmService.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent);
    }
}