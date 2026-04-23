package com.example.edulock.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.edulock.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

/**
 * Foreground service that keeps a Firebase listener alive so the controller
 * can block / unblock this device even after the user swipes EduLock away
 * from recents.
 *
 * Three big fixes vs the previous version:
 *   1) Listens at registered_devices/{connection_code} instead of /{ANDROID_ID}.
 *      The connection_code is the UUID written by RestrictFragment when the
 *      QR is generated, and is persisted in SharedPreferences "EduLock".
 *
 *   2) Shows the overlay whenever isBlocked == true, regardless of
 *      controllerConnected. Previously the overlay was gated on
 *      controllerConnected, but Firebase's onDisconnect flips that to false
 *      the moment the app is killed — meaning the overlay was *guaranteed*
 *      not to show in exactly the case we care about.
 *
 *   3) onDestroy() no longer writes controllerConnected=false / isBlocked=false.
 *      Those wipes are why a blocked phone "forgot" it was blocked the next
 *      time the service restarted. The block state now persists until the
 *      teacher unblocks or disconnects.
 *
 * Plus: writes a server-side lastHeartbeat every 15 s so the website can
 * detect "phone shut down / no signal" within ~35 s instead of waiting on
 * Firebase's slow onDisconnect.
 */
public class BlockOverlayService extends Service {

    private static final String TAG               = "BlockOverlayService";
    private static final int    NOTIFICATION_ID   = 1;
    private static final String CHANNEL_ID        = "BlockOverlayChannel";
    private static final long   HEARTBEAT_MS      = 10_000L;

    private static final String PREFS_NAME        = "EduLock";
    private static final String KEY_CONN_CODE     = "connection_code";

    private WindowManager windowManager;
    private View overlayView;
    private View systemUIOverlay;
    private boolean isOverlayShown = false;

    private DatabaseReference deviceRef;
    private ValueEventListener blockListener;
    private String connectionCode;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (deviceRef != null) {
                deviceRef.child("lastHeartbeat").setValue(ServerValue.TIMESTAMP);
            }
            handler.postDelayed(this, HEARTBEAT_MS);
        }
    };

    // ── Lifecycle ─────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("EduLock is monitoring this device"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        startForeground(NOTIFICATION_ID, createNotification("EduLock is monitoring this device"));

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String code = prefs.getString(KEY_CONN_CODE, null);

        if (code == null) {
            Log.i(TAG, "No connection_code saved — stopping service.");
            stopSelf();
            return START_NOT_STICKY;
        }

        // (Re)attach listener if the code changed.
        if (!code.equals(connectionCode)) {
            detachListener();
            connectionCode = code;
            attachListener(code);
        }

        // (Re)start heartbeat.
        handler.removeCallbacks(heartbeat);
        handler.post(heartbeat);

        // Manual SHOW_OVERLAY override (kept for backward compatibility).
        if (intent != null && intent.hasExtra("SHOW_OVERLAY")) {
            boolean show = intent.getBooleanExtra("SHOW_OVERLAY", false);
            if (show && !isOverlayShown) showBlockOverlay();
            else if (!show && isOverlayShown) hideBlockOverlay();
        }

        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // User swiped EduLock from recents — re-issue start so we keep
        // running. START_STICKY alone is unreliable on some OEM ROMs.
        Intent restart = new Intent(getApplicationContext(), BlockOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplicationContext().startForegroundService(restart);
        } else {
            getApplicationContext().startService(restart);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        handler.removeCallbacks(heartbeat);
        detachListener();
        hideBlockOverlay();
        // IMPORTANT: do NOT write controllerConnected=false or isBlocked=false here.
        // Firebase's onDisconnect already handles controllerConnected, and
        // wiping isBlocked would let a blocked device escape on a momentary
        // service restart.
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── Firebase wiring ───────────────────────────────────────────────

    private void attachListener(final String code) {
        deviceRef = FirebaseDatabase.getInstance()
                .getReference("registered_devices").child(code);
        deviceRef.keepSynced(true);

        // Re-arm onDisconnect — Firebase clears these after they fire once.
        deviceRef.child("controllerConnected").onDisconnect().setValue(false);

        // Mark ourselves online again now that the service is alive.
        // (After a kill, controllerConnected was flipped to false by onDisconnect.)
        deviceRef.child("controllerConnected").setValue(true);

        blockListener = deviceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Teacher disconnected this device on the controller.
                    Log.i(TAG, "Device node removed; clearing saved code and stopping.");
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit().remove(KEY_CONN_CODE).apply();
                    hideBlockOverlay();
                    stopSelf();
                    return;
                }

                Boolean blocked = snapshot.child("isBlocked").getValue(Boolean.class);
                Log.d(TAG, "isBlocked=" + blocked);

                // Show whenever blocked is true, regardless of controllerConnected.
                // The teacher's intent is "this device must be blocked"; we honor
                // that even if our socket has briefly dropped.
                if (Boolean.TRUE.equals(blocked)) {
                    showBlockOverlay();
                    updateNotification("Device is currently blocked");
                } else {
                    hideBlockOverlay();
                    updateNotification("EduLock is monitoring this device");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Listener cancelled: " + error.getMessage());
            }
        });
    }

    private void detachListener() {
        if (deviceRef != null && blockListener != null) {
            deviceRef.removeEventListener(blockListener);
        }
        blockListener = null;
        deviceRef = null;
    }

    // ── Overlay ───────────────────────────────────────────────────────

    private void showBlockOverlay() {
        if (isOverlayShown || overlayView != null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW not granted.");
            return;
        }

        try {
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_block, null);
            if (overlayView == null) return;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_FULLSCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN
                            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                            | WindowManager.LayoutParams.FLAG_SECURE,
                    PixelFormat.OPAQUE);
            params.gravity = Gravity.FILL;

            // Status bar / system UI mask
            systemUIOverlay = new View(this);
            systemUIOverlay.setBackgroundColor(0xFF000000);
            WindowManager.LayoutParams systemUIParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.OPAQUE);
            systemUIParams.gravity = Gravity.TOP;
            systemUIParams.height = 200;

            overlayView.setBackgroundColor(0xFF000000);
            TextView messageTextView = overlayView.findViewById(R.id.overlayMessageTextView);
            if (messageTextView != null) {
                messageTextView.setText(
                        "Your teacher has temporarily restricted access to help you stay focused on your lesson. "
                                + "You can use this device again after class. \n\nThanks for understanding!");
                messageTextView.setTextColor(0xFFFFFFFF);
                messageTextView.setTextSize(18);
            }

            windowManager.addView(systemUIOverlay, systemUIParams);
            windowManager.addView(overlayView, params);
            isOverlayShown = true;
        } catch (Exception e) {
            Log.e(TAG, "Error showing overlay", e);
            hideBlockOverlay();
        }
    }

    private void hideBlockOverlay() {
        if (systemUIOverlay != null) {
            try { windowManager.removeView(systemUIOverlay); }
            catch (Exception e) { Log.e(TAG, "remove systemUIOverlay", e); }
            systemUIOverlay = null;
        }
        if (overlayView != null) {
            try { windowManager.removeView(overlayView); }
            catch (Exception e) { Log.e(TAG, "remove overlay", e); }
            overlayView = null;
        }
        isOverlayShown = false;
    }

    // ── Notification helpers ──────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Block Overlay Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Used when device is being controlled");
            NotificationManager mgr = getSystemService(NotificationManager.class);
            if (mgr != null) mgr.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("EduLock Active")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager mgr = getSystemService(NotificationManager.class);
        if (mgr != null) mgr.notify(NOTIFICATION_ID, createNotification(text));
    }

    // ── Public start/stop helpers ─────────────────────────────────────

    public static void start(Context ctx) {
        Intent i = new Intent(ctx.getApplicationContext(), BlockOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.getApplicationContext().startForegroundService(i);
        } else {
            ctx.getApplicationContext().startService(i);
        }
    }

    public static void stop(Context ctx) {
        ctx.getApplicationContext().stopService(
                new Intent(ctx.getApplicationContext(), BlockOverlayService.class));
    }
}
