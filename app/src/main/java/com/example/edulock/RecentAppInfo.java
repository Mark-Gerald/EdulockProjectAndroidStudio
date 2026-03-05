package com.example.edulock;

import android.graphics.drawable.Drawable;

public class RecentAppInfo {
    private String packageName;
    private String appName;
    private long usageTime;
    private Drawable appIcon;

    public RecentAppInfo(String packageName, String appName, long usageTime, Drawable appIcon) {
        this.packageName = packageName;
        this.appName = appName;
        this.usageTime = usageTime;
        this.appIcon = appIcon;
    }

    public String getPackageName() { return packageName; }
    public String getAppName() { return appName; }
    public long getUsageTime() { return usageTime; }
    public Drawable getAppIcon() { return appIcon; }
}