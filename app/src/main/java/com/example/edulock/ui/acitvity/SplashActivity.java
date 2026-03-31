package com.example.edulock.ui.acitvity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.example.edulock.utils.AuthStateManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DISPLAY_LENGTH = 1000; // 1 second
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        AuthStateManager authStateManager = new AuthStateManager(this);
        FirebaseUser currentUser = auth.getCurrentUser();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent;

            // User is logged in - go to dashboard
            if (currentUser != null) {
                intent = new Intent(SplashActivity.this, statistics_usage_data.class);
            }
            // Welcome not completed - go to welcome
            else if (!authStateManager.isWelcomeCompleted()) {
                intent = new Intent(SplashActivity.this, MainActivity.class);
            }
            // Welcome completed, not logged in - go to login
            else {
                intent = new Intent(SplashActivity.this, login_register.class);
            }

            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        }, SPLASH_DISPLAY_LENGTH);
    }
}