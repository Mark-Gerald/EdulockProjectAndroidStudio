package com.example.edulock;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.ViewHolder> {
    private List<AppUsageInfo> appUsageList;

    // Constructor
    public AppUsageAdapter(List<AppUsageInfo> appUsageList) {
        this.appUsageList = appUsageList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the item layout
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_usage, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppUsageInfo app = appUsageList.get(position);

        holder.appName.setText(app.getAppName());
        holder.appTime.setText(formatTime(app.getUsageTime()));

        long maxUsage = appUsageList.isEmpty() ? 1 : appUsageList.get(0).getUsageTime();

        int progress = (int) ((app.getUsageTime() * 100) / maxUsage);
        holder.usageProgress.setProgress(progress);
    }

    @Override
    public int getItemCount() {
        return appUsageList.size();
    }

    // ViewHolder class
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView appName, appTime;
        ProgressBar usageProgress;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            appName = itemView.findViewById(R.id.appName);
            appTime = itemView.findViewById(R.id.appTime);
            usageProgress = itemView.findViewById(R.id.usageProgress);
        }
    }

    public void updateData(List<AppUsageInfo> newList) {
        this.appUsageList.clear();
        this.appUsageList.addAll(newList);
        notifyDataSetChanged();
    }

    // Helper method to format time from milliseconds to hours/minutes
    private String formatTime(long millis) {
        long minutes = millis / 60000;
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m";
    }

    // New method to filter system apps
    public void filterSystemApps(PackageManager pm) {
        List<AppUsageInfo> filteredList = new ArrayList<>();
        for (AppUsageInfo app : this.appUsageList) {
            if (isUserApp(app.getPackageName(), pm)) {
                filteredList.add(app);
            }
        }
        this.appUsageList = filteredList;
        notifyDataSetChanged();
    }

    private boolean isUserApp(String packageName, PackageManager pm) {
        try {
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            return (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}