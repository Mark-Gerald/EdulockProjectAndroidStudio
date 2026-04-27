package com.example.edulock.ui.control;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.edulock.R;
import com.example.edulock.ui.acitvity.TimeLimitActivity;

import java.util.HashSet;
import java.util.Set;

public class ControlFragment extends Fragment {

    private static final String PREFS_NAME = "app_restrictions";
    private static final String KEY_RESTRICTED_APPS = "restricted_apps";
    private static final String KEY_PIN = "time_limit_pin";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_control, container, false);

        // Views to animate
        View headerSection = view.findViewById(R.id.header_section);
        View sectionLabel = view.findViewById(R.id.section_label);
        CardView timeLimitCard = view.findViewById(R.id.time_limit_option);
        CardView infoCard = view.findViewById(R.id.info_card);

        // Start invisible and offset downward
        headerSection.setAlpha(0f);
        headerSection.setTranslationY(-30f);
        sectionLabel.setAlpha(0f);
        timeLimitCard.setAlpha(0f);
        timeLimitCard.setTranslationY(60f);
        infoCard.setAlpha(0f);
        infoCard.setTranslationY(60f);

        DecelerateInterpolator interp = new DecelerateInterpolator();

        headerSection.animate().alpha(1f).translationY(0f).setStartDelay(80).setDuration(400).setInterpolator(interp).start();
        sectionLabel.animate().alpha(1f).setStartDelay(180).setDuration(300).start();
        timeLimitCard.animate().alpha(1f).translationY(0f).setStartDelay(250).setDuration(420).setInterpolator(interp).start();
        infoCard.animate().alpha(1f).translationY(0f).setStartDelay(340).setDuration(420).setInterpolator(interp).start();

        timeLimitCard.setOnClickListener(v -> {
            timeLimitCard.animate()
                    .scaleX(0.96f).scaleY(0.96f).setDuration(100)
                    .withEndAction(() -> timeLimitCard.animate()
                            .scaleX(1f).scaleY(1f).setDuration(100)
                            .withEndAction(this::handleTimeLimitCardClick)
                            .start())
                    .start();
        });

        return view;
    }

    /**
     * Decides whether to show PIN verification or open TimeLimitActivity directly.
     * PIN is only required if restrictions have been saved AND a PIN exists.
     */
    private void handleTimeLimitCardClick() {
        if (getContext() == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> restrictedApps = prefs.getStringSet(KEY_RESTRICTED_APPS, new HashSet<>());
        String savedPin = prefs.getString(KEY_PIN, null);

        boolean hasRestrictions = !restrictedApps.isEmpty();
        boolean hasPIN = savedPin != null && !savedPin.isEmpty();

        if (hasRestrictions && hasPIN) {
            showPinVerificationDialog(savedPin);
        } else {
            openTimeLimitActivity();
        }
    }

    /**
     * Shows a 4-digit PIN entry dialog. Opens TimeLimitActivity on success.
     */
    private void showPinVerificationDialog(String correctPin) {
        if (getContext() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.EduLock_AlertDialog);

        // --- Build custom PIN input layout ---
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(60, 40, 60, 20);

        TextView title = new TextView(requireContext());
        title.setText("Enter PIN");
        title.setTextSize(20f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 8);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText("Enter your 4-digit PIN to access Time Limits");
        subtitle.setTextSize(13f);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setAlpha(0.6f);
        subtitle.setPadding(0, 0, 0, 24);

        EditText pinInput = new EditText(requireContext());
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        pinInput.setGravity(Gravity.CENTER);
        pinInput.setTextSize(28f);
        pinInput.setLetterSpacing(0.5f);
        pinInput.setHint("● ● ● ●");

        // Dot indicator row
        LinearLayout dotsRow = new LinearLayout(requireContext());
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        dotsRow.setPadding(0, 16, 0, 0);

        TextView[] dots = new TextView[4];
        for (int i = 0; i < 4; i++) {
            TextView dot = new TextView(requireContext());
            dot.setText("○");
            dot.setTextSize(22f);
            dot.setPadding(12, 0, 12, 0);
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

        layout.addView(title);
        layout.addView(subtitle);
        layout.addView(pinInput);
        layout.addView(dotsRow);

        builder.setView(layout);
        builder.setCancelable(true);

        builder.setPositiveButton("Unlock", (dialog, which) -> {
            String entered = pinInput.getText().toString().trim();
            if (entered.equals(correctPin)) {
                openTimeLimitActivity();
            } else {
                Toast.makeText(requireContext(), "Incorrect PIN. Try again.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // Auto-submit when 4 digits entered
        pinInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 4) {
                    if (s.toString().equals(correctPin)) {
                        dialog.dismiss();
                        openTimeLimitActivity();
                    } else {
                        pinInput.setError("Wrong PIN");
                        pinInput.setText("");
                        Toast.makeText(requireContext(), "Incorrect PIN. Try again.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    private void openTimeLimitActivity() {
        Intent intent = new Intent(getActivity(), TimeLimitActivity.class);
        startActivity(intent);
    }
}