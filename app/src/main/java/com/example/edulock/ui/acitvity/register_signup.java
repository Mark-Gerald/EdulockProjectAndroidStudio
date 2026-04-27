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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.edulock.R;
import com.example.edulock.utils.AuthStateManager;
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

    // ✅ Individual RadioButtons — no RadioGroup needed
    private RadioButton radioStudent, radioTeacher, radioParent, radioChild;
    private RadioButton currentlySelectedRole = null;

    private boolean isGoogleSignInEmail = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register_signup);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeUIElements();
        handleGoogleSignInData();
        setupClickListeners();

        View root = findViewById(R.id.main);
        root.setAlpha(0f);
        root.setTranslationY(80f);
        root.animate().alpha(1f).translationY(0f).setDuration(600).start();

        animateFormElements();
    }

    private void animateFormElements() {
        // ✅ Use userTypeContainer instead of the old userTypeRadioGroup
        View userTypeContainer = findViewById(R.id.userTypeContainer);

        View[] views = {
                signupFirstName,
                signupLastName,
                signupEmail,
                signupPassword,
                userTypeContainer,   // ✅ fixed
                signupButton,
                loginRedirectText
        };

        int delay = 0;
        for (View v : views) {
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
        signupFirstName  = findViewById(R.id.signup_firstname);
        signupLastName   = findViewById(R.id.signup_lastname);
        signupEmail      = findViewById(R.id.signup_email);
        signupPassword   = findViewById(R.id.signup_password);
        signupButton     = findViewById(R.id.signup_button);
        loginRedirectText = findViewById(R.id.loginRedirectText);

        // ✅ Find individual RadioButtons from the 2x2 grid
        radioStudent = findViewById(R.id.radioStudent);
        radioTeacher = findViewById(R.id.radioTeacher);
        radioParent  = findViewById(R.id.radioParent);
        radioChild   = findViewById(R.id.radioChild);

        setupRoleSelection();
        styleLoginRedirectText();
    }

    /**
     * Simulates RadioGroup single-selection behavior across the 2x2 grid.
     */
    private void setupRoleSelection() {
        RadioButton[] roles = {radioStudent, radioTeacher, radioParent, radioChild};

        for (RadioButton rb : roles) {
            rb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // Uncheck all others
                    for (RadioButton other : roles) {
                        if (other != buttonView) {
                            other.setChecked(false);
                        }
                    }
                    currentlySelectedRole = (RadioButton) buttonView;
                }
            });
        }
    }

    private void handleGoogleSignInData() {
        Intent intent = getIntent();

        if (intent != null && intent.hasExtra("email")) {
            String googleEmail = intent.getStringExtra("email");
            String googleDisplayName = intent.getStringExtra("displayName");

            if (googleEmail != null && !googleEmail.isEmpty()) {
                signupEmail.setText(googleEmail);
                signupEmail.setEnabled(false);
                isGoogleSignInEmail = true;
                Log.d(TAG, "✅ Pre-filled email from Google: " + googleEmail);
            }

            if (googleDisplayName != null && !googleDisplayName.isEmpty()) {
                String[] nameParts = googleDisplayName.split(" ");
                if (nameParts.length >= 1) {
                    signupFirstName.setText(nameParts[0]);
                    Log.d(TAG, "✅ Pre-filled first name: " + nameParts[0]);
                }
                if (nameParts.length >= 2) {
                    signupLastName.setText(nameParts[1]);
                    Log.d(TAG, "✅ Pre-filled last name: " + nameParts[1]);
                }
            }
        }
    }

    private void setupClickListeners() {
        signupButton.setOnClickListener(v -> {
            v.animate()
                    .scaleX(0.95f).scaleY(0.95f).setDuration(100)
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

        if (!validateInputs(userFname, userLname, userEmail, pass)) {
            return;
        }

        // ✅ Use currentlySelectedRole instead of RadioGroup.getCheckedRadioButtonId()
        if (currentlySelectedRole == null) {
            Toast.makeText(this, "Please Select A User Type", Toast.LENGTH_SHORT).show();
            return;
        }

        String userType = currentlySelectedRole.getText().toString();
        signupButton.setEnabled(false);

        if (isGoogleSignInEmail) {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                registerUserProfileOnly(user.getUid(), userFname, userLname, userEmail, userType);
            } else {
                signupButton.setEnabled(true);
                Toast.makeText(this, "Error: User not authenticated", Toast.LENGTH_SHORT).show();
            }
        } else {
            createUserWithEmailAndPassword(userEmail, pass, userFname, userLname, userType);
        }
    }

    private void registerUserProfileOnly(String userId, String userFname, String userLname,
                                         String userEmail, String userType) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("firstName", userFname);
        userData.put("lastName", userLname);
        userData.put("email", userEmail);
        userData.put("userType", userType);
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("profileImageUrl", "");

        saveUserDataWithRetry(userId, userData, 3);
    }

    private void createUserWithEmailAndPassword(String userEmail, String pass,
                                                String userFname, String userLname, String userType) {
        auth.createUserWithEmailAndPassword(userEmail, pass)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("firstName", userFname);
                            userData.put("lastName", userLname);
                            userData.put("email", userEmail);
                            userData.put("userType", userType);
                            userData.put("createdAt", System.currentTimeMillis());
                            userData.put("profileImageUrl", "");

                            saveUserDataWithRetry(user.getUid(), userData, 3);
                        }
                    } else {
                        signupButton.setEnabled(true);
                        String errorMessage = task.getException() != null
                                ? task.getException().getMessage()
                                : "Registration failed";
                        Toast.makeText(register_signup.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserDataWithRetry(String userId, Map<String, Object> userData, int retriesLeft) {
        db.collection("users").document(userId)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(register_signup.this, "Registration Successful", Toast.LENGTH_SHORT).show();

                    AuthStateManager authStateManager = new AuthStateManager(register_signup.this);
                    authStateManager.markUserLoggedIn(true);

                    Intent intent = new Intent(register_signup.this, statistics_usage_data.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    if (retriesLeft > 0) {
                        saveUserDataWithRetry(userId, userData, retriesLeft - 1);
                    } else {
                        signupButton.setEnabled(true);
                        Log.e(TAG, "Failed to save user data after retries", e);
                        Toast.makeText(register_signup.this,
                                "Account created but profile save failed. Please update profile later.",
                                Toast.LENGTH_LONG).show();

                        AuthStateManager authStateManager = new AuthStateManager(register_signup.this);
                        authStateManager.markUserLoggedIn(true);

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
        if (!isGoogleSignInEmail) {
            if (password.isEmpty()) {
                signupPassword.setError("Password Cannot Be Empty");
                return false;
            }
            if (password.length() < 6) {
                signupPassword.setError("Password must be at least 6 characters");
                return false;
            }
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