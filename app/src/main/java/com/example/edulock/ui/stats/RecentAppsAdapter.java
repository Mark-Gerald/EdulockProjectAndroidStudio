package com.example.edulock.ui.stats;

import android.os.Handler;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import android.view.View;
import android.util.Log;

import com.example.edulock.R;
import com.example.edulock.model.RecentAppInfo;

/**
 * RECENT APPS ADAPTER
 *
 * Displays list of recently used apps with "X min ago" timestamps
 * Auto-updates timestamps every second (doesn't fetch new data, just refreshes times)
 */
public class RecentAppsAdapter extends RecyclerView.Adapter<RecentAppsAdapter.ViewHolder> {
    private static final String TAG = "RecentAppsAdapter";
    private List<RecentAppInfo> recentAppsList;
    private List<RecentAppInfo> filteredList;
    private Context context;

    private Handler handler = new Handler();

    // Constructor taking list of recent apps
    public RecentAppsAdapter(List<RecentAppInfo> appsList) {
        this.recentAppsList = new ArrayList<>(appsList);
        this.filteredList = new ArrayList<>(appsList);

        startAutoRefresh();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Getting context from parent view
        context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_recent_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position >= filteredList.size()) return; // Safety check

        RecentAppInfo app = filteredList.get(position);

        holder.appIcon.setImageDrawable(app.getAppIcon());
        holder.appName.setText(app.getAppName());

        // Show relative time: "Just now", "2 min ago", etc.
        holder.usageTime.setText(formatLastUsed(app.getUsageTime()));
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    /**
     * UPDATE DATA - Called from StatsFragment when new data is loaded
     * This is the MAIN source of data - replaces the list completely
     */
    public void updateData(List<RecentAppInfo> newList) {
        if (newList == null) newList = new ArrayList<>();

        recentAppsList.clear();
        recentAppsList.addAll(newList);

        // Sort by most recent (highest timestamp = most recent)
        Collections.sort(recentAppsList, (a, b) ->
                Long.compare(b.getUsageTime(), a.getUsageTime())
        );

        // Update filtered list
        filteredList.clear();
        filteredList.addAll(recentAppsList);

        Log.d(TAG, "Updated with " + newList.size() + " apps");
        notifyDataSetChanged();
    }

    /**
     * FILTER - Search through apps
     */
    public void filter(String query) {
        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(recentAppsList);
        } else {
            String lowerCaseQuery = query.toLowerCase(Locale.getDefault());
            for (RecentAppInfo app : recentAppsList) {
                if (app.getAppName().toLowerCase(Locale.getDefault()).contains(lowerCaseQuery)) {
                    filteredList.add(app);
                }
            }
        }
        notifyDataSetChanged();
    }

    /**
     * FORMAT LAST USED - Shows relative time
     *
     * Converts timestamp to human-readable format:
     * - "Active now" (< 1 min)
     * - "2 min ago"
     * - "3 hr ago"
     * - "1 d ago"
     */
    private String formatLastUsed(long timeStamp) {
        long diff = System.currentTimeMillis() - timeStamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (seconds < 60) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + " min ago";
        } else if (hours < 24) {
            return hours + " hr ago";
        } else {
            return days + " d ago";
        }
    }

    /**
     * AUTO REFRESH - Updates timestamps every second
     *
     * This does NOT fetch new data from system
     * It just re-calculates the relative time display ("2 min ago" → "3 min ago")
     * New data comes from updateData() when StatsFragment reloads
     */
    private void startAutoRefresh() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Only refresh the display times, don't re-fetch data
                notifyDataSetChanged();
                handler.postDelayed(this, 1000); // Update every 1 second
            }
        }, 1000);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName, usageTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.appIcon);
            appName = itemView.findViewById(R.id.appName);
            usageTime = itemView.findViewById(R.id.usageTime);
        }
    }
}