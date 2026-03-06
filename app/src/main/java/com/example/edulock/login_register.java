package com.example.edulock;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.AnimationDrawable;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class login_register extends AppCompatActivity {

    private FirebaseAuth auth;
    private EditText loginEmail, loginPassword;
    private TextView signupRedirectText;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login_register);

        // Apply system window insets for immersive UI
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        NestedScrollView root = findViewById(R.id.main);

        AnimationDrawable animationDrawable = (AnimationDrawable) root.getBackground();

        animationDrawable.setEnterFadeDuration(2000);
        animationDrawable.setExitFadeDuration(20000);
        animationDrawable.start();

        View card = findViewById(R.id.loginCard);

        card.setAlpha(0f);
        card.setTranslationY(80f);

        card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(700)
                .start();

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance();

        // Initialize UI components
        loginEmail = findViewById(R.id.signup_email);
        loginPassword = findViewById(R.id.signup_password);
        signupRedirectText = findViewById(R.id.loginRedirectText); // Correct ID here
        loginButton = findViewById(R.id.signup_button);

        loginButton.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(80)
                .withEndAction(() ->
                        loginButton.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(80)
                                .start())
                .start();

        // Style "Sign Up" text with underline and color
        styleSignUpRedirect();

        // Set up login button click listener
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = loginEmail.getText().toString().trim();
                String pass = loginPassword.getText().toString().trim();

                if (!email.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    if (!pass.isEmpty()) {
                        auth.signInWithEmailAndPassword(email, pass)
                                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                                    @Override
                                    public void onSuccess(AuthResult authResult) {
                                        Toast.makeText(login_register.this, "Login Successful", Toast.LENGTH_SHORT).show();
                                        View loginCard = findViewById(R.id.loginCard);

                                        SoundManager.playButtonSound(login_register.this);

                                        loginCard.animate()
                                                .alpha(0f)
                                                .translationY(-50f)
                                                .setDuration(400)
                                                .withEndAction(() -> {

                                                    Intent intent = new Intent(login_register.this, statistics_usage_data.class);
                                                    startActivity(intent);

                                                    overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);

                                                    finish();

                                                })
                                                .start();
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Toast.makeText(login_register.this, "Login Failed", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    } else {
                        loginPassword.setError("Password Cannot Be Empty");
                    }
                } else if (email.isEmpty()) {
                    loginEmail.setError("Email Cannot Be Empty");
                } else {
                    loginEmail.setError("Please Enter A Valid Email");
                }

                v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(80)
                        .withEndAction(() -> {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(80);
                        });
            }
        });

        // Set up sign-up redirect listener
        signupRedirectText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(login_register.this, register_signup.class));
            }
        });
    }

    // Function to style "Sign Up" text with underline and color
    private void styleSignUpRedirect() {
        String text = "Don't have an account? Sign up";
        SpannableString spannable = new SpannableString(text);
        int start = text.indexOf("Sign up");
        int end = start + "Sign up".length();

        // Apply underline and purple color to "Sign up"
        spannable.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#6538e9")), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        signupRedirectText.setText(spannable);
    }
}
