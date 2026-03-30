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
    private static final String EDULOCK_PACKAGE = "com.example.edulock";

    private RecyclerView appUsageRecyclerView;
    private RecyclerView recentAppsRecyclerView;
    private EditText searchApps;
    private TextView totalScreenTime;
    private TextView emptyStateMessage;  // 🔥 NEW: Empty state message

    final private List<AppUsageInfo> appUsageList = new ArrayList<>();
    final private List<RecentAppInfo> recentAppsList = new ArrayList<>();

    private AppUsageAdapter appUsageAdapter;
    private RecentAppsAdapter recentAppsAdapter;
    private PackageManager packageManager;

    // 🔥 Prevent duplicate loads
    private boolean isLoadingStats = false;

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
        emptyStateMessage = view.findViewById(R.id.emptyStateMessage);  // 🔥 NEW: Find empty state TextView

        // 🔥 NEW: Initially hide empty state message
        if (emptyStateMessage != null) {
            emptyStateMessage.setVisibility(View.GONE);
        }
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
            // 🔥 Prevent duplicate loads at the same time
            if (isLoadingStats) {
                Log.d(TAG, "Already loading stats, skipping duplicate call");
                return;
            }
            isLoadingStats = true;

            Log.d(TAG, "Starting to load all usage stats...");

            // Use centralized calculator - SAME source as notifications!
            Map<String, Long> appUsageMap = UsageTimeCalculator.getAppUsageToday(requireContext());
            Log.d(TAG, "Got appUsageMap with " + appUsageMap.size() + " apps");

            // Get total screen time
            long totalTime = UsageTimeCalculator.getTotalScreenTimeToday(requireContext());
            Log.d(TAG, "Total screen time: " + UsageTimeCalculator.formatTime(totalTime));

            // 🔥 GET RECENT ACTIVITY TIMESTAMPS
            Log.d(TAG, "About to call getRecentActivityTimestamps...");
            Map<String, Long> lastUsedMap = getRecentActivityTimestamps(appUsageMap.keySet());
            Log.d(TAG, "Returned from getRecentActivityTimestamps with " + lastUsedMap.size() + " apps");

            // Process and display everything
            processAndDisplayUsage(appUsageMap, lastUsedMap, totalTime);

            isLoadingStats = false;
            Log.d(TAG, "Finished loading stats");

        } catch (Exception e) {
            Log.e(TAG, "Error loading usage stats", e);
            e.printStackTrace();  // 🔥 NEW: Print full stack trace
            isLoadingStats = false;
        }
    }

    /**
     * GET RECENT ACTIVITY TIMESTAMPS - Find when each app was last used
     *
     * This tracks ACTIVITY_RESUMED events to find the most recent time each app was opened
     * Only includes apps that appear in the provided app list (and NOT system apps)
     *
     * @param appPackages List of app package names to track (from Screen Time dashboard)
     * @return Map of package name -> last used timestamp
     */
    private Map<String, Long> getRecentActivityTimestamps(java.util.Set<String> appPackages) {
        Log.d(TAG, "getRecentActivityTimestamps called with " + appPackages.size() + " packages");

        if (appPackages == null || appPackages.isEmpty()) {
            Log.d(TAG, "No apps to track for recent activities");
            return new HashMap<>();
        }

        try {
            UsageStatsManager usm = (UsageStatsManager) requireContext()
                    .getSystemService(Context.USAGE_STATS_SERVICE);

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            long startTime = calendar.getTimeInMillis();
            long endTime = System.currentTimeMillis();

            Log.d(TAG, "Querying events from " + startTime + " to " + endTime);
            UsageEvents events = usm.queryEvents(startTime, endTime);

            // Map to store: app package name -> last time it was used (ACTIVITY_RESUMED)
            Map<String, Long> lastUsedMap = new HashMap<>();

            UsageEvents.Event event = new UsageEvents.Event();
            int eventCount = 0;

            // Loop through events and track when each app was last RESUMED (opened)
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                eventCount++;

                // Only track ACTIVITY_RESUMED (when user opens app)
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    String packageName = event.getPackageName();

                    // ✅ Three conditions:
                    // 1. App is in our app usage list (from dashboard)
                    // 2. NOT EduLock itself
                    // 3. NOT a system app
                    if (appPackages.contains(packageName)
                            && !packageName.equals(EDULOCK_PACKAGE)
                            && !isSystemApp(packageName)) {

                        lastUsedMap.put(packageName, event.getTimeStamp());
                        Log.d(TAG, "Tracked: " + packageName + " at " + event.getTimeStamp());
                    }
                }
            }

            Log.d(TAG, "Processed " + eventCount + " total events");
            Log.d(TAG, "Found " + lastUsedMap.size() + " recent activities (excluding EduLock and system apps)");
            return lastUsedMap;

        } catch (Exception e) {
            Log.e(TAG, "Error in getRecentActivityTimestamps", e);
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    /**
     * CHECK IF APP IS SYSTEM APP - Determines if app is system-level
     */
    private boolean isSystemApp(String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            // System apps have FLAG_SYSTEM set
            boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            return isSystemApp;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * PROCESS AND DISPLAY - Update UI with stats
     *
     * NOW: appUsageMap contains the same apps as Screen Time dashboard!
     * lastUsedMap contains when each app was last opened
     */
    private void processAndDisplayUsage(Map<String, Long> appUsageMap, Map<String, Long> lastUsedMap, long totalTime) {
        Log.d(TAG, "processAndDisplayUsage called");

        List<AppUsageInfo> appList = new ArrayList<>();

        // Process app usage list (SAME as Screen Time dashboard)
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
        Log.d(TAG, "App list has " + appList.size() + " apps after filtering");

        // Process recent activities list using SAME apps as dashboard
        List<RecentAppInfo> recentList = new ArrayList<>();

        for (Map.Entry<String, Long> entry : lastUsedMap.entrySet()) {
            try {
                String packageName = entry.getKey();
                long lastUsedTimestamp = entry.getValue();

                // Skip EduLock itself (double check)
                if (packageName.equals(EDULOCK_PACKAGE)) {
                    continue;
                }

                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                String appName = packageManager.getApplicationLabel(appInfo).toString();

                recentList.add(new RecentAppInfo(
                        packageName,
                        appName,
                        lastUsedTimestamp, // Timestamp when app was last opened
                        packageManager.getApplicationIcon(appInfo)
                ));

                Log.d(TAG, "Added to recent: " + appName + " (last used: " + lastUsedTimestamp + ")");

            } catch (Exception e) {
                Log.e(TAG, "Error processing recent app", e);
            }
        }

        // Sort by most recent first (highest timestamp = most recent)
        Collections.sort(recentList, (a, b) ->
                Long.compare(b.getUsageTime(), a.getUsageTime()));

        Log.d(TAG, "Recent activities: " + recentList.size() + " apps");

        // Update UI on main thread
        requireActivity().runOnUiThread(() -> {
            totalScreenTime.setText(UsageTimeCalculator.formatTime(totalTime));

            appUsageList.clear();
            appUsageList.addAll(appList);
            appUsageAdapter.notifyDataSetChanged();

            recentAppsList.clear();
            recentAppsList.addAll(recentList);
            recentAppsAdapter.notifyDataSetChanged();

            // 🔥 NEW: Show/hide empty state message based on whether there are recent apps
            if (recentList.isEmpty()) {
                // No recent activities - show empty state message
                searchApps.setVisibility(View.GONE);
                recentAppsRecyclerView.setVisibility(View.GONE);
                if (emptyStateMessage != null) {
                    emptyStateMessage.setVisibility(View.VISIBLE);
                    emptyStateMessage.setText("No Apps Currently in use.");
                }
                Log.d(TAG, "Showing empty state message");
            } else {
                // Has recent activities - show search and list
                searchApps.setVisibility(View.VISIBLE);
                recentAppsRecyclerView.setVisibility(View.VISIBLE);
                if (emptyStateMessage != null) {
                    emptyStateMessage.setVisibility(View.GONE);
                }
                Log.d(TAG, "Showing recent activities list");
            }

            Log.d(TAG, "Updated UI: " + appList.size() + " apps, " + recentList.size() + " recent (EduLock excluded)");
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
            // Add delay to prevent race condition with onCreateView()
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (!isLoadingStats) {  // Only load if not already loading
                    loadAllUsageStats();
                }
            }, 1000); // Wait 1 second before loading
        }
    }
}