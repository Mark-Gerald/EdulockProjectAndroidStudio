package com.example.edulock;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        MaterialCardView card = findViewById(R.id.imageCard);

        card.setAlpha(0f);
        card.setTranslationY(200f);

        card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(800)
                .start();

        TextView welcome = findViewById(R.id.welcome);

        welcome.setAlpha(0f);

        welcome.animate()
                .alpha(1f)
                .setStartDelay(500)
                .setDuration(800)
                .start();

        TextView info = findViewById(R.id.info);

        info.setAlpha(0f);

        info.animate()
                .alpha(1f)
                .setStartDelay(500)
                .setDuration(600)
                .start();

        MaterialButton button = findViewById(R.id.accessUsageData);

        button.setAlpha(0f);
        button.setTranslationY(200f);

        button.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(700)
                .setDuration(600)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        button.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, termsAndAgreement.class);
            startActivity(intent);
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

}