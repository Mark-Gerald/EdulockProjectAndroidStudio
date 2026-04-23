package com.example.edulock.ui.restrict;

import android.content.Context;
import android.content.SharedPreferences;
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

    // ─── Persistence keys ───────────────────────────────────────────────
    private static final String PREFS_NAME      = "EduLock";
    private static final String KEY_CONN_CODE   = "connection_code";

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

        // Try to restore a previously generated QR / active session.
        // This is what lets the QR survive an app close, swipe-away, or
        // "clear from recents". Only the user's Disconnect button or the
        // teacher's Disconnect on the controller will wipe this state.
        restoreSavedConnection();

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
        Animation popIn = AnimationUtils.loadAnimation(getContext(), R.anim.qr_pop_in);
        qrCodeImageView.startAnimation(popIn);

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

        disconnectButton.setVisibility(View.GONE);
        statusTextView.setText("Generate QR code to connect to controller");

        qrPlaceholder = view.findViewById(R.id.qrPlaceholder);
        statusChip    = view.findViewById(R.id.statusChip);
        statusDot     = view.findViewById(R.id.statusDot);

        qrCodeImageView.setVisibility(View.GONE);
        qrPlaceholder.setVisibility(View.VISIBLE);
        setStatus("Waiting to connect", StatusKind.NEUTRAL);
    }

    /**
     * IMPORTANT: We no longer call setValue(...) on registered_devices/{deviceId}
     * here. That call was overwriting controllerConnected / isBlocked back to
     * false every single time the app launched, which is why a blocked phone
     * "forgot" it was blocked after being killed.
     *
     * We only obtain the database reference now. The actual node is created
     * in generateAndDisplayQRCode() under the connection-code key, and is
     * restored (read-only, NOT overwritten) by restoreSavedConnection().
     */
    private void setupFirebase() {
        deviceId = Settings.Secure.getString(getContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        databaseRef = FirebaseDatabase.getInstance().getReference("registered_devices");
    }

    private void setupListeners() {
        generateQrButton.setOnClickListener(v -> generateAndDisplayQRCode());
        disconnectButton.setOnClickListener(v -> disconnectController());
    }

    // ─── Persistence helpers ───────────────────────────────────────────

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void saveConnectionCode(String code) {
        prefs().edit().putString(KEY_CONN_CODE, code).apply();
    }

    private void clearSavedConnectionCode() {
        prefs().edit().remove(KEY_CONN_CODE).apply();
    }

    private String getStoredConnectionCode() {
        return prefs().getString(KEY_CONN_CODE, null);
    }

    private String getStoredDeviceId() {
        String storedId = prefs().getString("device_id", null);
        if (storedId == null) {
            storedId = UUID.randomUUID().toString();
            prefs().edit().putString("device_id", storedId).apply();
        }
        return storedId;
    }

    /**
     * Restore a previously saved QR session if one exists in Firebase.
     * Three possible outcomes:
     *   1) Saved code exists AND node still exists in Firebase  → rebuild QR + reattach listener.
     *   2) Saved code exists but node was DELETED by the teacher → forget it, show fresh "generate" UI.
     *   3) No saved code at all                                  → do nothing.
     */
    private void restoreSavedConnection() {
        final String saved = getStoredConnectionCode();
        if (saved == null) return;

        final DatabaseReference savedRef = databaseRef.child(saved);
        savedRef.keepSynced(true);

        savedRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Teacher disconnected on the controller side — wipe local state.
                    clearSavedConnectionCode();
                    return;
                }

                try {
                    currentConnectionCode = saved;

                    // Rebuild the QR bitmap from the saved code.
                    MultiFormatWriter writer = new MultiFormatWriter();
                    BitMatrix bitMatrix = writer.encode(saved, BarcodeFormat.QR_CODE, 500, 500);
                    Bitmap bitmap = new BarcodeEncoder().createBitmap(bitMatrix);

                    qrCodeImageView.setImageBitmap(bitmap);
                    qrCodeImageView.setVisibility(View.VISIBLE);
                    qrPlaceholder.setVisibility(View.GONE);
                    generateQrButton.setVisibility(View.GONE);
                    disconnectButton.setVisibility(View.VISIBLE);

                    Boolean connected = snapshot.child("controllerConnected").getValue(Boolean.class);
                    Boolean blocked   = snapshot.child("isBlocked").getValue(Boolean.class);

                    if (Boolean.TRUE.equals(connected)) {
                        stopStatusDotPulse();
                        setStatus(
                                Boolean.TRUE.equals(blocked)
                                        ? "Device is blocked"
                                        : "Connected — device unblocked",
                                StatusKind.SUCCESS
                        );
                        if (controlListener != null) {
                            controlListener.onDeviceControlStatusChanged(Boolean.TRUE.equals(blocked));
                        }
                    } else {
                        setStatus("Waiting for controller to scan…", StatusKind.WARNING);
                        startStatusDotPulse();
                    }

                    // Re-arm the auto-disconnect on socket loss.
                    // Only flip controllerConnected → false. We DO NOT touch isBlocked
                    // here; the teacher controls that and it must persist.
                    savedRef.child("controllerConnected").onDisconnect().setValue(false);

                    // Make sure controllerConnected reflects reality right now.
                    // If we were online before being killed, Firebase already flipped
                    // it to false via onDisconnect. The teacher's UI listener will
                    // detect us coming back online once the controller pings.

                    startListeningForConnection(saved);
                } catch (Exception e) {
                    clearSavedConnectionCode();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { /* ignore */ }
        });
    }

    private void generateAndDisplayQRCode() {
        try {
            currentConnectionCode = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

            MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
            BitMatrix bitMatrix = multiFormatWriter.encode(currentConnectionCode, BarcodeFormat.QR_CODE, 500, 500);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);

            qrCodeImageView.setImageBitmap(bitmap);
            qrCodeImageView.setVisibility(View.VISIBLE);
            qrPlaceholder.setVisibility(View.GONE);
            animateQrIn();
            setStatus("Waiting for controller to scan…", StatusKind.WARNING);
            startStatusDotPulse();

            final DatabaseReference newDeviceRef = databaseRef.child(currentConnectionCode);
            newDeviceRef.keepSynced(true);

            FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            final String userId = currentUser != null ? currentUser.getUid() : null;

            newDeviceRef.setValue(new DeviceData(
                    "Device " + deviceId.substring(0, 8),
                    false,
                    false
            )).addOnSuccessListener(aVoid -> {
                if (userId != null) {
                    newDeviceRef.child("userId").setValue(userId);
                }
                // Stable identifier — the website uses this to detect that a
                // reconnecting QR belongs to the SAME phone, so it can revive
                // the offline row instead of creating a duplicate.
                newDeviceRef.child("hardwareId").setValue(deviceId);

                // Auto-mark the controller as disconnected if the network drops.
                // We deliberately do NOT touch isBlocked here — the block state
                // must persist until the teacher unblocks or disconnects.
                newDeviceRef.child("controllerConnected").onDisconnect().setValue(false);

                // Persist so the QR survives an app close / clear from recents.
                saveConnectionCode(currentConnectionCode);

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
        if (connectionListener != null && currentConnectionCode != null) {
            databaseRef.child(currentConnectionCode).removeEventListener(connectionListener);
        }

        DatabaseReference deviceRef = databaseRef.child(connectionCode);
        connectionListener = deviceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Node was deleted — that means the TEACHER disconnected this
                // device on the controller. Wipe everything locally and go back
                // to the "generate QR" screen.
                if (!snapshot.exists()) {
                    if (connectionListener != null) {
                        deviceRef.removeEventListener(connectionListener);
                        connectionListener = null;
                    }
                    currentConnectionCode = null;
                    clearSavedConnectionCode();
                    if (controlListener != null) {
                        controlListener.onDeviceControlStatusChanged(false);
                    }
                    resetViews();
                    return;
                }

                Boolean isBlocked   = snapshot.child("isBlocked").getValue(Boolean.class);
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

    /**
     * User tapped Disconnect inside the app. This is one of only two places
     * that wipes the saved connection code (the other is a teacher-initiated
     * delete picked up by startListeningForConnection).
     */
    private void disconnectController() {
        if (connectionListener != null && currentConnectionCode != null) {
            DatabaseReference deviceRef = databaseRef.child(currentConnectionCode);
            deviceRef.removeEventListener(connectionListener);

            // Cancel onDisconnect so it doesn't fire after we removeValue.
            deviceRef.child("controllerConnected").onDisconnect().cancel();

            deviceRef.removeValue().addOnCompleteListener(task -> {
                connectionListener = null;
                currentConnectionCode = null;
                clearSavedConnectionCode();
                resetViews();
            });
        } else {
            clearSavedConnectionCode();
            resetViews();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        controlListener = null;
        // NOTE: we do NOT remove the saved code here. Detaching the fragment
        // (e.g. tab switch, app backgrounded, app killed) should leave the
        // saved code intact so the next launch can restore the QR.
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
