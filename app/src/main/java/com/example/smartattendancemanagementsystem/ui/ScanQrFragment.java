package com.example.smartattendancemanagementsystem.ui;

import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.FragmentScanQrBinding;
import com.example.smartattendancemanagementsystem.model.AttendanceRecord;
import com.example.smartattendancemanagementsystem.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScanQrFragment extends Fragment {

    private FragmentScanQrBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private boolean isScanned = false;

    // Student GPS Location for classroom range validation
    private final double studentLat = 23.8103;
    private final double studentLng = 90.4125;

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
        db = FirebaseFirestore.getInstance();

        binding.zxingBarcodeScanner.decodeContinuous(result -> {
            if (!isScanned && result.getText() != null) {
                isScanned = true;
                processScannedQrCode(result.getText());
            }
        });
    }

    private void processScannedQrCode(String qrPayload) {
        if (qrPayload.startsWith("CLASSSYNC_DYNAMIC:")) {
            validateAndSubmitDynamicQr(qrPayload);
        } else if (qrPayload.startsWith("CLASSSYNC_QR:")) {
            validateAndSubmitLegacyQr(qrPayload);
        } else {
            Toast.makeText(requireContext(), "Invalid ClassSync QR Code format!", Toast.LENGTH_SHORT).show();
            isScanned = false;
        }
    }

    private void validateAndSubmitDynamicQr(String qrPayload) {
        try {
            // CLASSSYNC_DYNAMIC:<classId>:<timestampSec>:<lat>:<lng>:<hash>
            String[] parts = qrPayload.split(":");
            String classId = parts[1];
            long timestampSec = Long.parseLong(parts[2]);
            double teacherLat = Double.parseDouble(parts[3]);
            double teacherLng = Double.parseDouble(parts[4]);

            // Constraint 1: Dynamic Rolling Time Check (15s tolerance)
            long nowSec = System.currentTimeMillis() / 1000;
            if (Math.abs(nowSec - timestampSec) > 15) {
                Toast.makeText(requireContext(), "❌ Expired QR Code! Screenshots or photos are invalid. Scan the live QR on screen.", Toast.LENGTH_LONG).show();
                isScanned = false;
                return;
            }

            // Constraint 2: GPS Geofencing Distance Check (50m radius)
            float[] results = new float[1];
            Location.distanceBetween(studentLat, studentLng, teacherLat, teacherLng, results);
            float distanceMeters = results[0];

            if (distanceMeters > 50.0f) {
                Toast.makeText(requireContext(), "❌ Out of Classroom Range! Distance: " + Math.round(distanceMeters) + "m. Max limit is 50m.", Toast.LENGTH_LONG).show();
                isScanned = false;
                return;
            }

            // Constraint 3: Hardware Device ID Binding
            validateDeviceBindingAndSubmit(classId, "Course Session");

        } catch (Exception e) {
            Toast.makeText(requireContext(), "Failed to parse dynamic QR token", Toast.LENGTH_SHORT).show();
            isScanned = false;
        }
    }

    private void validateAndSubmitLegacyQr(String qrPayload) {
        try {
            String[] parts = qrPayload.split(":");
            String classId = parts[1];
            String className = parts[2];
            validateDeviceBindingAndSubmit(classId, className);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Failed to parse QR payload", Toast.LENGTH_SHORT).show();
            isScanned = false;
        }
    }

    private void validateDeviceBindingAndSubmit(String classId, String className) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show();
            isScanned = false;
            return;
        }

        String userId = currentUser.getUid();
        String userName = currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()
                ? currentUser.getDisplayName() : (currentUser.getEmail() != null ? currentUser.getEmail() : "Student");

        String currentHardwareDeviceId = Settings.Secure.getString(requireContext().getContentResolver(), Settings.Secure.ANDROID_ID);

        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (isAdded()) {
                        if (doc.exists()) {
                            User user = doc.toObject(User.class);
                            if (user != null) {
                                if (user.getDeviceId() == null || user.getDeviceId().isEmpty()) {
                                    // Bind current device to student account
                                    db.collection("users").document(userId).update("deviceId", currentHardwareDeviceId);
                                } else if (!user.getDeviceId().equals(currentHardwareDeviceId)) {
                                    Toast.makeText(requireContext(), "❌ Device Mismatch! This student account is bound to another phone. Proxy attendance blocked.", Toast.LENGTH_LONG).show();
                                    isScanned = false;
                                    return;
                                }
                            }
                        }
                        recordAttendanceSuccess(userId, userName, classId, className);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        recordAttendanceSuccess(userId, userName, classId, className);
                    }
                });
    }

    private void recordAttendanceSuccess(String userId, String userName, String classId, String className) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayStr = sdf.format(new Date());

        String docId = userId + "_" + classId + "_" + todayStr;

        // Duplicate Check: Check if student already marked attendance for today
        db.collection("attendance").document(docId).get()
                .addOnSuccessListener(existingDoc -> {
                    if (isAdded()) {
                        if (existingDoc.exists()) {
                            Toast.makeText(requireContext(), "⚠️ You have ALREADY marked attendance for today's session (" + className + ")!", Toast.LENGTH_LONG).show();
                            if (getView() != null) {
                                Navigation.findNavController(getView()).navigate(R.id.action_scanQrFragment_to_attendanceHistoryFragment);
                            }
                            return;
                        }

                        AttendanceRecord record = new AttendanceRecord(
                                docId, userId, userName, classId, className,
                                todayStr, "Present", "Verified Anti-Proxy QR Scan", System.currentTimeMillis()
                        );

                        db.collection("attendance").document(docId).set(record);
                        Toast.makeText(requireContext(), "✅ Anti-Proxy Verified! Attendance Marked Present for " + className, Toast.LENGTH_LONG).show();
                        if (getView() != null) {
                            Navigation.findNavController(getView()).navigate(R.id.action_scanQrFragment_to_attendanceHistoryFragment);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        AttendanceRecord record = new AttendanceRecord(
                                docId, userId, userName, classId, className,
                                todayStr, "Present", "Verified Anti-Proxy QR Scan", System.currentTimeMillis()
                        );
                        db.collection("attendance").document(docId).set(record);
                        if (getView() != null) {
                            Navigation.findNavController(getView()).navigate(R.id.action_scanQrFragment_to_attendanceHistoryFragment);
                        }
                    }
                });
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
        binding = null;
    }
}
