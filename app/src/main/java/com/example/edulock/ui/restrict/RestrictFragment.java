package com.example.edulock.ui.restrict;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.edulock.R;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.UUID;

public class RestrictFragment extends Fragment {
    private ImageView qrCodeImageView;
    private Button generateQrButton;
    private Button disconnectButton;
    private TextView statusTextView;
    private String deviceId;
    private DatabaseReference databaseRef;
    private ValueEventListener connectionListener;
    private OnDeviceControlListener controlListener;
    private View qrPlaceholder;
    private View statusChip;
    private View statusDot;

    private String currentConnectionCode;

    public interface OnDeviceControlListener {
        void onDeviceControlStatusChanged(boolean isBlocked);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_restrict, container, false);

        initializeViews(view);
        setupFirebase();
        setupListeners();

        return view;
    }

    private enum StatusKind { NEUTRAL, SUCCESS, WARNING }

    private void setStatus(String text, StatusKind kind) {
        statusTextView.setText(text);
        int chipBg, dotBg;
        switch (kind) {
            case SUCCESS:
                chipBg = R.drawable.bg_status_chip_success;
                dotBg  = R.drawable.bg_status_dot_success;
                break;
            case WARNING:
                chipBg = R.drawable.bg_status_chip_warning;
                dotBg  = R.drawable.bg_status_dot_warning;
                break;
            default:
                chipBg = R.drawable.bg_status_chip_neutral;
                dotBg  = R.drawable.bg_status_dot_neutral;
                break;
        }
        statusChip.setBackgroundResource(chipBg);
        statusDot.setBackgroundResource(dotBg);
    }

    private void animateQrIn() {
        // QR image pops in with overshoot
        Animation popIn = AnimationUtils.loadAnimation(getContext(), R.anim.qr_pop_in);
        qrCodeImageView.startAnimation(popIn);

        // Buttons slide up staggered
        generateQrButton.setVisibility(View.GONE);
        disconnectButton.setAlpha(0f);
        disconnectButton.setTranslationY(40f);
        disconnectButton.setVisibility(View.VISIBLE);
        disconnectButton.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay(180)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Status chip slides up
        statusChip.setAlpha(0f);
        statusChip.setTranslationY(24f);
        statusChip.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(280)
                .setStartDelay(120)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void animateResetIn() {
        generateQrButton.setAlpha(0f);
        generateQrButton.setVisibility(View.VISIBLE);
        generateQrButton.animate()
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void startStatusDotPulse() {
        ObjectAnimator pulse  = ObjectAnimator.ofFloat(statusDot, "scaleX", 1f, 1.5f, 1f);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(statusDot, "scaleY", 1f, 1.5f, 1f);

        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setRepeatMode(ObjectAnimator.RESTART);
        pulse.setDuration(900);

        pulseY.setRepeatCount(ObjectAnimator.INFINITE);
        pulseY.setRepeatMode(ObjectAnimator.RESTART);
        pulseY.setDuration(900);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(pulse, pulseY);
        set.start();

        statusDot.setTag(set);
    }

    private void stopStatusDotPulse() {
        Object tag = statusDot.getTag();
        if (tag instanceof AnimatorSet) {
            ((AnimatorSet) tag).cancel();
            statusDot.setScaleX(1f);
            statusDot.setScaleY(1f);
        }
    }

    private void initializeViews(View view) {
        qrCodeImageView = view.findViewById(R.id.qrCodeImageView);
        generateQrButton = view.findViewById(R.id.generateQrButton);
        disconnectButton = view.findViewById(R.id.disconnectButton);
        statusTextView = view.findViewById(R.id.statusTextView);

        // Set initial state
        disconnectButton.setVisibility(View.GONE);
        statusTextView.setText("Generate QR code to connect to controller");

        qrPlaceholder = view.findViewById(R.id.qrPlaceholder);
        statusChip    = view.findViewById(R.id.statusChip);
        statusDot     = view.findViewById(R.id.statusDot);

        qrCodeImageView.setVisibility(View.GONE);
        qrPlaceholder.setVisibility(View.VISIBLE);
        setStatus("Waiting to connect", StatusKind.NEUTRAL);
    }

    private void setupFirebase() {
        deviceId = Settings.Secure.getString(getContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        databaseRef = FirebaseDatabase.getInstance().getReference("registered_devices");

        DatabaseReference myDeviceRef = databaseRef.child(deviceId);

        // Keep device data in sync
        myDeviceRef.keepSynced(true);

        // Initialize device data with onDisconnect handlers
        myDeviceRef.setValue(new DeviceData(
                "Device " + deviceId.substring(0, 8),
                false,  // controllerConnected
                false   // isBlocked
        )).addOnSuccessListener(aVoid -> {
            myDeviceRef.child("controllerConnected").onDisconnect().setValue(false);
            myDeviceRef.child("isBlocked").onDisconnect().setValue(false);
        });
    }

    private void setupListeners() {
        generateQrButton.setOnClickListener(v -> generateAndDisplayQRCode());
        disconnectButton.setOnClickListener(v -> disconnectController());
    }

    private String getStoredDeviceId() {
        String storedId = getContext().getSharedPreferences("EduLock", Context.MODE_PRIVATE)
                .getString("device_id", null);
        if (storedId == null) {
            storedId = UUID.randomUUID().toString();
            getContext().getSharedPreferences("EduLock", Context.MODE_PRIVATE)
                    .edit()
                    .putString("device_id", storedId)
                    .apply();
        }
        return storedId;
    }

    private void generateAndDisplayQRCode() {
        try {
            // Generate a new random connection code
            currentConnectionCode = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

            // Generate QR code with the new connection code
            MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
            BitMatrix bitMatrix = multiFormatWriter.encode(currentConnectionCode, BarcodeFormat.QR_CODE, 500, 500);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);

            // Update UI first
            qrCodeImageView.setImageBitmap(bitmap);
            qrCodeImageView.setVisibility(View.VISIBLE);
            qrPlaceholder.setVisibility(View.GONE);
            animateQrIn();
            setStatus("Waiting for controller to scan…", StatusKind.WARNING);
            startStatusDotPulse();

            // Update Firebase with new connection code
            DatabaseReference newDeviceRef = databaseRef.child(currentConnectionCode);
            FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            final String userId = currentUser != null ? currentUser.getUid() : null;

            newDeviceRef.setValue(new DeviceData(
                    "Device " + deviceId.substring(0, 8),
                    false,
                    false
            )).addOnSuccessListener(aVoid -> {
                if (userId != null) {
                    newDeviceRef.child("userId").setValue(userId);  // ← this one line
                }
                startListeningForConnection(currentConnectionCode);
            }).addOnFailureListener(e -> {
                Toast.makeText(getContext(), "Failed to register device", Toast.LENGTH_SHORT).show();
            });
        } catch (WriterException e) {
            Toast.makeText(getContext(), "Error generating QR code", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startListeningForConnection(String connectionCode) {
        if (connectionListener != null) {
            // Remove any existing listener
            databaseRef.child(deviceId).removeEventListener(connectionListener);
        }

        // Listen to the new connection code path instead of device ID
        DatabaseReference deviceRef = databaseRef.child(connectionCode);
        connectionListener = deviceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                Boolean isBlocked = snapshot.child("isBlocked").getValue(Boolean.class);
                Boolean isConnected = snapshot.child("controllerConnected").getValue(Boolean.class);

                if (isConnected != null && isConnected) {
                    disconnectButton.setVisibility(View.VISIBLE);
                    stopStatusDotPulse();
                    setStatus(
                            isBlocked != null && isBlocked ? "Device is blocked" : "Connected — device unblocked",
                            StatusKind.SUCCESS
                    );
                }

                if (isBlocked != null && controlListener != null) {
                    controlListener.onDeviceControlStatusChanged(isBlocked);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Connection error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void disconnectController() {
        if (connectionListener != null && currentConnectionCode != null) {
            DatabaseReference deviceRef = databaseRef.child(currentConnectionCode);
            deviceRef.removeEventListener(connectionListener);

            // Remove the device data completely when disconnecting
            deviceRef.removeValue().addOnCompleteListener(task -> {
                connectionListener = null;
                currentConnectionCode = null;
                resetViews();
            });
        } else {
            resetViews();
        }
    }

    // Modify onDetach to clean up properly
    @Override
    public void onDetach() {
        super.onDetach();
        controlListener = null;
        if (connectionListener != null && currentConnectionCode != null) {
            databaseRef.child(currentConnectionCode).removeEventListener(connectionListener);
        }
    }

    private void resetViews() {
        stopStatusDotPulse();
        qrCodeImageView.setVisibility(View.GONE);
        qrPlaceholder.setVisibility(View.VISIBLE);
        disconnectButton.setVisibility(View.GONE);
        setStatus("Waiting to connect", StatusKind.NEUTRAL);
        animateResetIn();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnDeviceControlListener) {
            controlListener = (OnDeviceControlListener) context;
        }
    }

    private static class DeviceData {
        public String deviceName;
        public boolean controllerConnected;
        public boolean isBlocked;

        public DeviceData(String deviceName, boolean controllerConnected, boolean isBlocked) {
            this.deviceName = deviceName;
            this.controllerConnected = controllerConnected;
            this.isBlocked = isBlocked;
        }

        public DeviceData() {
            // Required empty constructor for Firebase
        }
    }
}