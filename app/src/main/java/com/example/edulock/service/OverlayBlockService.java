package com.example.edulock.service;

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
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.edulock.R;

/**
 * Shows blocking overlay as a system window (not an activity)
 * This ensures it appears on top of the blocked app, not in EduLock
 */
public class OverlayBlockService extends Service {
    private static final String TAG = "OverlayBlockService";

    private WindowManager windowManager;
    private View overlayView;
    private String blockedPackage;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }

        blockedPackage = intent.getStringExtra("package_name");
        Log.d(TAG, "🎬 Showing overlay for: " + blockedPackage);

        if (overlayView == null) {
            showOverlay();
        }

        return START_STICKY;
    }

    private void showOverlay() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

            // Create overlay view
            LayoutInflater inflater = LayoutInflater.from(this);
            overlayView = inflater.inflate(R.layout.overlay_app_blocked, null);

            // Setup button
            Button goHomeBtn = overlayView.findViewById(R.id.btn_go_home);
            if (goHomeBtn != null) {
                goHomeBtn.setOnClickListener(v -> {
                    Log.d(TAG, "Home button clicked");
                    hideOverlay();
                });
            }

            // Create window params
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }

            params.format = PixelFormat.TRANSLUCENT;
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.TOP | Gravity.LEFT;

            // Add view to window
            windowManager.addView(overlayView, params);
            Log.d(TAG, "✅ Overlay added to window");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error showing overlay: " + e.getMessage(), e);
        }
    }

    private void hideOverlay() {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager.removeView(overlayView);
                overlayView = null;
                Log.d(TAG, "Overlay removed");
            }

            // Send to home
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);

            stopSelf();
        } catch (Exception e) {
            Log.e(TAG, "Error hiding overlay: " + e.getMessage());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideOverlay();
    }
}
