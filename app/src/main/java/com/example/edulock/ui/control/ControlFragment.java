package com.example.edulock.ui.control;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.example.edulock.R;
import com.example.edulock.ui.acitvity.TimeLimitActivity;

public class ControlFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_control, container, false);

        LinearLayout timeLimit = view.findViewById(R.id.time_limit_option);
        timeLimit.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), TimeLimitActivity.class);
            startActivity(intent);
        });

        return view;
    }
}