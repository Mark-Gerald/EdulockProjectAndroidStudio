package com.example.edulock.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/**
 * PinSetupHelper — handles creating and saving a 4-digit PIN for TimeLimitActivity.
 *
 * Usage (call from TimeLimitActivity after a successful save that has apps selected):
 *
 *   PinSetupHelper.promptSetPinIfNeeded(this, () -> {
 *       // optional: run after PIN is set (or skipped)
 *       finish();
 *   });
 */
public class PinSetupHelper {

    private static final String PREFS_NAME = "app_restrictions";
    private static final String KEY_PIN = "time_limit_pin";

    /**
     * Shows a PIN creation dialog only if no PIN has been set yet.
     * If a PIN already exists, calls onComplete immediately.
     *
     * @param context    Activity context
     * @param onComplete Runnable to call after PIN is set or if PIN already exists
     */
    public static void promptSetPinIfNeeded(Context context, Runnable onComplete) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String existingPin = prefs.getString(KEY_PIN, null);

        if (existingPin != null && !existingPin.isEmpty()) {
            // PIN already set — skip prompt
            if (onComplete != null) onComplete.run();
            return;
        }

        showPinCreationDialog(context, onComplete);
    }

    /**
     * Forces a new PIN creation dialog (e.g. for "Change PIN" feature).
     */
    public static void showPinCreationDialog(Context context, Runnable onComplete) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(60, 40, 60, 20);

        TextView title = new TextView(context);
        title.setText("Create a PIN");
        title.setTextSize(20f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 8);

        TextView subtitle = new TextView(context);
        subtitle.setText("Set a 4-digit PIN to protect your time limit settings");
        subtitle.setTextSize(13f);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setAlpha(0.6f);
        subtitle.setPadding(0, 0, 0, 24);

        // First PIN entry
        TextView pinLabel1 = new TextView(context);
        pinLabel1.setText("Enter PIN:");
        pinLabel1.setTextSize(14f);
        pinLabel1.setPadding(0, 0, 0, 8);

        EditText pinInput1 = new EditText(context);
        pinInput1.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput1.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        pinInput1.setGravity(Gravity.CENTER);
        pinInput1.setTextSize(24f);
        pinInput1.setHint("● ● ● ●");

        // Confirm PIN entry
        TextView pinLabel2 = new TextView(context);
        pinLabel2.setText("Confirm PIN:");
        pinLabel2.setTextSize(14f);
        pinLabel2.setPadding(0, 16, 0, 8);

        EditText pinInput2 = new EditText(context);
        pinInput2.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput2.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        pinInput2.setGravity(Gravity.CENTER);
        pinInput2.setTextSize(24f);
        pinInput2.setHint("● ● ● ●");

        // Dot indicators for both fields
        LinearLayout dots1Row = buildDotsRow(context, pinInput1);
        LinearLayout dots2Row = buildDotsRow(context, pinInput2);

        layout.addView(title);
        layout.addView(subtitle);
        layout.addView(pinLabel1);
        layout.addView(pinInput1);
        layout.addView(dots1Row);
        layout.addView(pinLabel2);
        layout.addView(pinInput2);
        layout.addView(dots2Row);

        builder.setView(layout);
        builder.setCancelable(false); // Force them to set a PIN

        builder.setPositiveButton("Save PIN", (dialog, which) -> {
            String pin1 = pinInput1.getText().toString().trim();
            String pin2 = pinInput2.getText().toString().trim();

            if (pin1.length() != 4) {
                Toast.makeText(context, "PIN must be exactly 4 digits", Toast.LENGTH_SHORT).show();
                showPinCreationDialog(context, onComplete); // re-show
                return;
            }

            if (!pin1.equals(pin2)) {
                Toast.makeText(context, "PINs do not match. Try again.", Toast.LENGTH_SHORT).show();
                showPinCreationDialog(context, onComplete); // re-show
                return;
            }

            // Save PIN
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_PIN, pin1).apply();
            Toast.makeText(context, "PIN saved! Time limits are now protected.", Toast.LENGTH_SHORT).show();

            if (onComplete != null) onComplete.run();
        });

        builder.setNegativeButton("Skip", (dialog, which) -> {
            // No PIN set — onComplete still runs
            if (onComplete != null) onComplete.run();
        });

        builder.show();
    }

    /**
     * Clears the saved PIN (e.g., when all restrictions are removed).
     */
    public static void clearPin(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_PIN).apply();
    }

    /**
     * Returns true if a PIN is currently saved.
     */
    public static boolean hasPinSet(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String pin = prefs.getString(KEY_PIN, null);
        return pin != null && !pin.isEmpty();
    }

    // Helper to build a dot indicator row linked to an EditText
    private static LinearLayout buildDotsRow(Context context, EditText pinInput) {
        LinearLayout dotsRow = new LinearLayout(context);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        dotsRow.setPadding(0, 8, 0, 0);

        TextView[] dots = new TextView[4];
        for (int i = 0; i < 4; i++) {
            TextView dot = new TextView(context);
            dot.setText("○");
            dot.setTextSize(20f);
            dot.setPadding(10, 0, 10, 0);
            dots[i] = dot;
            dotsRow.addView(dot);
        }

        pinInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                int len = s.length();
                for (int i = 0; i < 4; i++) {
                    dots[i].setText(i < len ? "●" : "○");
                }
            }
        });

        return dotsRow;
    }
}