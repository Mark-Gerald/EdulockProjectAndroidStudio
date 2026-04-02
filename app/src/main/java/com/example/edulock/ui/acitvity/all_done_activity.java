package com.example.edulock.ui.acitvity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.edulock.R;
import com.example.edulock.utils.AuthStateManager;
import com.example.edulock.utils.SoundManager;

public class all_done_activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_all_done);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageView image = findViewById(R.id.allDone);
        TextView title = findViewById(R.id.titleDone);
        TextView text1 = findViewById(R.id.text1);
        TextView text2 = findViewById(R.id.text2);
        TextView text3 = findViewById(R.id.text3);
        Button button = findViewById(R.id.nextAllDone);

        image.setAlpha(0f);
        image.setTranslationY(-120f);

        title.setAlpha(0f);
        text1.setAlpha(0f);
        text2.setAlpha(0f);
        text3.setAlpha(0f);

        button.setAlpha(0f);
        button.setTranslationY(200f);
        button.setScaleX(0.8f);
        button.setScaleY(0.8f);

        image.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(700)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        title.animate()
                .alpha(1f)
                .setStartDelay(300)
                .setDuration(500)
                .start();

        text1.animate()
                .alpha(1f)
                .setStartDelay(450)
                .setDuration(500)
                .start();

        text2.animate()
                .alpha(1f)
                .setStartDelay(600)
                .setDuration(500)
                .start();

        text3.animate()
                .alpha(1f)
                .setStartDelay(750)
                .setDuration(500)
                .start();

        button.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(900)
                .setDuration(500)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SoundManager.playButtonSound(all_done_activity.this);

                AuthStateManager authStateManager = new AuthStateManager(all_done_activity.this);
                authStateManager.markWelcomeCompleted();
                authStateManager.markPermissionGranted();

                Intent intent = new Intent(all_done_activity.this, login_register.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });
    }
}