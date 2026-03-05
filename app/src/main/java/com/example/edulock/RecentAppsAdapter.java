package com.example.edulock;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.view.View;

public class RecentAppsAdapter extends RecyclerView.Adapter<RecentAppsAdapter.ViewHolder> {
    private List<RecentAppInfo> recentAppsList;
    private List<RecentAppInfo> filteredList;
    private Context context;

    // Constructor taking list of recent apps
    public RecentAppsAdapter(List<RecentAppInfo> appsList) {
        this.recentAppsList = new ArrayList<>(appsList);
        this.filteredList = new ArrayList<>(appsList);
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
        RecentAppInfo app = filteredList.get(position);
        holder.appIcon.setImageDrawable(app.getAppIcon());
        holder.appName.setText(app.getAppName());
        holder.usageTime.setText(formatTime(app.getUsageTime()));
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    public void updateData(List<RecentAppInfo> newList) {
        recentAppsList = new ArrayList<>(newList);
        filteredList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    // Improved filter method that shows results immediately
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

    private String formatTime(long millis) {
        long minutes = millis / 60000;
        long hours = minutes / 60;
        minutes = minutes % 60;
        return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
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