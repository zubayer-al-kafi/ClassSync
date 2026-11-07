package com.example.smartattendancemanagementsystem.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.FragmentScanQrBinding;
import com.example.smartattendancemanagementsystem.model.AttendanceRecord;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.example.smartattendancemanagementsystem.util.LocationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.nio.charset.StandardCharsets;

public class ScanQrFragment extends Fragment {

    private static final String TOKEN_PREFIX = "CLASSSYNC_V2:";
    private static final double MAX_GEOFENCE_METERS = 50.0;
    private static final float MAX_ACCURACY_METERS = 200.0f;

    private FragmentScanQrBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private boolean processing;
    private String pendingToken;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(requireContext(), "Camera permission is required to scan QR codes.", Toast.LENGTH_LONG).show();
                    if (getView() != null) Navigation.findNavController(getView()).popBackStack();
                }
            });

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
        db = FirebaseFirestore.getInstance();

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        if (binding == null) return;
        binding.zxingBarcodeScanner.decodeContinuous(result -> {
            if (processing || result == null || result.getText() == null) return;
            processing = true;
            onQrPayload(result.getText().trim());
        });
        binding.zxingBarcodeScanner.resume();
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
                processClientSideAttendance(pendingToken, latitude, longitude, accuracyMetres, deviceId);
            }

            @Override
            public void onFailure(String message) {
                resetScanner(message);
            }
        });
    }

    // Completely simulates the Cloud Function locally for Firebase Free Plan compatibility
    private void processClientSideAttendance(String token, double studentLat, double studentLng, float studentAccuracy, String deviceId) {
        if (studentAccuracy > MAX_ACCURACY_METERS) {
            resetScanner("Your GPS signal is too weak (" + studentAccuracy + "m accuracy). Step outside or near a window and try again.");
            return;
        }

        try {
            // Decode the Base64 token
            String decodedRaw = new String(Base64.decode(token, Base64.URL_SAFE), StandardCharsets.UTF_8);
            String[] parts = decodedRaw.split("\\|");

            if (parts.length != 7) {
                resetScanner("This scan was refused. The code is malformed or invalid.");
                return;
            }

            String teacherId = parts[0];
            String classId = parts[1];
            String slotId = parts[2];
            long expiryMillis = Long.parseLong(parts[3]);
            double teacherLat = Double.parseDouble(parts[4]);
            double teacherLng = Double.parseDouble(parts[5]);

            long now = System.currentTimeMillis();

            if (now > expiryMillis) {
                resetScanner("This scan was refused. The QR code has expired.");
                return;
            }

            // Geofence check
            float[] results = new float[1];
            Location.distanceBetween(teacherLat, teacherLng, studentLat, studentLng, results);
            float distance = results[0];

            if (distance > MAX_GEOFENCE_METERS) {
                resetScanner(String.format(java.util.Locale.US, "You are too far from the classroom (%.1f meters away). You must be within %.0f meters to mark attendance.", distance, MAX_GEOFENCE_METERS));
                return;
            }

            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser == null) {
                resetScanner("You must be logged in to mark attendance.");
                return;
            }

            String studentUid = currentUser.getUid();
            String studentName = currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()
                    ? currentUser.getDisplayName() : (currentUser.getEmail() != null ? currentUser.getEmail() : "Student");
            
            String dateString = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date(now));
            String recordId = classId + "_" + studentUid + "_" + dateString;

            Classroom classroom = ClassroomRepository.getInstance().getClassroomById(classId);
            String className = classroom != null ? classroom.getClassName() : "Course";

            if (classroom != null && (classroom.getEnrolledStudentIds() == null || !classroom.getEnrolledStudentIds().contains(studentUid))) {
                resetScanner("You are not enrolled in " + className + ". Please join the class first.");
                return;
            }

            // Write attendance directly to Firestore (Simulating the Cloud Function write)
            AttendanceRecord record = new AttendanceRecord(
                    recordId, studentUid, studentName, classId, className, teacherId,
                    dateString, slotId, "Present", "Marked via Secure QR Code", now
            );
            
            // Also securely bind deviceId to user to stop proxy (Like Cloud function did)
            db.collection("users").document(studentUid).update("deviceId", deviceId);

            db.collection("attendance").document(recordId).set(record)
                    .addOnSuccessListener(aVoid -> {
                        if (!isAdded() || binding == null) return;
                        binding.progressBar.setVisibility(View.GONE);

                        Toast.makeText(requireContext(), "Attendance securely marked for " + className + ".", Toast.LENGTH_LONG).show();

                        if (getView() != null) {
                            Navigation.findNavController(requireView())
                                    .navigate(R.id.action_scanQrFragment_to_attendanceHistoryFragment);
                        }
                    })
                    .addOnFailureListener(e -> resetScanner("Could not save attendance to database: " + e.getMessage()));

        } catch (Exception e) {
            resetScanner("This scan was refused. The code may be forged or corrupted.");
        }
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
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                binding.zxingBarcodeScanner.resume();
            }
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
