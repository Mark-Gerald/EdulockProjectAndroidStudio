package com.example.edulock;

import android.app.Application;
import com.google.firebase.FirebaseApp;

public class EduLockApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this);
        }
    }
}
