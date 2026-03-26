package com.example.edulock.ui.acitvity;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.edulock.R;
import com.example.edulock.service.AppBlockerAccessibilityService;
import com.example.edulock.utils.SoundManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

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

        MaterialCardView card = findViewById(R.id.bottomCanvas);
        ImageView phone = findViewById(R.id.phoneImage);
        TextView title = findViewById(R.id.accessAppUsage);

        phone.setAlpha(0f);
        phone.setTranslationY(-100f);

        card.setAlpha(0f);
        card.setTranslationY(300f);

        title.setAlpha(0f);

        button.setAlpha(0f);
        button.setScaleX(0.8f);
        button.setScaleY(0.8f);

        //Phone Animation change if you want
        phone.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(500)
                .setDuration(1000)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        //Dark Background Animation change if you want
        card.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(200)
                .setDuration(700)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        //Title change if you want
        title.animate()
                .alpha(1f)
                .setStartDelay(400)
                .setDuration(500)
                .start();

        button.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(600)
                .setDuration(400)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private boolean areAllPermissionGranted() {
        return Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled();
    }


    protected void onResume(){
        super.onResume();

        if(areAllPermissionGranted()) {
            button.setText("Next");

            button.animate()
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(200)
                    .withEndAction(() ->
                            button.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(200)
                            );
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
        SoundManager.playButtonSound(this);
        startActivity(intent);
    }
}
