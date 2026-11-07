package com.example.smartattendancemanagementsystem.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.FragmentScanQrBinding;
import com.example.smartattendancemanagementsystem.util.LocationHelper;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

/**
 * Scans the instructor's signed QR code and asks the backend to record attendance.
 *
 * Nothing here decides whether the scan is valid: the token, the expiry, the
 * geofence, the enrollment and the device binding are all checked in the
 * markAttendance Cloud Function, because a client check is only a suggestion.
 */
public class ScanQrFragment extends Fragment {

    private static final String TOKEN_PREFIX = "CLASSSYNC_V2:";

    private FragmentScanQrBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFunctions functions;

    private boolean processing;
    private String pendingToken;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        if (!isAdded()) return;
                        if (LocationHelper.hasPermission(requireContext())) {
                            submitScannedToken();
                        } else {
                            resetScanner("Location permission is off. Enable it in Settings to "
                                    + "mark attendance.");
                        }
                    });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentScanQrBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        functions = FirebaseFunctions.getInstance();

        binding.zxingBarcodeScanner.decodeContinuous(result -> {
            if (processing || result == null || result.getText() == null) return;
            processing = true;
            onQrPayload(result.getText().trim());
        });
    }

    private void onQrPayload(String payload) {
        if (payload.startsWith(TOKEN_PREFIX)) {
            pendingToken = payload.substring(TOKEN_PREFIX.length());
            requestLocationAndSubmit();
            return;
        }

        if (payload.startsWith("CLASSSYNC_DYNAMIC:") || payload.startsWith("CLASSSYNC_QR:")) {
            resetScanner("This classroom still shows an old QR code. "
                    + "Ask your instructor to update the app and generate a new one.");
            return;
        }

        resetScanner("That is not a ClassSync attendance code.");
    }

    private void requestLocationAndSubmit() {
        if (!LocationHelper.hasPermission(requireContext())) {
            locationPermissionLauncher.launch(LocationHelper.PERMISSIONS);
            return;
        }
        submitScannedToken();
    }

    private void submitScannedToken() {
        if (!isAdded() || binding == null) return;
        if (pendingToken == null) {
            resetScanner("Nothing to submit. Scan the code again.");
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.zxingBarcodeScanner.pause();

        String deviceId = Settings.Secure.getString(
                requireContext().getContentResolver(), Settings.Secure.ANDROID_ID);

        LocationHelper.currentLocation(requireActivity(), new LocationHelper.Callback() {
            @Override
            public void onLocation(double latitude, double longitude, float accuracyMetres) {
                if (!isAdded() || binding == null) return;
                callMarkAttendance(pendingToken, latitude, longitude, accuracyMetres, deviceId);
            }

            @Override
            public void onFailure(String message) {
                resetScanner(message);
            }
        });
    }

    private void callMarkAttendance(String token, double latitude, double longitude,
                                    float accuracy, String deviceId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("lat", latitude);
        payload.put("lng", longitude);
        payload.put("accuracy", accuracy);
        payload.put("deviceId", deviceId);

        functions.getHttpsCallable("markAttendance")
                .call(payload)
                .addOnSuccessListener(result -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);

                    Object raw = result.getData();
                    String className = "this class";
                    boolean alreadyMarked = false;

                    if (raw instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) raw;
                        if (data.get("className") instanceof String) {
                            className = (String) data.get("className");
                        }
                        alreadyMarked = Boolean.TRUE.equals(data.get("alreadyMarked"));
                    }

                    Toast.makeText(requireContext(),
                            alreadyMarked
                                    ? "You have already marked attendance for this session (" + className + ")."
                                    : "Attendance marked for " + className + ".",
                            Toast.LENGTH_LONG).show();

                    if (getView() != null) {
                        Navigation.findNavController(requireView())
                                .navigate(R.id.action_scanQrFragment_to_attendanceHistoryFragment);
                    }
                })
                .addOnFailureListener(e -> resetScanner(functionError(e)));
    }

    private String functionError(Exception error) {
        String message = UiErrors.describe(error, "Could not mark attendance.");

        if (message.toLowerCase().contains("not found")
                || message.toLowerCase().contains("internal")) {
            return "The attendance service is not deployed yet. "
                    + "Run: firebase deploy --only functions";
        }
        if (UiErrors.PERMISSION.equals(message)) {
            return "This scan was refused. The code may be forged, expired, or for another class.";
        }
        return message;
    }

    /** Re-arms the scanner after showing why the attempt failed. */
    private void resetScanner(String message) {
        if (!isAdded() || binding == null) return;

        processing = false;
        pendingToken = null;
        binding.progressBar.setVisibility(View.GONE);

        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        binding.zxingBarcodeScanner.resume();

        // Brief pause so a still-visible code does not retrigger instantly.
        handler.postDelayed(() -> {
            if (binding != null) binding.zxingBarcodeScanner.resume();
        }, 1500);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && binding.zxingBarcodeScanner != null) {
            binding.zxingBarcodeScanner.resume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (binding != null && binding.zxingBarcodeScanner != null) {
            binding.zxingBarcodeScanner.pause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        binding = null;
    }
}
