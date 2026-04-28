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

    private static final String PREFS_NAME    = "EduLock";
    private static final String KEY_CONN_CODE = "connection_code";

    // ── Phase tracking ─────────────────────────────────────────────────
    // Prevents the immediate Firebase echo from overwriting Phase 2.
    // Set to true only after the listener has been armed. The first
    // snapshot that arrives is ALWAYS the locally-written value (Phase 2),
    // so we skip it and wait for a real controller update.
    private boolean listenerArmed = false;

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

    // ── Phase 4: track blocked state to disable disconnect ────────────
    private boolean isCurrentlyBlocked = false;

    public interface OnDeviceControlListener {
        void onDeviceControlStatusChanged(boolean isBlocked);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_restrict, container, false);
        initializeViews(view);
        setupFirebase();
        setupListeners();
        restoreSavedConnection();
        return view;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  STATUS CHIP
    // ═══════════════════════════════════════════════════════════════════

    private enum StatusKind { NEUTRAL, SUCCESS, WARNING, BLOCKED }

    private void setStatus(String text, StatusKind kind) {
        if (!isAdded()) return;
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
            case BLOCKED:
                // Use your "danger/red" drawable here – or reuse warning if you
                // don't have one yet. Add bg_status_chip_blocked to your drawables.
                chipBg = R.drawable.bg_status_chip_warning; // replace with _blocked
                dotBg  = R.drawable.bg_status_dot_warning;  // replace with _blocked
                break;
            default:
                chipBg = R.drawable.bg_status_chip_neutral;
                dotBg  = R.drawable.bg_status_dot_neutral;
                break;
        }
        statusChip.setBackgroundResource(chipBg);
        statusDot.setBackgroundResource(dotBg);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ANIMATIONS
    // ═══════════════════════════════════════════════════════════════════

    private void animateQrIn() {
        Animation popIn = AnimationUtils.loadAnimation(getContext(), R.anim.qr_pop_in);
        qrCodeImageView.startAnimation(popIn);

        generateQrButton.setVisibility(View.GONE);
        disconnectButton.setAlpha(0f);
        disconnectButton.setTranslationY(40f);
        disconnectButton.setVisibility(View.VISIBLE);
        disconnectButton.animate()
                .alpha(1f).translationY(0f)
                .setDuration(300).setStartDelay(180)
                .setInterpolator(new DecelerateInterpolator()).start();

        statusChip.setAlpha(0f);
        statusChip.setTranslationY(24f);
        statusChip.animate()
                .alpha(1f).translationY(0f)
                .setDuration(280).setStartDelay(120)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void animateResetIn() {
        generateQrButton.setAlpha(0f);
        generateQrButton.setVisibility(View.VISIBLE);
        generateQrButton.animate().alpha(1f).setDuration(250)
                .setInterpolator(new DecelerateInterpolator()).start();
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

    // ═══════════════════════════════════════════════════════════════════
    //  INIT
    // ═══════════════════════════════════════════════════════════════════

    private void initializeViews(View view) {
        qrCodeImageView  = view.findViewById(R.id.qrCodeImageView);
        generateQrButton = view.findViewById(R.id.generateQrButton);
        disconnectButton = view.findViewById(R.id.disconnectButton);
        statusTextView   = view.findViewById(R.id.statusTextView);
        qrPlaceholder    = view.findViewById(R.id.qrPlaceholder);
        statusChip       = view.findViewById(R.id.statusChip);
        statusDot        = view.findViewById(R.id.statusDot);

        disconnectButton.setVisibility(View.GONE);
        qrCodeImageView.setVisibility(View.GONE);
        qrPlaceholder.setVisibility(View.VISIBLE);
        setStatus("Waiting to connect", StatusKind.NEUTRAL);
    }

    private void setupFirebase() {
        deviceId    = Settings.Secure.getString(
                getContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        databaseRef = FirebaseDatabase.getInstance().getReference("registered_devices");
    }

    private void setupListeners() {
        generateQrButton.setOnClickListener(v -> generateAndDisplayQRCode());
        disconnectButton.setOnClickListener(v -> {
            // ── Phase 4 guard: block cannot be bypassed via disconnect ──
            if (isCurrentlyBlocked) {
                Toast.makeText(getContext(),
                        "Cannot disconnect while device is blocked.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            disconnectController();
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════
    //  RESTORE SAVED SESSION
    // ═══════════════════════════════════════════════════════════════════

    private void restoreSavedConnection() {
        final String saved = getStoredConnectionCode();
        if (saved == null) return;

        final DatabaseReference savedRef = databaseRef.child(saved);
        savedRef.keepSynced(true);

        savedRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                if (!snapshot.exists()) {
                    clearSavedConnectionCode();
                    return;
                }

                try {
                    currentConnectionCode = saved;

                    MultiFormatWriter writer = new MultiFormatWriter();
                    BitMatrix bitMatrix = writer.encode(
                            saved, BarcodeFormat.QR_CODE, 500, 500);
                    Bitmap bitmap = new BarcodeEncoder().createBitmap(bitMatrix);

                    qrCodeImageView.setImageBitmap(bitmap);
                    qrCodeImageView.setVisibility(View.VISIBLE);
                    qrPlaceholder.setVisibility(View.GONE);
                    generateQrButton.setVisibility(View.GONE);
                    disconnectButton.setVisibility(View.VISIBLE);

                    Boolean connected = snapshot.child("controllerConnected")
                            .getValue(Boolean.class);
                    Boolean blocked   = snapshot.child("isBlocked")
                            .getValue(Boolean.class);

                    if (Boolean.TRUE.equals(connected)) {
                        stopStatusDotPulse();
                        applyPhase(Boolean.TRUE.equals(blocked));
                    } else {
                        // Phase 2 – waiting for controller
                        setStatus("Waiting for controller to scan…", StatusKind.WARNING);
                        startStatusDotPulse();
                    }

                    savedRef.child("controllerConnected")
                            .onDisconnect().setValue(false);

                    // Mark armed BEFORE attaching the live listener so the
                    // first echo is treated as real data (this is a restore,
                    // not a fresh generate – the data already reflects truth).
                    listenerArmed = true;
                    startListeningForConnection(saved);

                } catch (Exception e) {
                    clearSavedConnectionCode();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    //  GENERATE QR  (Phase 1 → Phase 2)
    // ═══════════════════════════════════════════════════════════════════

    private void generateAndDisplayQRCode() {
        try {
            currentConnectionCode = UUID.randomUUID()
                    .toString().replace("-", "").substring(0, 16);

            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix bitMatrix = writer.encode(
                    currentConnectionCode, BarcodeFormat.QR_CODE, 500, 500);
            Bitmap bitmap = new BarcodeEncoder().createBitmap(bitMatrix);

            qrCodeImageView.setImageBitmap(bitmap);
            qrCodeImageView.setVisibility(View.VISIBLE);
            qrPlaceholder.setVisibility(View.GONE);
            animateQrIn();

            // ── Phase 2 ──────────────────────────────────────────────
            setStatus("Waiting for controller to scan…", StatusKind.WARNING);
            startStatusDotPulse();
            // listenerArmed = false here; the first Firebase echo will be
            // skipped, keeping us visually in Phase 2.
            listenerArmed = false;

            final DatabaseReference newDeviceRef = databaseRef.child(currentConnectionCode);
            newDeviceRef.keepSynced(true);

            FirebaseUser currentUser =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            final String userId = currentUser != null ? currentUser.getUid() : null;

            newDeviceRef.setValue(new DeviceData(
                    "Device " + deviceId.substring(0, 8), false, false)
            ).addOnSuccessListener(aVoid -> {
                if (!isAdded()) return;

                if (userId != null) newDeviceRef.child("userId").setValue(userId);
                newDeviceRef.child("hardwareId").setValue(deviceId);
                newDeviceRef.child("controllerConnected").onDisconnect().setValue(false);

                saveConnectionCode(currentConnectionCode);

                // Arm AFTER the write succeeds so the first listener callback
                // (which echoes back our own write) is always ignored.
                // The listener is added here; Firebase will fire once immediately
                // with {controllerConnected:false, isBlocked:false}. Because
                // listenerArmed is still false at that exact moment, we skip it.
                // On the NEXT update (real controller scan) listenerArmed is true
                // and we transition to Phase 3.
                startListeningForConnection(currentConnectionCode);

            }).addOnFailureListener(e -> {
                if (isAdded())
                    Toast.makeText(getContext(),
                            "Failed to register device", Toast.LENGTH_SHORT).show();
            });

        } catch (WriterException e) {
            Toast.makeText(getContext(),
                    "Error generating QR code", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(),
                    "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FIREBASE LISTENER  (Phase 2 → 3 → 4)
    // ═══════════════════════════════════════════════════════════════════

    private void startListeningForConnection(String connectionCode) {
        // Remove any existing listener first
        if (connectionListener != null && currentConnectionCode != null) {
            databaseRef.child(currentConnectionCode)
                    .removeEventListener(connectionListener);
        }

        DatabaseReference deviceRef = databaseRef.child(connectionCode);

        connectionListener = deviceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                // ── Teacher-side disconnect (node deleted) ─────────────
                if (!snapshot.exists()) {
                    if (connectionListener != null) {
                        deviceRef.removeEventListener(connectionListener);
                        connectionListener = null;
                    }
                    currentConnectionCode = null;
                    listenerArmed = false;
                    isCurrentlyBlocked = false;
                    clearSavedConnectionCode();
                    if (controlListener != null)
                        controlListener.onDeviceControlStatusChanged(false);
                    resetViews();
                    return;
                }

                // ── Skip the first echo after a fresh generate ─────────
                // (listenerArmed is false; we flip it and do nothing else)
                if (!listenerArmed) {
                    listenerArmed = true;
                    // We're now in Phase 2 — keep the status as-is.
                    return;
                }

                // ── Real update from controller ────────────────────────
                Boolean isBlocked   = snapshot.child("isBlocked")
                        .getValue(Boolean.class);
                Boolean isConnected = snapshot.child("controllerConnected")
                        .getValue(Boolean.class);

                if (Boolean.TRUE.equals(isConnected)) {
                    stopStatusDotPulse();
                    disconnectButton.setVisibility(View.VISIBLE);
                    applyPhase(Boolean.TRUE.equals(isBlocked));
                } else {
                    // Controller went offline but node still exists →
                    // fall back to Phase 2
                    setStatus("Waiting for controller to scan…", StatusKind.WARNING);
                    startStatusDotPulse();
                    applyDisconnectButtonState(false); // re-enable
                }

                if (isBlocked != null && controlListener != null)
                    controlListener.onDeviceControlStatusChanged(isBlocked);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded())
                    Toast.makeText(getContext(),
                            "Connection error: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PHASE HELPER
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Applies Phase 3 (unblocked) or Phase 4 (blocked) and manages
     * the disconnect button state accordingly.
     */
    private void applyPhase(boolean blocked) {
        isCurrentlyBlocked = blocked;

        if (blocked) {
            // ── Phase 4: Connected – Device Blocked ───────────────────
            setStatus("Connected — device blocked", StatusKind.BLOCKED);
            applyDisconnectButtonState(true); // grey out + disable
        } else {
            // ── Phase 3: Connected – Device Unblocked ─────────────────
            setStatus("Connected — device unblocked", StatusKind.SUCCESS);
            applyDisconnectButtonState(false); // normal + enabled
        }
    }

    /**
     * Visually disables or re-enables the Disconnect button.
     * Using alpha + setEnabled is the standard Android pattern for
     * a "greyed out" button without changing the tonal style.
     */
    private void applyDisconnectButtonState(boolean disabled) {
        disconnectButton.setEnabled(!disabled);
        disconnectButton.setAlpha(disabled ? 0.38f : 1.0f);
        // 0.38 is Material Design's standard disabled-state opacity
    }

    // ═══════════════════════════════════════════════════════════════════
    //  DISCONNECT (user-initiated)
    // ═══════════════════════════════════════════════════════════════════

    private void disconnectController() {
        if (connectionListener != null && currentConnectionCode != null) {
            DatabaseReference deviceRef = databaseRef.child(currentConnectionCode);
            deviceRef.removeEventListener(connectionListener);
            deviceRef.child("controllerConnected").onDisconnect().cancel();
            deviceRef.removeValue().addOnCompleteListener(task -> {
                connectionListener    = null;
                currentConnectionCode = null;
                listenerArmed         = false;
                isCurrentlyBlocked    = false;
                clearSavedConnectionCode();
                resetViews();
            });
        } else {
            clearSavedConnectionCode();
            resetViews();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RESET
    // ═══════════════════════════════════════════════════════════════════

    private void resetViews() {
        if (!isAdded()) return;
        stopStatusDotPulse();
        isCurrentlyBlocked = false;
        applyDisconnectButtonState(false);
        qrCodeImageView.setVisibility(View.GONE);
        qrPlaceholder.setVisibility(View.VISIBLE);
        disconnectButton.setVisibility(View.GONE);
        setStatus("Waiting to connect", StatusKind.NEUTRAL);
        animateResetIn();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FRAGMENT LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnDeviceControlListener)
            controlListener = (OnDeviceControlListener) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        controlListener = null;
        if (connectionListener != null && currentConnectionCode != null)
            databaseRef.child(currentConnectionCode)
                    .removeEventListener(connectionListener);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  DATA CLASS
    // ═══════════════════════════════════════════════════════════════════

    private static class DeviceData {
        public String deviceName;
        public boolean controllerConnected;
        public boolean isBlocked;

        public DeviceData(String n, boolean c, boolean b) {
            deviceName = n; controllerConnected = c; isBlocked = b;
        }
        public DeviceData() { }
    }
}