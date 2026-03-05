package com.example.edulock;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.NonNull;

import androidx.annotation.Nullable;

public class BlockOverlayService extends Service {
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "BlockOverlayChannel";

    private WindowManager windowManager;
    private View overlayView;
    private boolean isOverlayShown = false;

    private DatabaseReference database;
    private ValueEventListener blockListener;

    private View systemUIOverlay;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("BlockOverlayService", "Service onCreate called");
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();

        // Start foreground immediately
        startForeground(NOTIFICATION_ID, createNotification());

        // Add Firebase initialization
        database = FirebaseDatabase.getInstance().getReference();
        Log.d("BlockOverlayService", "Firebase database initialized");
        setupBlockListener();
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("EduLock Active")
                .setContentText("Device control is active")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build();
    }

    private void setupBlockListener() {
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        Log.d("BlockOverlayService", "Setting up listener for device: " + deviceId);

        // Update the database path to match
        DatabaseReference deviceRef = database.child("registered_devices").child(deviceId);

        // Listen for both block status and controller connection
        blockListener = deviceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean isBlocked = snapshot.child("isBlocked").getValue(Boolean.class);
                    Boolean isConnected = snapshot.child("controllerConnected").getValue(Boolean.class);

                    Log.d("BlockOverlayService", "Block status: " + isBlocked + ", Connected: " + isConnected);

                    if (isBlocked != null && isConnected != null && isConnected) {
                        if (isBlocked) {
                            showBlockOverlay();
                        } else {
                            hideBlockOverlay();
                        }
                    } else {
                        hideBlockOverlay();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("BlockOverlayService", "Firebase listener cancelled", error.toException());
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Block Overlay Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Used when device is being controlled");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("BlockOverlayService", "onStartCommand called");

        // Ensure foreground is started immediately
        startForeground(NOTIFICATION_ID, createNotification());

        if (intent != null && intent.hasExtra("SHOW_OVERLAY")) {
            boolean showOverlay = intent.getBooleanExtra("SHOW_OVERLAY", false);
            if (showOverlay && !isOverlayShown) {
                showBlockOverlay();
            } else if (!showOverlay && isOverlayShown) {
                hideBlockOverlay();
            }
        }

        return START_STICKY;
    }

    private void showBlockOverlay() {
        if (overlayView == null) {
            try {
                overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_block, null);
                if (overlayView == null) {
                    return;
                }

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN |
                                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                                WindowManager.LayoutParams.FLAG_SECURE,
                        PixelFormat.OPAQUE);

                params.gravity = Gravity.FILL;
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                params.height = WindowManager.LayoutParams.MATCH_PARENT;

                // Create system UI overlay
                systemUIOverlay = new View(this);
                systemUIOverlay.setBackgroundColor(0xFF000000);
                WindowManager.LayoutParams systemUIParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.OPAQUE);
                systemUIParams.gravity = Gravity.TOP;
                systemUIParams.height = 200;

                overlayView.setBackgroundColor(0xFF000000);
                TextView messageTextView = overlayView.findViewById(R.id.overlayMessageTextView);
                if (messageTextView != null) {
                    messageTextView.setText("Your teacher has temporarily restricted access to help you stay focused on your lesson. You can use this device again after class. \n\nThanks for understanding!");
                    messageTextView.setTextColor(0xFFFFFFFF);
                    messageTextView.setTextSize(18);
                }

                windowManager.addView(systemUIOverlay, systemUIParams);
                windowManager.addView(overlayView, params);
                isOverlayShown = true;
            } catch (Exception e) {
                Log.e("BlockOverlayService", "Error showing overlay", e);
                hideBlockOverlay();
            }
        }
    }

    private void hideBlockOverlay() {
        if (systemUIOverlay != null) {
            try {
                windowManager.removeView(systemUIOverlay);
            } catch (Exception e) {
                Log.e("BlockOverlayService", "Error removing system UI overlay", e);
            }
            systemUIOverlay = null;
        }
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                Log.e("BlockOverlayService", "Error removing overlay", e);
            }
            overlayView = null;
        }
        isOverlayShown = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (blockListener != null && database != null) {
            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            DatabaseReference deviceRef = database.child("registered_devices").child(deviceId);
            deviceRef.removeEventListener(blockListener);
            // Reset device state on service destroy
            deviceRef.child("controllerConnected").setValue(false);
            deviceRef.child("isBlocked").setValue(false);
            blockListener = null;
        }
        hideBlockOverlay();
    }
}