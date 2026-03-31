package com.example.edulock.ui.acitvity;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
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

import com.example.edulock.R;
import com.example.edulock.utils.AuthStateManager;
import com.example.edulock.utils.SoundManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class login_register extends AppCompatActivity {

    private FirebaseAuth auth;
    private EditText loginEmail, loginPassword;
    private TextView signupRedirectText;
    private Button loginButton;
    private GoogleSignInClient googleSignInClient;
    private static final int RC_SIGN_IN = 9001;

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

        // 🔥 NEW: Initialize Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))  // Get from google-services.json
                .requestEmail()
                .requestProfile()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Initialize UI components
        loginEmail = findViewById(R.id.signup_email);
        loginPassword = findViewById(R.id.signup_password);
        signupRedirectText = findViewById(R.id.loginRedirectText);
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

        // 🔥 NEW: Google Sign-In Button Click Listener
        Button googleSignInButton = findViewById(R.id.google_sign_in_button);
        googleSignInButton.setOnClickListener(v -> {
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Log.d("Login", "🔵 Google Sign-In button clicked");
                Intent signInIntent = googleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN);
            });
        });

        // Set up sign-up redirect listener
        signupRedirectText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(login_register.this, register_signup.class));
            }
        });
    }

    // 🔥 NEW: Handle Google Sign-In result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    Log.d("Login", "🔐 Google Sign-In successful: " + account.getEmail());
                    handleSignInResult(account);
                }
            } catch (ApiException e) {
                Log.e("Login", "❌ Sign in failed: " + e.getStatusCode());
                Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Function to style "Sign Up" text with underline and color
    private void styleSignUpRedirect() {
        String text = "Don't have an account? Sign up";
        SpannableString spannable = new SpannableString(text);
        int start = text.indexOf("Sign up");
        int end = start + "Sign up".length();

        spannable.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#6538e9")), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        signupRedirectText.setText(spannable);
    }

    // 🔥 FIXED: Handle Sign In Result
    private void handleSignInResult(GoogleSignInAccount account) {
        try {
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);

            auth.signInWithCredential(credential)
                    .addOnSuccessListener(authResult -> {
                        FirebaseUser user = authResult.getUser();
                        if (user != null) {
                            Log.d("Login", "✅ Firebase Sign-In successful");

                            // NEW: Check if user exists in Firestore
                            FirebaseFirestore db = FirebaseFirestore.getInstance();
                            db.collection("users").document(user.getUid())
                                    .get()
                                    .addOnSuccessListener(documentSnapshot -> {
                                        if (documentSnapshot.exists()) {
                                            // User exists - go to dashboard
                                            AuthStateManager authStateManager = new AuthStateManager(login_register.this);
                                            authStateManager.markUserLoggedIn(true);

                                            View loginCard = findViewById(R.id.loginCard);
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
                                        } else {
                                            // User not in Firestore - redirect to signup
                                            redirectToSignUpWithEmail(user.getEmail(), user.getDisplayName());
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("Login", "❌ Error checking user: " + e.getMessage());
                                        Toast.makeText(login_register.this, "Error verifying account", Toast.LENGTH_SHORT).show();
                                        auth.signOut();
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("Login", "❌ Firebase Sign-In failed: " + e.getMessage());
                        Toast.makeText(login_register.this, "Sign-In Failed", Toast.LENGTH_SHORT).show();
                    });
        } catch (Exception e) {
            Log.e("Login", "❌ Error during sign-in: " + e.getMessage());
        }
    }

    private void redirectToSignUpWithEmail(String email, String displayName) {
        Intent intent = new Intent(login_register.this, register_signup.class);
        intent.putExtra("email", email);
        intent.putExtra("displayName", displayName);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // 🔥 NEW METHOD: Save user profile to Firestore
    private void saveUserProfileToFirestore(FirebaseUser user) {
        if (user == null) {
            Log.e("Login", "❌ User is null");
            return;
        }

        String userId = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Get user data
        String displayName = user.getDisplayName() != null ? user.getDisplayName() : "User";
        String email = user.getEmail() != null ? user.getEmail() : "";
        Uri photoUrl = user.getPhotoUrl();
        String photoUrlString = photoUrl != null ? photoUrl.toString() : "";

        Log.d("Login", "👤 Saving user profile to Firestore");
        Log.d("Login", "Name: " + displayName);
        Log.d("Login", "Email: " + email);
        Log.d("Login", "📸 Photo URL: " + photoUrlString);

        // Create user data map
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", userId);
        userData.put("firstName", getFirstName(displayName));
        userData.put("lastName", getLastName(displayName));
        userData.put("email", email);
        userData.put("photoUrl", photoUrlString);  // 🔥 SAVE PHOTO URL HERE!
        userData.put("userType", "Student");
        userData.put("createdAt", System.currentTimeMillis());

        // Save to Firestore
        db.collection("users").document(userId)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d("Login", "✅ User profile saved successfully to Firestore");
                })
                .addOnFailureListener(e -> {
                    Log.e("Login", "❌ Error saving user profile: " + e.getMessage());
                });
    }

    // Helper methods to split name
    private String getFirstName(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "User";
        String[] parts = fullName.split(" ");
        return parts[0];
    }

    private String getLastName(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "";
        String[] parts = fullName.split(" ");
        if (parts.length > 1) {
            return parts[1];
        }
        return "";
    }
}