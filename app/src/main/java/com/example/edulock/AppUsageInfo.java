package com.example.edulock;

public class AppUsageInfo {
    private String appName;
    private String packageName;  // Added field
    private long usageTime;
    private int color;

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public AppUsageInfo(String appName, long usageTime) {
        this.appName = appName;
        this.packageName = packageName;
        this.usageTime = usageTime;
    }

    public String getAppName() { return appName; }
    public String getPackageName() { return packageName; }
    public long getUsageTime() { return usageTime; }
    public void addUsageTime(long time) { this.usageTime += time; }
    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }
}