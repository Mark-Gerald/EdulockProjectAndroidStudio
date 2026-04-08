package com.example.edulock.manager;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.edulock.ui.acitvity.TimeLimitBlockedActivity;

/**
 * Manages app blocking overlay display
 *
 * Responsibilities:
 * - Show blocking overlay when time limit exceeded
 * - Handle app orientation detection
 * - Prevent dismissal of overlay
 */
public class OverlayManager {
    private static final String TAG = "OverlayManager";
    private static final long OVERLAY_DELAY_MS = 300;

    private Context context;
    private volatile boolean isOverlayShowing = false;

    public OverlayManager(Context context) {
        this.context = context;
    }

    /**
     * Show blocking overlay for restricted app
     * @param packageName Package name of the blocked app
     */
    public void showBlockingOverlay(String packageName) {
        if (isOverlayShowing) {
            Log.d(TAG, "⏭️  Overlay already showing, skipping");
            return;
        }

        isOverlayShowing = true;

        new Thread(() -> {
            try {
                // Small delay to ensure app is in foreground
                Thread.sleep(OVERLAY_DELAY_MS);

                Intent overlayIntent = new Intent(context, TimeLimitBlockedActivity.class);
                overlayIntent.putExtra("package_name", packageName);
                overlayIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                Intent.FLAG_ACTIVITY_NO_ANIMATION |
                                Intent.FLAG_ACTIVITY_SINGLE_TOP);

                context.startActivity(overlayIntent);
                Log.d(TAG, "✅ Overlay shown for: " + packageName);

            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to show overlay: " + e.getMessage(), e);
                isOverlayShowing = false;
            }
        }).start();
    }
}