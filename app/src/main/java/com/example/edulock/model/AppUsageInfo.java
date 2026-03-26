package com.example.edulock.model;

public class AppUsageInfo {
    private String appName;
    private String packageName;
    private long usageTime;
    private int color;

    public AppUsageInfo(String appName, long usageTime, String packageName) {
            this.appName = appName;
        this.usageTime = usageTime;
        this.packageName = packageName;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public long getUsageTime() {
        return usageTime;
    }

    public void addUsageTime(long time) {
        this.usageTime += time;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }
}