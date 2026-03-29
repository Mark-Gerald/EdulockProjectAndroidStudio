package com.example.edulock.ui.stats;

import com.example.edulock.utils.UsageTimeCalculator;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
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

    private void loadAllUsageStats() {
        try {
            // Use centralized calculator - SAME as notifications!
            Map<String, Long> appUsageMap = UsageTimeCalculator.getAppUsageToday(requireContext());

            // Get total screen time
            long totalTime = UsageTimeCalculator.getTotalScreenTimeToday(requireContext());

            // Process and display
            processAndDisplayUsage(appUsageMap, totalTime);

        } catch (Exception e) {
            Log.e(TAG, "Error loading usage stats", e);
        }
    }

    private void processAndDisplayUsage(Map<String, Long> appUsageMap, long totalTime) {
        List<AppUsageInfo> appList = new ArrayList<>();

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

        // Update UI on main thread
        requireActivity().runOnUiThread(() -> {
            totalScreenTime.setText(UsageTimeCalculator.formatTime(totalTime));

            appUsageList.clear();
            appUsageList.addAll(appList);
            appUsageAdapter.notifyDataSetChanged();
        });
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
}