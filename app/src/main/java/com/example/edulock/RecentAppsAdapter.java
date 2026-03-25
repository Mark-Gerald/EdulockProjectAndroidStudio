package com.example.edulock;

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

public class RecentAppsAdapter extends RecyclerView.Adapter<RecentAppsAdapter.ViewHolder> {
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
        RecentAppInfo app = filteredList.get(position);

        holder.appIcon.setImageDrawable(app.getAppIcon());
        holder.appName.setText(app.getAppName());

        long diff = System.currentTimeMillis() - app.getUsageTime();
        holder.usageTime.setText(formatLastUsed(app.getUsageTime()));

        holder.itemView.setAlpha(1.0f);
        holder.itemView.setBackgroundColor(0x00000000);

        if(diff < 5000) {
            holder.activeDot.setVisibility(View.VISIBLE);
            holder.itemView.setAlpha(1.0f);
            holder.itemView.setBackgroundColor(0x1A00FF00);
        } else {
            holder.itemView.setAlpha(0.85f);
            holder.itemView.setBackgroundColor(0x00000000);
            holder.activeDot.setVisibility(View.GONE);
        }
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

    private String formatLastUsed(long time) {
        long diff = System.currentTimeMillis() - time;

        long seconds = diff/1000;
        long minutes = seconds/60;
        long hours = minutes / 60;

        if (seconds < 60) return "Active now";
        else if (minutes < 60) return minutes + " min ago";
        else if (hours < 24) return hours + " hr ago";
        else return (hours / 24) + " d ago";
    }

    private void startAutoRefresh() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                Collections.sort(recentAppsList, (a, b) ->
                    Long.compare(b.getUsageTime(), a.getUsageTime())
                );

                filteredList.clear();
                filteredList.addAll(recentAppsList);

                notifyDataSetChanged();

                handler.postDelayed(this,1000);
            }
        },  1000);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName, usageTime;
        View activeDot;


        ViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.appIcon);
            appName = itemView.findViewById(R.id.appName);
            usageTime = itemView.findViewById(R.id.usageTime);
            activeDot = itemView.findViewById(R.id.activeDot);
        }
    }
}