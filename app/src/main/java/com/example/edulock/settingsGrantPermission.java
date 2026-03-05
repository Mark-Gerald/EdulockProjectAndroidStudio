package com.example.edulock;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class settingsGrantPermission extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 1234;
    private MaterialButton button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_grant_permission);

        button = findViewById(R.id.grantPermissionButton);

        button.setOnClickListener(v -> {

            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
            } else if (!isAccessibilityServiceEnabled()) {
                showAccessibilityDialog();
            } else {
                goToNextPage();
            }
        });
    }

    private boolean areAllPermissionGranted() {
        return Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled();
    }


    protected void onResume(){
        super.onResume();

        if(areAllPermissionGranted()) {
            button.setText("Next");
        } else {
          button.setText("Grant Permission");
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/" +
                AppBlockerAccessibilityService.class.getCanonicalName();

        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        return enabledServices != null &&
                enabledServices.contains(serviceName);
    }

    private void showAccessibilityDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Accessibility")
                .setMessage("EduLock needs accessibility permission to function properly")
                .setPositiveButton("Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void goToNextPage() {
        Intent intent = new Intent(this, all_done_activity.class);
        startActivity(intent);
    }
}
