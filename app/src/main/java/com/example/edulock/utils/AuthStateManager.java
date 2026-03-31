package com.example.edulock.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class AuthStateManager {
    private static final String PREFS_NAME = "edulock_auth_state";
    private static final String KEY_WELCOME_COMPLETED = "welcome_completed";
    private static final String KEY_PERMISSION_GRANTED = "permission_granted";
    private static final String KEY_USER_LOGGED_IN = "user_logged_in";

    private final SharedPreferences prefs;

    public AuthStateManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void markWelcomeCompleted() {
        prefs.edit().putBoolean(KEY_WELCOME_COMPLETED, true).apply();
    }

    public boolean isWelocomeCompleted() {
        return prefs.getBoolean(KEY_WELCOME_COMPLETED, false);
    }

    public void markPermissionGranted() {
        prefs.edit().putBoolean(KEY_PERMISSION_GRANTED, true).apply();
    }

    public boolean arePermissionsGranted() {
        return prefs.getBoolean(KEY_PERMISSION_GRANTED, false);
    }

    public void markUserLoggedIn(boolean isLoggedIn) {
        prefs.edit().putBoolean(KEY_USER_LOGGED_IN, isLoggedIn).apply();
    }

    public boolean isUserLoggedIn() {
        return prefs.getBoolean(KEY_USER_LOGGED_IN, false);
    }

    public void clearAuthState() {
        prefs.edit().putBoolean(KEY_USER_LOGGED_IN, false).apply();
    }
}
