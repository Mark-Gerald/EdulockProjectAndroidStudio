package com.example.edulock.ui.acitvity;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.Button;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.edulock.R;

public class TimeLimitBlockedActivity extends AppCompatActivity {
    private static final String TAG = "TimeLimitBlockedActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Created - orientation: " + getResources().getConfiguration().orientation);

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
                    Log.d(TAG, "Go home clicked");
                    sendToHome();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up button: " + e.getMessage());
        }
    }

    private void sendToHome() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(homeIntent);
        finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "Configuration changed to: " +
                (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? "LANDSCAPE" : "PORTRAIT"));

        setContentViewForOrientation();
        setupHomeButton();
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "Back pressed - ignoring");
        // Intentionally do nothing to prevent dismissing the overlay
    }

    @Override
    public void onPause() {
        super.onPause();
        // Force return to this activity if user tries to switch away
        Log.d(TAG, "Paused - bringing back to front");
        Intent intent = new Intent(this, TimeLimitBlockedActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }
}