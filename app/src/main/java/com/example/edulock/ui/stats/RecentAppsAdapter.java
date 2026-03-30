package com.example.edulock.ui.stats;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.example.edulock.R;
import com.example.edulock.model.RecentAppInfo;

/**
 * RECENT APPS ADAPTER - Shows recently used apps with timestamps
 *
 * This adapter displays apps that were recently used, showing:
 * - App icon
 * - App name
 * - Time when it was last used (e.g., "Just now", "5 min ago")
 */
public class RecentAppsAdapter extends RecyclerView.Adapter<RecentAppsAdapter.ViewHolder> {
    private static final String TAG = "RecentAppsAdapter";
    private List<RecentAppInfo> recentAppsList;
    private List<RecentAppInfo> filteredList;
    private Context context;
    private Handler handler = new Handler();

    // Constructor - receives list of recent apps
    public RecentAppsAdapter(List<RecentAppInfo> appsList) {
        Log.d(TAG, "Constructor called with " + (appsList != null ? appsList.size() : 0) + " apps");
        this.recentAppsList = appsList != null ? new ArrayList<>(appsList) : new ArrayList<>();
        this.filteredList = new ArrayList<>(this.recentAppsList);
        Log.d(TAG, "Initialized - recentAppsList size: " + this.recentAppsList.size());
        startAutoRefresh();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_recent_app, parent, false);
        Log.d(TAG, "ViewHolder created");
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position >= filteredList.size()) {
            Log.w(TAG, "Position " + position + " out of bounds, list size: " + filteredList.size());
            return;
        }

        RecentAppInfo app = filteredList.get(position);
        Log.d(TAG, "Binding position " + position + ": " + app.getAppName());

        holder.appIcon.setImageDrawable(app.getAppIcon());
        holder.appName.setText(app.getAppName());
        holder.usageTime.setText(formatLastUsed(app.getUsageTime()));
    }

    @Override
    public int getItemCount() {
        Log.d(TAG, "getItemCount called: " + filteredList.size());
        return filteredList.size();
    }

    /**
     * UPDATE DATA - Called from StatsFragment when new data is loaded
     *
     * This is the MAIN way data gets into the adapter
     * It replaces the entire list and updates the display
     */
    public void updateData(List<RecentAppInfo> newList) {
        Log.d(TAG, "updateData called with " + (newList != null ? newList.size() : 0) + " apps");

        if (newList == null) {
            newList = new ArrayList<>();
        }

        // Clear old data
        recentAppsList.clear();
        recentAppsList.addAll(newList);

        // Sort by most recent (highest timestamp = most recent)
        Collections.sort(recentAppsList, (a, b) ->
                Long.compare(b.getUsageTime(), a.getUsageTime())
        );

        // Update filtered list
        filteredList.clear();
        filteredList.addAll(recentAppsList);

        Log.d(TAG, "After updateData - recentAppsList size: " + recentAppsList.size() +
                ", filteredList size: " + filteredList.size());

        // Tell RecyclerView to refresh the display
        notifyDataSetChanged();
        Log.d(TAG, "notifyDataSetChanged() called");
    }

    /**
     * FILTER - Search through apps by name
     */
    public void filter(String query) {
        Log.d(TAG, "filter called with query: '" + query + "'");

        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(recentAppsList);
            Log.d(TAG, "Query empty - showing all " + filteredList.size() + " apps");
        } else {
            String lowerCaseQuery = query.toLowerCase(Locale.getDefault());
            for (RecentAppInfo app : recentAppsList) {
                if (app.getAppName().toLowerCase(Locale.getDefault()).contains(lowerCaseQuery)) {
                    filteredList.add(app);
                }
            }
            Log.d(TAG, "Query '" + query + "' - found " + filteredList.size() + " matching apps");
        }
        notifyDataSetChanged();
    }

    /**
     * FORMAT LAST USED - Converts timestamp to human-readable format
     *
     * Converts:
     * - < 1 min → "Just now"
     * - < 1 hour → "5 min ago"
     * - < 1 day → "3 hr ago"
     * - >= 1 day → "1 d ago"
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
     * This does NOT fetch new data from the system
     * It just refreshes the display so "5 min ago" becomes "6 min ago", etc.
     */
    private void startAutoRefresh() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Refresh display to update timestamps
                notifyDataSetChanged();
                // Schedule next refresh in 1 second
                handler.postDelayed(this, 1000);
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