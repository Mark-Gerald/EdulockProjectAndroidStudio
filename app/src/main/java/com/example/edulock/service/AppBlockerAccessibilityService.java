package com.example.edulock.service;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.accessibility.AccessibilityEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import android.view.WindowManager;
import android.view.LayoutInflater;
import android.graphics.PixelFormat;
import android.view.View;
import android.widget.Button;

import com.example.edulock.ui.acitvity.MainActivity;
import com.example.edulock.R;

public class AppBlockerAccessibilityService extends AccessibilityService {
    private static final String TAG = "AppBlockerAccessibility";
    private static final String PREFS_NAME = "app_restrictions";
    private static final String KEY_RESTRICTED_APPS = "restricted_apps";
    private static final String KEY_TIME_LIMIT = "time_limit";

    private Set<String> restrictedApps = new HashSet<>();
    private String currentForegroundApp = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private HashMap<String, Long> appUsageTimes = new HashMap<>();
    private HashMap<String, Long> lastUsedMap = new HashMap<>();
    private HashMap<String, Integer> appLimits = new HashMap<>();

    private static final boolean DEBUG = false;

    private View overlayView = null;

    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Handler overlayMonitor = new Handler(Looper.getMainLooper());

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (restrictedApps.contains(currentForegroundApp)) {
                long now = System.currentTimeMillis();

                Long lastTime = lastUsedMap.get(currentForegroundApp);
                if (lastTime == null) lastTime = now;

                long diff = (now - lastTime) / 1000;

                long currentUsage = appUsageTimes.getOrDefault(currentForegroundApp, 0L);
                currentUsage += diff;

                appUsageTimes.put(currentForegroundApp, currentUsage);
                lastUsedMap.put(currentForegroundApp, now);

                int limit = appLimits.getOrDefault(currentForegroundApp, 1);

                if (DEBUG) {
                    Log.d(TAG, "Timer: " + currentForegroundApp + " used for " + currentUsage + " seconds. Limit: " + limit + " minutes");
                }

                if (currentUsage >= limit * 60) {
                    showBlockingOverlay();
                    startOverlayMonitoring();
                    return;
                }
            } else {
                if (DEBUG) Log.v(TAG, "No restricted apps in foreground");
            }
            timerHandler.postDelayed(this, 1000);
        }
    };

    private Runnable overlayCheck = new Runnable() {
        @Override
        public void run() {
            if (restrictedApps.contains(currentForegroundApp)) {
                showBlockingOverlay();
            }
            overlayMonitor.postDelayed(this, 100); // Check every 100ms
        }
    };

    private void startOverlayMonitoring() {
        overlayMonitor.post(overlayCheck);
    }
    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadRestrictions();
        startMonitoringService();

        timerHandler.post(timerRunnable);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                loadRestrictions();
                handler.postDelayed(this, 60000);
            }
        }, 60000);

        Log.d(TAG, "AppBlockerAccessibilityService created");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String newApp = event.getPackageName().toString();

            if (!newApp.equals(currentForegroundApp)) {
                lastUsedMap.put(newApp, System.currentTimeMillis());
            }

            currentForegroundApp = newApp;

            checkAndBlockIfRestricted(currentForegroundApp);
        }
    }

    private ActivityInfo tryGetActivity(ComponentName componentName) {
        try {
            return getPackageManager().getActivityInfo(componentName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private String getCurrentForegroundApp() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (!tasks.isEmpty()) {
                ComponentName topActivity = tasks.get(0).topActivity;
                return topActivity != null ? topActivity.getPackageName() : "";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting foreground app", e);
        }
        return "";
    }

    private boolean isAppRestricted(String packageName) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> restrictedApps = prefs.getStringSet("restricted_apps", new HashSet<>());
        return restrictedApps.contains(packageName);
    }

    private void checkAndBlockIfRestricted(String packageName) {
       if (!isAppRestricted(packageName)) return;

       long currentUsage = appUsageTimes.getOrDefault(packageName, 0L);
       int limit = appLimits.getOrDefault(packageName, 1);

       if (currentUsage >= limit * 60) {
            showBlockingOverlay();
            startOverlayMonitoring();
       }
    }

    private void removeOverlayIfPresent() {
        try {
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (overlayView != null && overlayView.getParent() != null) {
                windowManager.removeView(overlayView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing overlay", e);
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

    private void checkAndBlockRestrictedApp() {
        if (restrictedApps.contains(currentForegroundApp)) {
            showBlockingOverlay();
            // Force immediate overlay display
            new Handler(Looper.getMainLooper()).post(() -> {
                if (overlayView != null && overlayView.getParent() == null) {
                    showBlockingOverlay();
                }
            });
        }
    }

    private void showBlockingOverlay() {
        if (overlayView != null) return;

        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        // Use the new createOverlayParams method
        WindowManager.LayoutParams params = createOverlayParams();
        params.gravity = Gravity.CENTER;

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_app_blocked, null);

        Button btnGoHome = overlayView.findViewById(R.id.btn_go_home);
        Button btnManageRestrictions = overlayView.findViewById(R.id.btn_manage_restrictions);

        btnGoHome.setOnClickListener(v -> {
            windowManager.removeView(overlayView);
            overlayView = null;

            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
        });

        btnManageRestrictions.setOnClickListener(v -> {
            windowManager.removeView(overlayView);
            Intent settingsIntent = new Intent(this, MainActivity.class);
            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(settingsIntent);
        });

        windowManager.addView(overlayView, params);
    }

    private WindowManager.LayoutParams createOverlayParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;
        params.format = PixelFormat.TRANSLUCENT;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        return params;
    }


    @Override
    public void onInterrupt() {
        Log.d(TAG, "AppBlockerAccessibilityService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        timerHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "AppBlockerAccessibilityService destroyed");
    }
}