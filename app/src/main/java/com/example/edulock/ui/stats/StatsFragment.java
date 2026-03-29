package com.example.edulock.ui.stats;

import com.example.edulock.utils.UsageTimeCalculator;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.edulock.R;
import com.example.edulock.model.AppUsageInfo;
import com.example.edulock.model.RecentAppInfo;
import com.example.edulock.receiver.UsageStatsResetReceiver;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * STATS FRAGMENT - Shows screen time statistics
 *
 * What it displays:
 * - Total screen time for today
 * - List of apps sorted by usage time (most used first)
 * - Recent activities (last apps used)
 */
public class StatsFragment extends Fragment {
    private static final String TAG = "StatsFragment";

    private RecyclerView appUsageRecyclerView;
    private RecyclerView recentAppsRecyclerView;
    private EditText searchApps;
    private TextView totalScreenTime;

    final private List<AppUsageInfo> appUsageList = new ArrayList<>();
    final private List<RecentAppInfo> recentAppsList = new ArrayList<>();

    private AppUsageAdapter appUsageAdapter;
    private RecentAppsAdapter recentAppsAdapter;
    private PackageManager packageManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stats, container, false);
        packageManager = requireContext().getPackageManager();

        initializeViews(view);
        setupAdapters();
        setupSearchListener();
        scheduleDailyReset();

        if (checkUsageStatsPermission()) {
            loadAllUsageStats();
        } else {
            requestUsageStatsPermission();
        }

        return view;
    }

    private void initializeViews(View view) {
        appUsageRecyclerView = view.findViewById(R.id.appUsageRecyclerView);
        recentAppsRecyclerView = view.findViewById(R.id.recentAppsRecyclerView);
        searchApps = view.findViewById(R.id.searchApps);
        totalScreenTime = view.findViewById(R.id.totalScreenTime);
    }

    private void setupAdapters() {
        // Setup app usage adapter with vertical scrolling
        appUsageAdapter = new AppUsageAdapter(appUsageList);
        appUsageRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        appUsageRecyclerView.setAdapter(appUsageAdapter);

        // Setup recent apps adapter with vertical scrolling
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
                filterRecentApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

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

    /**
     * LOAD ALL USAGE STATS - Main method to get app usage
     */
    private void loadAllUsageStats() {
        try {
            // Use centralized calculator - SAME as notifications!
            Map<String, Long> appUsageMap = UsageTimeCalculator.getAppUsageToday(requireContext());

            // Get total screen time
            long totalTime = UsageTimeCalculator.getTotalScreenTimeToday(requireContext());

            // Get recent activities (last apps used)
            Map<String, Long> lastUsedMap = getRecentActivities();

            // Process and display everything
            processAndDisplayUsage(appUsageMap, lastUsedMap, totalTime);

        } catch (Exception e) {
            Log.e(TAG, "Error loading usage stats", e);
        }
    }

    /**
     * GET RECENT ACTIVITIES - Track which apps were used most recently
     *
     * This uses UsageEvents to find ACTIVITY_RESUMED events
     * (when user opens an app) to determine which apps they used recently
     */
    private Map<String, Long> getRecentActivities() {
        UsageStatsManager usm = (UsageStatsManager) requireContext()
                .getSystemService(Context.USAGE_STATS_SERVICE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        UsageEvents events = usm.queryEvents(startTime, endTime);

        // Map to store: app package name -> last time it was used
        Map<String, Long> lastUsedMap = new HashMap<>();

        UsageEvents.Event event = new UsageEvents.Event();

        // Loop through events and track when each app was last RESUMED (opened)
        while (events.hasNextEvent()) {
            events.getNextEvent(event);

            // Only track ACTIVITY_RESUMED (when user opens app)
            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                String packageName = event.getPackageName();

                // Only track user apps, not system apps
                if (isUserApp(packageName)) {
                    lastUsedMap.put(packageName, event.getTimeStamp());
                }
            }
        }

        return lastUsedMap;
    }

    /**
     * CHECK IF APP IS USER APP - Filters out system apps
     */
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

    /**
     * PROCESS AND DISPLAY - Update UI with stats
     */
    private void processAndDisplayUsage(Map<String, Long> appUsageMap, Map<String, Long> lastUsedMap, long totalTime) {
        List<AppUsageInfo> appList = new ArrayList<>();

        // Process app usage list
        for (Map.Entry<String, Long> entry : appUsageMap.entrySet()) {
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(entry.getKey(), 0);
                String appName = packageManager.getApplicationLabel(appInfo).toString();

                long usageTime = entry.getValue();

                // Skip apps with less than 1 minute usage
                if (usageTime < 60000) continue;

                appList.add(new AppUsageInfo(appName, usageTime, entry.getKey()));

            } catch (Exception e) {
                Log.e(TAG, "Error processing app", e);
            }
        }

        // Sort by most used first
        appList.sort((a, b) -> Long.compare(b.getUsageTime(), a.getUsageTime()));

        // Process recent activities list
        List<RecentAppInfo> recentList = new ArrayList<>();
        for (Map.Entry<String, Long> entry : lastUsedMap.entrySet()) {
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(entry.getKey(), 0);
                String appName = packageManager.getApplicationLabel(appInfo).toString();

                recentList.add(new RecentAppInfo(
                        entry.getKey(),
                        appName,
                        entry.getValue(), // Last used timestamp
                        packageManager.getApplicationIcon(appInfo)
                ));

            } catch (Exception e) {
                Log.e(TAG, "Error processing recent app", e);
            }
        }

        // Sort by most recent first
        Collections.sort(recentList, (a, b) ->
                Long.compare(b.getUsageTime(), a.getUsageTime()));

        // Update UI on main thread
        requireActivity().runOnUiThread(() -> {
            totalScreenTime.setText(UsageTimeCalculator.formatTime(totalTime));

            appUsageList.clear();
            appUsageList.addAll(appList);
            appUsageAdapter.notifyDataSetChanged();

            recentAppsList.clear();
            recentAppsList.addAll(recentList);
            recentAppsAdapter.notifyDataSetChanged();

            Log.d(TAG, "Updated UI: " + appList.size() + " apps, " + recentList.size() + " recent");
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

    /**
     * REFRESH STATS - Called when fragment becomes visible
     *
     * Reloads the stats so recent data appears immediately
     */
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "Fragment resumed - refreshing stats");

        if (checkUsageStatsPermission()) {
            // Add small delay to ensure system has time to record events
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                loadAllUsageStats();
            }, 500); // Wait 500ms before loading
        }
    }
}