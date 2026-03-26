package com.example.edulock.ui.contact;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.edulock.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class ContactUsFragment extends Fragment {
    private EditText editTextName, editTextEmail, editTextPhone, editTextMessage;
    private Button buttonSubmit;
    private FirebaseDatabase database;
    private DatabaseReference feedbackRef;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contact_us, container, false);

        try {
            database = FirebaseDatabase.getInstance();
            Log.d("Firebase", "Database URL: " + database.getReference().toString());
            feedbackRef = database.getReference("feedback");

            // Add connection state listener
            database.getReference(".info/connected").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    boolean connected = snapshot.getValue(Boolean.class);
                    Log.d("Firebase", "Connected: " + connected);
                    if (!connected) {
                        Log.d("ContactUsFragment", "F");
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    Log.e("Firebase", "Connection error: " + error.getMessage());
                }
            });

        } catch (Exception e) {
            Log.e("Firebase", "Error initializing Firebase: " + e.getMessage());
            Toast.makeText(getContext(), "Error initializing database", Toast.LENGTH_LONG).show();
        }

        // Initialize views
        editTextName = view.findViewById(R.id.editTextName);
        editTextEmail = view.findViewById(R.id.editTextEmail);
        editTextPhone = view.findViewById(R.id.editTextPhone);
        editTextMessage = view.findViewById(R.id.editTextMessage);
        buttonSubmit = view.findViewById(R.id.buttonSubmit);

        buttonSubmit.setOnClickListener(v -> submitFeedback());

        return view;
    }

    private void submitFeedback() {

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(getContext(), "Please sign in to submit feedback", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = editTextName.getText().toString().trim();
        String email = editTextEmail.getText().toString().trim();
        String phone = editTextPhone.getText().toString().trim();
        String message = editTextMessage.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || message.isEmpty()) {
            Toast.makeText(getContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create feedback object
        Map<String, Object> feedback = new HashMap<>();
        feedback.put("name", name);
        feedback.put("email", email);
        feedback.put("phone", phone);
        feedback.put("message", message);
        feedback.put("timestamp", ServerValue.TIMESTAMP);

        // Push feedback to Firebase
        feedbackRef.push().setValue(feedback)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Feedback submitted successfully", Toast.LENGTH_SHORT).show();
                    clearForm();
                })
                .addOnFailureListener(e -> {
                    String errorMessage = "Error: " + e.getMessage();
                    Log.e("Firebase", errorMessage, e);
                    Toast.makeText(getContext(), errorMessage, Toast.LENGTH_LONG).show();
                });
    }

    private void clearForm() {
        editTextName.setText("");
        editTextEmail.setText("");
        editTextPhone.setText("");
        editTextMessage.setText("");
    }
}