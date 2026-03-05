package com.example.edulock;

import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatsFragment extends Fragment {
    private static final String TAG = "StatsFragment";
    private static final int MAX_APPS_IN_PIE_CHART = 5;

    private PieChart pieChart;
    private LineChart lineChart;
    private RecyclerView appUsageRecyclerView;
    private RecyclerView recentAppsRecyclerView;
    private EditText searchApps;
    private TextView totalScreenTime;
    private List<AppUsageInfo> appUsageList = new ArrayList<>();
    private List<RecentAppInfo> recentAppsList = new ArrayList<>();
    private AppUsageAdapter appUsageAdapter;
    private RecentAppsAdapter recentAppsAdapter;
    private PackageManager packageManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stats, container, false);
        packageManager = requireContext().getPackageManager();

        initializeViews(view);
        setupCharts();
        setupAdapters();
        setupSearchListener();

        if (checkUsageStatsPermission()) {
            loadAllUsageStats();
        } else {
            requestUsageStatsPermission();
        }

        return view;
    }

    private void initializeViews(View view) {
        pieChart = view.findViewById(R.id.usagePieChart);
        lineChart = view.findViewById(R.id.usageLineChart);
        appUsageRecyclerView = view.findViewById(R.id.appUsageRecyclerView);
        recentAppsRecyclerView = view.findViewById(R.id.recentAppsRecyclerView);
        searchApps = view.findViewById(R.id.searchApps);
        totalScreenTime = view.findViewById(R.id.totalScreenTime);
    }

    private void setupCharts() {
        // Setup Pie Chart
        pieChart.getDescription().setEnabled(false);
        pieChart.setUsePercentValues(true);
        pieChart.setExtraOffsets(5, 10, 5, 5);
        pieChart.setDragDecelerationFrictionCoef(0.95f);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.TRANSPARENT); // Make hole transparent to match background
        pieChart.setTransparentCircleRadius(0f); // Remove transparent circle
        pieChart.setCenterText(""); // Remove center text

        // Disable the legend (this removes the black text)
        pieChart.getLegend().setEnabled(false);
    }

    private void setupAdapters() {
        // Setup app usage adapter with vertical scrolling
        appUsageAdapter = new AppUsageAdapter(appUsageList);
        appUsageRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        appUsageRecyclerView.setAdapter(appUsageAdapter);

        // Setup recent apps adapter with vertical scrolling (changed from horizontal)
        recentAppsAdapter = new RecentAppsAdapter(recentAppsList);
        recentAppsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recentAppsRecyclerView.setAdapter(recentAppsAdapter);
    }

    private void setupSearchListener() {
        searchApps.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Only filter the Recent Apps list, not the app usage list
                filterRecentApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // New method to filter only Recent Apps
    private void filterRecentApps(String query) {
        if (recentAppsAdapter != null) {
            recentAppsAdapter.filter(query);
        }
    }

    private boolean checkUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) requireContext().getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), requireContext().getPackageName());
        boolean granted = mode == AppOpsManager.MODE_ALLOWED;
        Log.d(TAG, "Usage Stats Permission granted: " + granted);
        return granted;
    }

    private void requestUsageStatsPermission() {
        Toast.makeText(requireContext(),
                "Please grant usage access permission for app usage statistics",
                Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    private boolean isUserInstalledApp(String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            Intent intent = packageManager.getLaunchIntentForPackage(packageName);
            boolean hasLaunchIntent = intent != null;

            Log.d("AppFilter", "Package: " + packageName +
                    " isSystemApp: " + isSystemApp +
                    " hasLaunchIntent: " + hasLaunchIntent);

            return !isSystemApp && hasLaunchIntent;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void loadAllUsageStats() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) requireContext()
                .getSystemService(Context.USAGE_STATS_SERVICE);

        if (usageStatsManager == null) {
            Log.e(TAG, "UsageStatsManager is null");
            return;
        }

        // Get midnight of current day
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        // Query for INTERVAL_DAILY to get more accurate results
        List<UsageStats> stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

        if (stats != null && !stats.isEmpty()) {
            Log.d(TAG, "Got " + stats.size() + " stats");
            processUsageStats(stats);
        } else {
            Log.e(TAG, "No usage stats retrieved");
            Toast.makeText(requireContext(),
                    "Please enable usage access for EduLock",
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean isUserApp(String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            // Check if it's not a system app and has a launch intent
            boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);

            return !isSystemApp && launchIntent != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void processUsageStats(List<UsageStats> stats) {
        Map<String, AppUsageInfo> appUsageMap = new HashMap<>();
        Map<String, RecentAppInfo> recentAppsMap = new HashMap<>();
        long totalUsageTime = 0;

        // Get today's start time (midnight)
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long dayStartTime = calendar.getTimeInMillis();

        for (UsageStats usageStats : stats) {
            String packageName = usageStats.getPackageName();

            // Calculate usage time only for today
            long lastTimeUsed = usageStats.getLastTimeUsed();
            long timeInForeground = 0;

            if (lastTimeUsed >= dayStartTime) {
                // For Android 10 (API 29) and above
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    timeInForeground = usageStats.getTotalTimeVisible();
                } else {
                    timeInForeground = usageStats.getTotalTimeInForeground();
                }
            }

            // Skip if no usage time today
            if (timeInForeground <= 0) {
                continue;
            }

            // Check if it's a user app (non-system app)
            if (isUserApp(packageName)) {
                try {
                    ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                    String appName = packageManager.getApplicationLabel(appInfo).toString();

                    // Update app usage map
                    AppUsageInfo appUsageInfo = appUsageMap.computeIfAbsent(packageName,
                            k -> {
                                AppUsageInfo info = new AppUsageInfo(appName, 0);
                                info.setPackageName(packageName);
                                return info;
                            });
                    appUsageInfo.addUsageTime(timeInForeground);

                    // Update recent apps map
                    recentAppsMap.put(packageName, new RecentAppInfo(
                            packageName,
                            appName,
                            timeInForeground,
                            packageManager.getApplicationIcon(appInfo)
                    ));

                    totalUsageTime += timeInForeground;
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, "Error getting app info for " + packageName, e);
                }
            }
        }

        // Update UI on main thread
        long finalTotalUsageTime = totalUsageTime;
        requireActivity().runOnUiThread(() -> {
            updateUIWithUsageStats(appUsageMap.values(), recentAppsMap.values(), finalTotalUsageTime);
        });
    }

    private void scheduleDailyReset() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(requireContext(), UsageStatsResetReceiver.class);
        intent.setAction("com.example.edulock.RESET_STATS");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(requireContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC, calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, pendingIntent);
        }
    }

    private void updateUIWithUsageStats(Collection<AppUsageInfo> appUsage,
                                        Collection<RecentAppInfo> recentApps,
                                        long totalTime) {
        totalScreenTime.setText(formatTime(totalTime));

        List<AppUsageInfo> sortedAppUsage = new ArrayList<>(appUsage);
        Collections.sort(sortedAppUsage, (a, b) -> Long.compare(b.getUsageTime(), a.getUsageTime()));

        updatePieChartData(sortedAppUsage);

        List<RecentAppInfo> sortedRecentApps = new ArrayList<>(recentApps);
        Collections.sort(sortedRecentApps, (a, b) -> Long.compare(b.getUsageTime(), a.getUsageTime()));

        // Clear the current list and add all sorted items
        appUsageList.clear();
        if (sortedAppUsage.size() > MAX_APPS_IN_PIE_CHART) {
            appUsageList.addAll(sortedAppUsage.subList(0, MAX_APPS_IN_PIE_CHART));
        } else {
            appUsageList.addAll(sortedAppUsage);
        }
        appUsageAdapter.notifyDataSetChanged();

        // Update recent apps with the full list
        recentAppsList.clear();
        recentAppsList.addAll(sortedRecentApps);
        recentAppsAdapter.updateData(sortedRecentApps);
    }

    private void updatePieChartData(List<AppUsageInfo> sortedAppUsage) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();
        int[] CHART_COLORS = {
                Color.rgb(76, 175, 80),    // Green
                Color.rgb(255, 193, 7),    // Yellow
                Color.rgb(255, 87, 34),    // Orange
                Color.rgb(33, 150, 243),   // Blue
                Color.rgb(156, 39, 176)    // Purple
        };

        // Take top apps for pie chart
        int appsToShow = Math.min(MAX_APPS_IN_PIE_CHART, sortedAppUsage.size());
        for (int i = 0; i < appsToShow; i++) {
            AppUsageInfo app = sortedAppUsage.get(i);
            entries.add(new PieEntry((float) app.getUsageTime(), app.getAppName()));
            int color = CHART_COLORS[i % CHART_COLORS.length];
            colors.add(color);
            app.setColor(color);
        }

        // Update pie chart
        PieDataSet dataSet = new PieDataSet(entries, "Usage Statistics");
        dataSet.setColors(colors);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);
        dataSet.setSliceSpace(2f);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format("%.1f%%", value);
            }
        });

        pieChart.setData(data);
        pieChart.invalidate();
    }

    private String formatTime(long millis) {
        long minutes = millis / 60000;
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m";
    }
}