package com.example.edulock;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class settingsGrantPermission extends AppCompatActivity {

    private Button grantPermissionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_grant_permission);

        // Handle edge-to-edge layout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize the button
        grantPermissionButton = findViewById(R.id.grantPermissionButton);

        // Update button state based on permission status
        updateButtonState();

        // Button click listener
        grantPermissionButton.setOnClickListener(v -> {
            if (!hasUsageAccessPermission()) {
                // Redirect to Usage Access Settings
                Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                startActivity(intent);
            } else {
                // Navigate to the next activity
                navigateToNextActivity();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update the button state when the activity resumes
        updateButtonState();
    }

    // Check if the app has Usage Access Permission
    private boolean hasUsageAccessPermission() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            Log.e("PermissionCheck", "Error checking usage stats permission", e);
            return false;
        }
    }

    // Update the button text based on the permission status
    private void updateButtonState() {
        if (hasUsageAccessPermission()) {
            grantPermissionButton.setText("Next");
        } else {
            grantPermissionButton.setText("Grant Permission");
        }
    }

    // Navigate to the next activity
    private void navigateToNextActivity() {
        Intent intent = new Intent(this, all_done_activity.class);
        startActivity(intent);
        finish();
    }
}
