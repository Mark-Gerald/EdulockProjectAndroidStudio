package com.example.edulock.ui.stats;

import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
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
import android.app.usage.UsageStatsManager;
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

    private void loadAllUsageStats() {
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

        processUsageEvents(events);
    }

    private void processUsageEvents(UsageEvents events) {
        Map<String, Long> appUsageMap = new HashMap<>();
        Map<String, Long> appStartTime = new HashMap<>();
        Map<String, Long> lastUsedMap = new HashMap<>();

        long totalTime = 0;

        UsageEvents.Event event = new UsageEvents.Event();

        while (events.hasNextEvent()) {
            events.getNextEvent(event);

            String packageName = event.getPackageName();

            if (!isUserApp(packageName)) continue;

            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                appStartTime.put(packageName, event.getTimeStamp());

                lastUsedMap.put(packageName, event.getTimeStamp());
            }
            else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED) {
                Long start = appStartTime.get(packageName);

                if (start != null) {
                    long duration = event.getTimeStamp() - start;

                    appUsageMap.put(packageName,
                            appUsageMap.getOrDefault(packageName, 0L) + duration);

                    totalTime += duration;
                    appStartTime.remove(packageName);
                }
            }
        }

        long now = System.currentTimeMillis();
        for (String pkg : appStartTime.keySet()) {
            Long start = appStartTime.get(pkg);

            if (start != null) {
                long duration = System.currentTimeMillis() - start;

                appUsageMap.put(pkg,
                        appUsageMap.getOrDefault(pkg, 0L) + duration);

                totalTime += duration;
            }
        }

        buildFinalLists(appUsageMap, lastUsedMap, totalTime);
    }

    private void buildFinalLists(Map<String, Long> appUsageMap, Map<String, Long> lastUsedMap, long totalTime) {
        List<AppUsageInfo> appList = new ArrayList<>();

        for (Map.Entry<String, Long> entry : appUsageMap.entrySet()) {
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(entry.getKey(), 0 );
                String appName = packageManager.getApplicationLabel(appInfo).toString();

                long usageTime = entry.getValue();

                if (usageTime < 60000) continue;

                appList.add(new AppUsageInfo(
                    appName, usageTime, entry.getKey()
                ));

            } catch (Exception e) {
                Log.e(TAG, "Error processing app usage", e);
            }
        }

        appList.sort((a,b) -> Long.compare(b.getUsageTime(), a.getUsageTime()));

        requireActivity().runOnUiThread(() -> {
            totalScreenTime.setText(formatTime(totalTime));

            appUsageList.clear();
            appUsageList.addAll(appList);
            appUsageAdapter.notifyDataSetChanged();

            recentAppsAdapter.updateData(convertToRecent(lastUsedMap));
        });
    }

    private List<RecentAppInfo> convertToRecent(Map<String, Long> lastUsedMap) {
        List<RecentAppInfo> recentList = new ArrayList<>();

        for (Map.Entry<String, Long> entry : lastUsedMap.entrySet()) {
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(entry.getKey(), 0);
                String appName = packageManager.getApplicationLabel(appInfo).toString();

                recentList.add(new RecentAppInfo(
                        entry.getKey(),
                        appName,
                        entry.getValue(), // 🔥 THIS IS LAST USED TIME
                        packageManager.getApplicationIcon(appInfo)
                ));

            } catch (Exception e) {
                Log.e(TAG, "Error processing app usage", e);
            }
        }
        Collections.sort(recentList, (a, b) ->
                Long.compare(b.getUsageTime(), a.getUsageTime())
                );
        return recentList;
    }

    private void scheduleDailyReset() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY,0);
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

    private String formatTime(long millis) {
        long minutes = millis / 60000;
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m";
    }
}