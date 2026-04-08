package com.example.edulock.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import androidx.core.app.NotificationCompat;

import com.example.edulock.R;

/**
 * Shows blocking overlay as a system window (not an activity)
 * This ensures it appears on top of the blocked app, not in EduLock
 */
public class OverlayBlockService extends Service {
    private static final String TAG = "OverlayBlockService";
    private static final int NOTIFICATION_ID = 456;
    private static final String CHANNEL_ID = "OverlayChannel";

    private WindowManager windowManager;
    private View overlayView;
    private String blockedPackage;
    private WindowManager.LayoutParams params;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");

        if (intent == null) {
            return START_STICKY;
        }

        blockedPackage = intent.getStringExtra("package_name");
        Log.d(TAG, "🎬 Showing overlay for: " + blockedPackage);

        // ✅ Create notification for foreground service
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        if (overlayView == null) {
            showOverlay();
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "App Blocking Overlay",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows blocking overlay for restricted apps");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("EduLock")
                .setContentText("App blocking active")
                .setSmallIcon(R.drawable.ic_timer)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void showOverlay() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

            // Create overlay view
            LayoutInflater inflater = LayoutInflater.from(this);
            overlayView = inflater.inflate(R.layout.overlay_app_blocked, null);

            // Setup button - CRITICAL: Make it clickable
            Button goHomeBtn = overlayView.findViewById(R.id.btn_go_home);
            if (goHomeBtn != null) {
                goHomeBtn.setOnClickListener(v -> {
                    Log.d(TAG, "✅ Go home button clicked");
                    hideOverlay();
                });
                Log.d(TAG, "✅ Button click listener set");
            } else {
                Log.e(TAG, "❌ Button not found!");
            }

            // Create window params - MUST BE TOUCHABLE
            params = new WindowManager.LayoutParams();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }

            params.format = PixelFormat.TRANSLUCENT;
            // ✅ CRITICAL: Remove FLAG_NOT_TOUCHABLE so touches go through
            params.flags = WindowManager.LayoutParams.FLAG_FULLSCREEN;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.TOP | Gravity.LEFT;

            // Add view to window
            windowManager.addView(overlayView, params);
            Log.d(TAG, "✅ Overlay added to window manager");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error showing overlay: " + e.getMessage(), e);
            stopSelf();
        }
    }

    private void hideOverlay() {
        try {
            Log.d(TAG, "🏠 Hiding overlay and going to home");

            if (overlayView != null && windowManager != null) {
                windowManager.removeView(overlayView);
                overlayView = null;
                Log.d(TAG, "✅ Overlay removed from window manager");
            }

            // Send to home
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(homeIntent);

            Log.d(TAG, "✅ Sent to home screen");

            // Stop this service
            stopForeground(true);
            stopSelf();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error hiding overlay: " + e.getMessage(), e);
            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🔴 Service destroyed");
        hideOverlay();
    }
}