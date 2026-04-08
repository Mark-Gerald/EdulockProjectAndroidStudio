package com.example.edulock.ui.acitvity;

import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.app.AlertDialog;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.edulock.R;
import com.example.edulock.service.AppBlockerAccessibilityService;
import com.example.edulock.service.AppMonitoringService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TimeLimitActivity extends AppCompatActivity {
    private static final String TAG = "TimeLimitActivity";
    private static final String PREFS_NAME = "app_restrictions";
    private static final String KEY_RESTRICTED_APPS = "restricted_apps";
    private static final String KEY_TIME_LIMIT = "selected_time_limit";

    private final List<String> selectedApps = new ArrayList<>();
    private final List<AppInfo> allApps = new ArrayList<>();
    private final List<AppInfo> userApps = new ArrayList<>();
    private List<AppInfo> appList;

    private int selectedTimeLimit = 1;
    private LinearLayout appsContainer;
    private SearchView searchView;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time_limit);

        initializeViews();
        initializePreferences();
        setupTimeSelection();
        loadSavedSettings();
        loadInstalledApps();
        setupSearch();
        setupSelectAllCheckbox();
        setupButtons();

        Log.d("TimeLimitActivity", "✅ onCreate completed - all setup done");
    }

    private void initializeViews() {
        appsContainer = findViewById(R.id.apps_container);
        searchView = findViewById(R.id.search_app);
    }

    private void initializePreferences() {
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> savedApps = preferences.getStringSet(KEY_RESTRICTED_APPS, new HashSet<>());
        selectedApps.addAll(savedApps);
    }

    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/" + AppBlockerAccessibilityService.class.getCanonicalName();
        int accessibilityEnabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                splitter.setString(settingValue);
                while (splitter.hasNext()) {
                    String accessibilityService = splitter.next();
                    if (accessibilityService.equalsIgnoreCase(serviceName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void showAccessibilityDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Please enable the accessibility service for EduLock to work properly")
                .setPositiveButton("Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupTimeSelection() {
        // Remove old TextViews setup - we'll use a custom picker now
        setupCustomTimePicker();
    }

    private void setupCustomTimePicker() {
        // Create a linear layout to hold hour, minute, second pickers
        LinearLayout timePickerContainer = findViewById(R.id.time_picker_container);

        if (timePickerContainer != null) {
            timePickerContainer.removeAllViews();

            // Hours NumberPicker
            NumberPicker hourPicker = new NumberPicker(this);
            hourPicker.setMinValue(0);
            hourPicker.setMaxValue(23);
            hourPicker.setValue(0);
            hourPicker.setWrapSelectorWheel(true);

            // Minutes NumberPicker
            NumberPicker minutePicker = new NumberPicker(this);
            minutePicker.setMinValue(0);
            minutePicker.setMaxValue(59);
            minutePicker.setValue(0);
            minutePicker.setWrapSelectorWheel(true);

            // Seconds NumberPicker
            NumberPicker secondPicker = new NumberPicker(this);
            secondPicker.setMinValue(0);
            secondPicker.setMaxValue(59);
            secondPicker.setValue(0);
            secondPicker.setWrapSelectorWheel(true);

            // Container for pickers
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.weight = 1;

            // Add listeners to update selectedTimeLimit
            hourPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                updateSelectedTimeFromPicker(hourPicker, minutePicker, secondPicker);
            });

            minutePicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                updateSelectedTimeFromPicker(hourPicker, minutePicker, secondPicker);
            });

            secondPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                updateSelectedTimeFromPicker(hourPicker, minutePicker, secondPicker);
            });

            timePickerContainer.addView(hourPicker, params);

            // Add separators (colons)
            TextView colon1 = new TextView(this);
            colon1.setText(":");
            colon1.setTextColor(Color.WHITE);
            colon1.setTextSize(24);
            colon1.setGravity(Gravity.CENTER);
            colon1.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            timePickerContainer.addView(colon1);

            timePickerContainer.addView(minutePicker, params);

            TextView colon2 = new TextView(this);
            colon2.setText(":");
            colon2.setTextColor(Color.WHITE);
            colon2.setTextSize(24);
            colon2.setGravity(Gravity.CENTER);
            colon2.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            timePickerContainer.addView(colon2);

            timePickerContainer.addView(secondPicker, params);
        }
    }

    private void updateSelectedTimeFromPicker(NumberPicker hours, NumberPicker minutes, NumberPicker seconds) {
        int totalSeconds = (hours.getValue() * 3600) + (minutes.getValue() * 60) + seconds.getValue();
        selectedTimeLimit = totalSeconds / 60; // 0 is valid — means no restriction
        Log.d(TAG, "Selected time: " + hours.getValue() + ":" +
                String.format("%02d", minutes.getValue()) + ":" +
                String.format("%02d", seconds.getValue()) + " = " + selectedTimeLimit + " minutes");
    }

    private void loadSavedSettings() {
        int savedTimeLimit = preferences.getInt(KEY_TIME_LIMIT, 0);
        Log.d("TimeLimitActivity", "Loaded saved time limit: " + savedTimeLimit + " minutes");

        int hours = savedTimeLimit / 60;
        int minutes = savedTimeLimit % 60;

        LinearLayout container = findViewById(R.id.time_picker_container);
        if (container != null && container.getChildCount() >= 5) {
            try {
                NumberPicker hourPicker = (NumberPicker) container.getChildAt(0);
                NumberPicker minutePicker = (NumberPicker) container.getChildAt(2);
                NumberPicker secondPicker = (NumberPicker) container.getChildAt(4);

                hourPicker.setValue(hours);
                minutePicker.setValue(minutes);
                secondPicker.setValue(0);

                Log.d("TimeLimitActivity", "Set pickers to: " + hours + "h " + minutes + "m");
            } catch (Exception e) {
                Log.e("TimeLimitActivity", "Error setting pickers: " + e.getMessage());
            }
        }
    }

    private void setupSelectAllCheckbox() {
        CheckBox selectAllCheckBox = findViewById(R.id.select_all_checkbox);

        selectAllCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {

            selectedApps.clear(); // 🔥 IMPORTANT

            for (int i = 0; i < appsContainer.getChildCount(); i++) {
                View appView = appsContainer.getChildAt(i);
                CheckBox appCheckBox = appView.findViewById(R.id.app_checkbox);

                if (appCheckBox != null) {
                    appCheckBox.setChecked(isChecked);

                    if (isChecked) {
                        String packageName = userApps.get(i).getPackageName();
                        selectedApps.add(packageName);
                    }
                }
            }

            Log.d("TimeLimitActivity", "SelectAll apps: " + selectedApps.size());
        });
    }

    private void setupButtons() {
        Log.d("TimeLimitActivity", "Setting up buttons...");

        findViewById(R.id.back_arrow).setOnClickListener(v -> {
            Log.d("TimeLimitActivity", "Back arrow clicked");
            finish();
        });

        findViewById(R.id.save_button).setOnClickListener(v -> {
            Log.d("TimeLimitActivity", "🟢 SAVE BUTTON CLICKED");
            saveRestrictions();
        });

        findViewById(R.id.cancel_button).setOnClickListener(v -> {
            Log.d("TimeLimitActivity", "Cancel button clicked");
            finish();
        });
    }

    private void loadInstalledApps() {
        new Thread(() -> {

            PackageManager packageManager = getPackageManager();
            List<ApplicationInfo> apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

            allApps.clear();
            userApps.clear();

            String myPackageName = getPackageName();

            for (ApplicationInfo appInfo : apps) {
                // ✅ EXCLUDE OUR OWN APP
                if (appInfo.packageName.equals(myPackageName)) {
                    Log.d(TAG, "Skipping own app: " + myPackageName);
                    continue;
                }

                // Skip system apps
                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    continue;
                }

                // Skip apps without launch intent
                if (packageManager.getLaunchIntentForPackage(appInfo.packageName) == null) {
                    continue;
                }

                try {
                    String appName = packageManager.getApplicationLabel(appInfo).toString();
                    Drawable appIcon = packageManager.getApplicationIcon(appInfo);

                    AppInfo app = new AppInfo(appName, appInfo.packageName, appIcon);
                    userApps.add(app);
                    allApps.add(app);

                } catch (Exception e) {
                    Log.e(TAG, "Error loading app info: " + e.getMessage());
                }
            }

            userApps.sort((a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));
            appList = userApps;

            runOnUiThread(() -> displayApps(userApps));

        }).start();
    }

    private void displayApps(List<AppInfo> apps) {
        if (appsContainer == null) {
            Log.e(TAG, "Apps container is null");
            return;
        }

        appsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        if (apps.isEmpty()) {
            TextView noAppsText = new TextView(this);
            noAppsText.setText(R.string.no_apps_found);
            noAppsText.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            noAppsText.setPadding(16, 16, 16, 16);
            appsContainer.addView(noAppsText);
            return;
        }

        apps.sort((a, b) -> {
            boolean aSelected = selectedApps.contains(a.getPackageName());
            boolean bSelected = selectedApps.contains(b.getPackageName());

            return Boolean.compare(bSelected, aSelected);
        });

        for (AppInfo app : apps) {
            View appView = inflater.inflate(R.layout.item_app_checkbox, appsContainer, false);
            CheckBox appCheckBox = appView.findViewById(R.id.app_checkbox);
            TextView appNameText = appView.findViewById(R.id.app_name);
            ImageView appIconView = appView.findViewById(R.id.app_icon);

            appNameText.setText(app.getAppName());
            appIconView.setImageDrawable(app.getAppIcon());
            appCheckBox.setChecked(selectedApps.contains(app.getPackageName()));

            appCheckBox.setOnCheckedChangeListener(null);

            appCheckBox.setChecked(selectedApps.contains(app.getPackageName()));

            appCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {

                if (isChecked) {
                    selectedApps.add(app.getPackageName());
                    Log.d("CHECKBOX", "ADDED: " + app.getPackageName());
                } else {
                    selectedApps.remove(app.getPackageName());
                    Log.d("CHECKBOX", "REMOVED: " + app.getPackageName());
                }

                Log.d("CHECKBOX", "TOTAL: " + selectedApps.size());
            });

            appsContainer.addView(appView);
        }

        updateSelectAllCheckboxState();
    }

    private void updateSelectAllCheckboxState() {
        CheckBox selectAllCheckBox = findViewById(R.id.select_all_checkbox);
        if (selectAllCheckBox != null && appsContainer != null && appsContainer.getChildCount() > 0) {
            boolean allSelected = true;
            for (int i = 0; i < appsContainer.getChildCount(); i++) {
                View appView = appsContainer.getChildAt(i);
                CheckBox appCheckBox = appView.findViewById(R.id.app_checkbox);
                if (appCheckBox != null && !appCheckBox.isChecked()) {
                    allSelected = false;
                    break;
                }
            }
            selectAllCheckBox.setChecked(allSelected);
        }
    }

    private void setupSearch() {
        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    if (newText.isEmpty()) {
                        displayApps(userApps);
                    } else {
                        List<AppInfo> filteredApps = new ArrayList<>();
                        for (AppInfo app : userApps) {
                            if (app.getAppName().toLowerCase().contains(newText.toLowerCase())) {
                                filteredApps.add(app);
                            }
                        }
                        displayApps(filteredApps);
                    }
                    return true;
                }
            });
        }
    }

    private void saveRestrictions() {
        Log.d("TimeLimitActivity", "💾 SAVE button clicked");

        // ✅ STEP 1: Get time from pickers
        LinearLayout container = findViewById(R.id.time_picker_container);
        int hours = 0, minutes = 0, seconds = 0;

        if (container != null && container.getChildCount() >= 5) {
            try {
                NumberPicker hourPicker = (NumberPicker) container.getChildAt(0);
                NumberPicker minutePicker = (NumberPicker) container.getChildAt(2);
                NumberPicker secondPicker = (NumberPicker) container.getChildAt(4);

                hours = hourPicker.getValue();
                minutes = minutePicker.getValue();
                seconds = secondPicker.getValue();

                Log.d("TimeLimitActivity", "⏱️  Pickers read: " + hours + "h " + minutes + "m " + seconds + "s");
            } catch (Exception e) {
                Log.e("TimeLimitActivity", "❌ Error reading time pickers: " + e.getMessage());
                Toast.makeText(this, "Error reading time", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // ✅ STEP 2: Convert to minutes
        int totalSeconds = (hours * 3600) + (minutes * 60) + seconds;
        int selectedTimeLimit = totalSeconds / 60;

        Log.d("TimeLimitActivity", "⏱️  Converted: " + selectedTimeLimit + " minutes");

        // ✅ STEP 3: Get selected apps
        Set<String> selectedAppsSet = new HashSet<>(selectedApps);
        Log.d("TimeLimitActivity", "📱 Selected apps count: " + selectedAppsSet.size());

        if (selectedAppsSet.isEmpty()) {
            Log.e("TimeLimitActivity", "❌ NO APPS SELECTED!");
            Toast.makeText(this, "Please select at least one app", Toast.LENGTH_SHORT).show();
            return;
        }

        for (String app : selectedAppsSet) {
            Log.d("TimeLimitActivity", "   📦 App: " + app);
        }

        // ✅ STEP 4: Save to SharedPreferences
        try {
            SharedPreferences prefs = getSharedPreferences("app_restrictions", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            // Clear and save
            editor.clear();
            editor.putStringSet("restricted_apps", selectedAppsSet);
            editor.putInt("selected_time_limit", selectedTimeLimit);

            Log.d("TimeLimitActivity", "🔧 About to save to SharedPreferences...");
            boolean result = editor.commit();
            Log.d("TimeLimitActivity", "💾 Commit result: " + result);

            // Verify immediately
            Thread.sleep(100);
            Set<String> verify = prefs.getStringSet("restricted_apps", new HashSet<>());
            int verifyTime = prefs.getInt("selected_time_limit", -1);
            Log.d("TimeLimitActivity", "✅ VERIFIED - Apps: " + verify.size() + ", Time: " + verifyTime);

            if (verify.isEmpty()) {
                Log.e("TimeLimitActivity", "❌ VERIFICATION FAILED - Apps not saved!");
            }

        } catch (Exception e) {
            Log.e("TimeLimitActivity", "❌ Error saving: " + e.getMessage(), e);
            Toast.makeText(this, "Error saving restrictions", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ STEP 5: Tell service to reload
        try {
            Log.d("TimeLimitActivity", "🚀 Sending UPDATE_RESTRICTIONS to service...");
            Intent updateIntent = new Intent(this, AppMonitoringService.class);
            updateIntent.setAction("UPDATE_RESTRICTIONS");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(updateIntent);
            } else {
                startService(updateIntent);
            }

            Log.d("TimeLimitActivity", "✅ Service update intent sent");
        } catch (Exception e) {
            Log.e("TimeLimitActivity", "❌ Error starting service: " + e.getMessage(), e);
        }

        //Toast.makeText(this, "Saved: " + selectedAppsSet.size() + " app(s), " + selectedTimeLimit + " min", Toast.LENGTH_SHORT).show();

        if (selectedApps.size() > 1) {
            Toast.makeText(this, "Saved: " + selectedAppsSet.size() + " apps, " + selectedTimeLimit + " min", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Saved: " + selectedAppsSet.size() + " app, " + selectedTimeLimit + " min", Toast.LENGTH_SHORT).show();
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Log.e("TimeLimitActivity", "Sleep interrupted");
        }

        finish();
    }

    private static class AppInfo {
        private final String appName;
        private final String packageName;
        private final Drawable appIcon;

        public AppInfo(String appName, String packageName, Drawable appIcon) {
            this.appName = appName;
            this.packageName = packageName;
            this.appIcon = appIcon;
        }

        public String getAppName() {
            return appName;
        }

        public String getPackageName() {
            return packageName;
        }

        public Drawable getAppIcon() {
            return appIcon;
        }
    }
}