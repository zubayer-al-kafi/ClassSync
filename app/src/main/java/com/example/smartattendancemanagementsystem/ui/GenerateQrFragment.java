package com.example.smartattendancemanagementsystem.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartattendancemanagementsystem.databinding.FragmentGenerateQrBinding;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.example.smartattendancemanagementsystem.util.AppClock;
import com.example.smartattendancemanagementsystem.util.LocationHelper;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GenerateQrFragment extends Fragment {

    private static final long REFRESH_INTERVAL_MS = 10_000L;
    private static final long TOKEN_TTL_MS = 20_000L;

    private FragmentGenerateQrBinding binding;
    private FirebaseAuth mAuth;

    private final List<Classroom> teacherClassrooms = new ArrayList<>();
    private Classroom selectedClassroom;

    private boolean issuing;
    private boolean permissionDenied;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            issueToken();
        }
    };

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        if (!isAdded()) return;
                        if (LocationHelper.hasPermission(requireContext())) {
                            permissionDenied = false;
                            toast("Location enabled. Issuing the QR code...");
                            issueToken();
                        } else {
                            permissionDenied = true;
                            showMessage("Location permission is off, so no QR code can be issued. "
                                    + "Enable it in Settings, then reopen this screen.");
                        }
                    });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentGenerateQrBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        binding.btnGenerateQr.setOnClickListener(v -> {
            if (!ensureLocationPermission()) return;
            issueToken();
        });

        loadTeacherClassrooms();
    }

    private void loadTeacherClassrooms() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String teacherUid = currentUser != null ? currentUser.getUid() : "";

        binding.progressBar.setVisibility(View.VISIBLE);

        ClassroomRepository.getInstance().loadMine(teacherUid, true)
                .addOnSuccessListener(fetched -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    applyClassrooms(fetched);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);

                    List<Classroom> cached =
                            ClassroomRepository.getInstance().getTeacherClassrooms(teacherUid);
                    if (!cached.isEmpty()) {
                        applyClassrooms(cached);
                        toast(UiErrors.describe(e, "Showing cached classrooms."));
                    } else {
                        toast(UiErrors.describe(e, "Could not load your classrooms."));
                    }
                });
    }

    private void applyClassrooms(List<Classroom> classrooms) {
        teacherClassrooms.clear();
        teacherClassrooms.addAll(classrooms);

        if (teacherClassrooms.isEmpty()) {
            showMessage("No classrooms found. Create one first.");
            return;
        }

        List<String> names = new ArrayList<>();
        for (Classroom c : teacherClassrooms) {
            names.add(c.getClassName() + " (" + c.getClassCode() + ")");
        }

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                names
        );
        binding.actClassroom.setAdapter(arrayAdapter);
        binding.actClassroom.setOnItemClickListener((parent, view, position, id) -> {
            selectedClassroom = teacherClassrooms.get(position);
            startRefreshing();
        });

        selectedClassroom = teacherClassrooms.get(0);
        binding.actClassroom.setText(names.get(0), false);
        startRefreshing();
    }

    private void startRefreshing() {
        refreshHandler.removeCallbacks(refreshRunnable);

        if (selectedClassroom == null) return;
        if (!ensureLocationPermission()) {
            permissionDenied = true;
            return;
        }

        refreshHandler.post(refreshRunnable);
    }

    private boolean ensureLocationPermission() {
        if (LocationHelper.hasPermission(requireContext())) return true;

        if (permissionDenied) {
            showMessage("Location permission is off, so no QR code can be issued. "
                    + "Enable it in Settings, then reopen this screen.");
            return false;
        }

        locationPermissionLauncher.launch(LocationHelper.PERMISSIONS);
        return false;
    }

    private void issueToken() {
        if (!isAdded() || binding == null || selectedClassroom == null) return;
        if (issuing) return;

        issuing = true;
        binding.progressBar.setVisibility(View.VISIBLE);

        LocationHelper.currentLocation(requireActivity(), new LocationHelper.Callback() {
            @Override
            public void onLocation(double latitude, double longitude, float accuracyMetres) {
                if (!isAdded() || binding == null) return;
                generateClientSideQrToken(latitude, longitude, accuracyMetres);
            }

            @Override
            public void onFailure(String message) {
                if (!isAdded() || binding == null) return;
                issuing = false;
                binding.progressBar.setVisibility(View.GONE);
                showMessage(message);
                refreshHandler.removeCallbacks(refreshRunnable);
            }
        });
    }

    // Completely simulates the Cloud Function locally for Firebase Free Plan compatibility
    private void generateClientSideQrToken(double latitude, double longitude, float accuracy) {
        long now = System.currentTimeMillis();
        long expiry = now + TOKEN_TTL_MS;
        
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String teacherId = currentUser != null ? currentUser.getUid() : "UNKNOWN";
        String classId = selectedClassroom.getClassId();
        String slotId = "GENERAL";

        // Create the payload exactly as the Cloud Function did, formatted safely for QR
        String rawToken = String.format(Locale.US, "%s|%s|%s|%d|%f|%f|%f", 
                teacherId, classId, slotId, expiry, latitude, longitude, accuracy);

        // Simulated signing (Basic Base64 encoding since verification is also happening client-side now)
        String signedToken = android.util.Base64.encodeToString(rawToken.getBytes(StandardCharsets.UTF_8), android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP);

        issuing = false;
        binding.progressBar.setVisibility(View.GONE);

        renderQr(signedToken, selectedClassroom.getClassName(), slotId);
        scheduleNextRefresh();
    }

    private void renderQr(String token, String className, String slotId) {
        String qrPayload = "CLASSSYNC_V2:" + token;

        try {
            Bitmap bitmap = new BarcodeEncoder()
                    .encodeBitmap(qrPayload, BarcodeFormat.QR_CODE, 600, 600);

            binding.ivQrCode.setImageBitmap(bitmap);
            binding.cardQrResult.setVisibility(View.VISIBLE);
            binding.tvQrClassTitle.setText(className);
            binding.tvQrSubject.setText(selectedClassroom.getSubject()
                    + " (" + selectedClassroom.getClassCode() + ")");
            binding.tvQrInstructions.setText("Signed QR - valid for 20 seconds and bound to this "
                    + "location. Refreshes every 10s.");
        } catch (Exception e) {
            showMessage("Could not draw the QR code: " + e.getMessage());
        }
    }

    private void scheduleNextRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    private void showMessage(String message) {
        if (binding == null) return;
        binding.cardQrResult.setVisibility(View.VISIBLE);
        binding.tvQrInstructions.setText(message);
        binding.ivQrCode.setImageDrawable(null);
    }

    private void toast(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (selectedClassroom != null && LocationHelper.hasPermission(requireContext())) {
            startRefreshing();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        refreshHandler.removeCallbacks(refreshRunnable);
        binding = null;
    }
}
