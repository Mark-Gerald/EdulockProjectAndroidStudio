package com.example.edulock.ui.acitvity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.edulock.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class register_signup extends AppCompatActivity {

    private static final String TAG = "RegisterSignup";
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private EditText signupLastName, signupFirstName, signupEmail, signupPassword;
    private Button signupButton;
    private TextView loginRedirectText;
    private RadioGroup userTypeRadioGroup;
    private RadioButton selectedRadioButton;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register_signup);

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize UI elements
        initializeUIElements();

        // Set up click listeners
        setupClickListeners();

        View root = findViewById(R.id.main);

        root.setAlpha(0f);
        root.setTranslationY(80f);

        root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .start();
        animateFormElements();
    }

    private void animateFormElements() {

        View[] views = {signupFirstName, signupLastName, signupEmail, signupPassword, userTypeRadioGroup, signupButton, loginRedirectText};

        int delay = 0;

        for(View v : views){
            v.setAlpha(0f);
            v.setTranslationY(40f);

            v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(delay)
                    .setDuration(400)
                    .start();

            delay += 80;
        }
    }

    private void initializeUIElements() {
        signupFirstName = findViewById(R.id.signup_firstname);
        signupLastName = findViewById(R.id.signup_lastname);
        signupEmail = findViewById(R.id.signup_email);
        signupPassword = findViewById(R.id.signup_password);
        signupButton = findViewById(R.id.signup_button);
        loginRedirectText = findViewById(R.id.loginRedirectText);
        userTypeRadioGroup = findViewById(R.id.radioGroup);

        styleLoginRedirectText();
    }

    private void setupClickListeners() {
        signupButton.setOnClickListener(v -> {
            v.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                        registerUser();
                    })
                    .start();
        });
        loginRedirectText.setOnClickListener(v -> {
            Intent intent = new Intent(register_signup.this, login_register.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }

    private void registerUser() {
        String userFname = signupFirstName.getText().toString().trim();
        String userLname = signupLastName.getText().toString().trim();
        String userEmail = signupEmail.getText().toString().trim();
        String pass = signupPassword.getText().toString().trim();

        // Validate inputs
        if (!validateInputs(userFname, userLname, userEmail, pass)) {
            return;
        }

        // Get selected user type
        int selectedId = userTypeRadioGroup.getCheckedRadioButtonId();
        if (selectedId == -1) {
            Toast.makeText(this, "Please Select A User Type", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedRadioButton = findViewById(selectedId);
        String userType = selectedRadioButton.getText().toString();

        // Disable button and show progress
        signupButton.setEnabled(false);

        // Create authentication
        auth.createUserWithEmailAndPassword(userEmail, pass)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            // Create user profile data
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("firstName", userFname);
                            userData.put("lastName", userLname);
                            userData.put("email", userEmail);
                            userData.put("userType", userType);
                            userData.put("createdAt", System.currentTimeMillis());
                            userData.put("profileImageUrl", ""); // Default empty for now

                            // Save to Firestore with retry mechanism
                            saveUserDataWithRetry(user.getUid(), userData, 3);
                        }
                    } else {
                        signupButton.setEnabled(true);
                        String errorMessage = task.getException() != null ?
                                task.getException().getMessage() : "Registration failed";
                        Toast.makeText(register_signup.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserDataWithRetry(String userId, Map<String, Object> userData, int retriesLeft) {
        db.collection("users").document(userId)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(register_signup.this, "Registration Successful", Toast.LENGTH_SHORT).show();
                    // Navigate to main activity
                    Intent intent = new Intent(register_signup.this, statistics_usage_data.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    if (retriesLeft > 0) {
                        // Retry saving data
                        saveUserDataWithRetry(userId, userData, retriesLeft - 1);
                    } else {
                        signupButton.setEnabled(true);
                        Log.e(TAG, "Failed to save user data after retries", e);
                        Toast.makeText(register_signup.this,
                                "Account created but profile save failed. Please update profile later.",
                                Toast.LENGTH_LONG).show();
                        // Navigate anyway since authentication succeeded
                        Intent intent = new Intent(register_signup.this, statistics_usage_data.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                });
    }

    private boolean validateInputs(String firstName, String lastName, String email, String password) {
        if (firstName.isEmpty()) {
            signupFirstName.setError("First Name Cannot Be Empty");
            return false;
        }
        if (lastName.isEmpty()) {
            signupLastName.setError("Last Name Cannot Be Empty");
            return false;
        }
        if (email.isEmpty()) {
            signupEmail.setError("Email Cannot Be Empty");
            return false;
        }
        if (password.isEmpty()) {
            signupPassword.setError("Password Cannot Be Empty");
            return false;
        }
        if (password.length() < 6) {
            signupPassword.setError("Password must be at least 6 characters");
            return false;
        }
        return true;
    }

    private void styleLoginRedirectText() {
        String text = "Login";
        SpannableString spannable = new SpannableString(text);
        int start = text.indexOf("Login");
        int end = start + "Login".length();

        spannable.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#6538e9")),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        loginRedirectText.setText(spannable);
    }
}