package com.example.edulock;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class termsAndAgreement extends AppCompatActivity {

    private CheckBox cbTermsAndAgreement;
    private Button secondActivityButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_terms_and_agreement);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        cbTermsAndAgreement = findViewById(R.id.cbTermsAndAgreement);
        secondActivityButton = findViewById(R.id.accessUsageData);

        secondActivityButton.setEnabled(false);
        secondActivityButton.setAlpha(0.3f);

        cbTermsAndAgreement.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                secondActivityButton.setEnabled(true);
                secondActivityButton.setAlpha(1f);
            }
            else {
                secondActivityButton.setEnabled(false);
                secondActivityButton.setAlpha(0.5f);
            }
        });

        ConstraintLayout root = findViewById(R.id.main);

        root.setAlpha(0f);
        root.setTranslationY(50f);

        root.animate()
                .alpha(1f)
                        .translationY(0f)
                                .setDuration(600)
                                        .start();

        secondActivityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v ) {

                SoundManager.playButtonSound(termsAndAgreement.this);

                Intent intent = new Intent(termsAndAgreement.this, settingsGrantPermission.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
    }
}