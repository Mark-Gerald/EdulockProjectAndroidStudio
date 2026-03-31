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
}
