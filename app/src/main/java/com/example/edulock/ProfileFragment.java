package com.example.edulock;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.bumptech.glide.Glide;
import android.net.Uri;

public class ProfileFragment extends DialogFragment {
    private ImageView profileImageView;
    private TextView userNameText;
    private TextView userEmailText;
    private TextView userTypeText;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.FullScreenDialogStyle);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Initialize Firebase instances
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Initialize views
        profileImageView = view.findViewById(R.id.profile_image);
        userNameText = view.findViewById(R.id.profile_name);
        userEmailText = view.findViewById(R.id.profile_email);
        userTypeText = view.findViewById(R.id.profile_type);

        // Add close button
        view.findViewById(R.id.close_button).setOnClickListener(v -> dismiss());

        // Load user data and profile picture
        loadUserProfile();
        loadProfilePicture();

        return view;
    }

    private void loadProfilePicture() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            Uri photoUrl = user.getPhotoUrl();
            if (photoUrl != null) {
                // Load profile image using Glide
                Glide.with(this)
                        .load(photoUrl)
                        .circleCrop()
                        .placeholder(R.drawable.default_profile)
                        .error(R.drawable.default_profile)
                        .into(profileImageView);
            }
        }
    }

    private void loadUserProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            String userId = user.getUid();
            db.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String firstName = documentSnapshot.getString("firstName");
                            String lastName = documentSnapshot.getString("lastName");
                            String email = documentSnapshot.getString("email");
                            String userType = documentSnapshot.getString("userType");

                            userNameText.setText(String.format("%s %s", firstName, lastName));
                            userEmailText.setText(email);
                            userTypeText.setText(userType);
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Handle error
                    });
        }
    }
}