package com.example.edulock.ui.acitvity;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.Button;
import android.util.Log;
import android.view.KeyEvent;

import androidx.appcompat.app.AppCompatActivity;

import com.example.edulock.R;

public class TimeLimitBlockedActivity extends AppCompatActivity {
    private static final String TAG = "TimeLimitBlockedActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "🔒 Overlay activity created");

        // Set layout based on orientation
        setContentViewForOrientation();

        setupHomeButton();
    }

    private void setContentViewForOrientation() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d(TAG, "Setting LANDSCAPE layout");
            setContentView(R.layout.overlay_app_blocked_land);
        } else {
            Log.d(TAG, "Setting PORTRAIT layout");
            setContentView(R.layout.overlay_app_blocked);
        }
    }

    private void setupHomeButton() {
        try {
            Button goHomeBtn = findViewById(R.id.btn_go_home);
            if (goHomeBtn != null) {
                goHomeBtn.setOnClickListener(v -> {
                    Log.d(TAG, "✅ Go home button clicked");
                    sendToHome();
                });
            } else {
                Log.e(TAG, "❌ Button not found!");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error setting up button: " + e.getMessage(), e);
        }
    }

    private void sendToHome() {
        Log.d(TAG, "🏠 Sending to home screen...");

        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(homeIntent);

        finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "Configuration changed - updating layout");

        setContentViewForOrientation();
        setupHomeButton();
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "Back pressed - sending to home");
        // Send to home instead of just blocking back
        sendToHome();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            Log.d(TAG, "Home key pressed");
            // Let it go to home
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "⏸️ Activity paused");

        // If app tries to leave overlay, bring it back to front
        if (!isFinishing()) {
            Log.d(TAG, "Bringing overlay back to front");
            Intent intent = new Intent(this, TimeLimitBlockedActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        }
    }
}