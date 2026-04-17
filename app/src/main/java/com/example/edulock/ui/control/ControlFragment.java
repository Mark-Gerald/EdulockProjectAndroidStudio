package com.example.edulock.ui.control;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.edulock.R;
import com.example.edulock.ui.acitvity.TimeLimitActivity;

public class ControlFragment extends Fragment {

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

        // Header slides down from top
        headerSection.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(80)
                .setDuration(400)
                .setInterpolator(interp)
                .start();

        // Section label fades in
        sectionLabel.animate()
                .alpha(1f)
                .setStartDelay(180)
                .setDuration(300)
                .start();

        // Cards slide up with staggered delays
        timeLimitCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(250)
                .setDuration(420)
                .setInterpolator(interp)
                .start();

        infoCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(340)
                .setDuration(420)
                .setInterpolator(interp)
                .start();

        // Click listeners
        timeLimitCard.setOnClickListener(v -> {
            // Quick press scale animation before launching
            timeLimitCard.animate()
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        timeLimitCard.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .withEndAction(() -> {
                                    Intent intent = new Intent(getActivity(), TimeLimitActivity.class);
                                    startActivity(intent);
                                })
                                .start();
                    })
                    .start();
        });

        return view;
    }
}
