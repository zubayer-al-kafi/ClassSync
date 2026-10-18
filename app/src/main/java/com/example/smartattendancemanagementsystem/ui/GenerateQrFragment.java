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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartattendancemanagementsystem.databinding.FragmentGenerateQrBinding;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.ArrayList;
import java.util.List;

public class GenerateQrFragment extends Fragment {

    private FragmentGenerateQrBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private final List<Classroom> teacherClassrooms = new ArrayList<>();
    private Classroom selectedClassroom;

    // Default classroom location coordinates (University Classroom GPS)
    private final double teacherLat = 23.8103;
    private final double teacherLng = 90.4125;

    private final Handler qrRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable qrRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded() && selectedClassroom != null) {
                generateRollingQrBitmap();
                qrRefreshHandler.postDelayed(this, 10000); // Auto-refreshes every 10 seconds
            }
        }
    };

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
        db = FirebaseFirestore.getInstance();

        binding.btnGenerateQr.setOnClickListener(v -> startRollingQr());

        loadTeacherClassrooms();
    }

    private void loadTeacherClassrooms() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String teacherUid = currentUser != null ? currentUser.getUid() : "";

        binding.progressBar.setVisibility(View.VISIBLE);

        List<Classroom> memoryList = ClassroomRepository.getInstance().getTeacherClassrooms(teacherUid);

        db.collection("classrooms")
                .whereEqualTo("teacherId", teacherUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        teacherClassrooms.clear();
                        List<String> names = new ArrayList<>();

                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Classroom c = doc.toObject(Classroom.class);
                            teacherClassrooms.add(c);
                            names.add(c.getClassName() + " (" + c.getClassCode() + ")");
                        }

                        if (teacherClassrooms.isEmpty() && !memoryList.isEmpty()) {
                            teacherClassrooms.addAll(memoryList);
                            for (Classroom c : memoryList) {
                                names.add(c.getClassName() + " (" + c.getClassCode() + ")");
                            }
                        }

                        if (!teacherClassrooms.isEmpty()) {
                            setupDropdown(names);
                        } else {
                            Toast.makeText(requireContext(), "No classrooms found. Please create one first.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        if (!memoryList.isEmpty()) {
                            teacherClassrooms.addAll(memoryList);
                            List<String> names = new ArrayList<>();
                            for (Classroom c : memoryList) {
                                names.add(c.getClassName() + " (" + c.getClassCode() + ")");
                            }
                            setupDropdown(names);
                        }
                    }
                });
    }

    private void setupDropdown(List<String> names) {
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                names
        );
        binding.actClassroom.setAdapter(arrayAdapter);
        binding.actClassroom.setOnItemClickListener((parent, view, position, id) -> {
            selectedClassroom = teacherClassrooms.get(position);
            startRollingQr();
        });

        selectedClassroom = teacherClassrooms.get(0);
        binding.actClassroom.setText(names.get(0), false);
        startRollingQr();
    }

    private void startRollingQr() {
        qrRefreshHandler.removeCallbacks(qrRefreshRunnable);
        qrRefreshHandler.post(qrRefreshRunnable);
    }

    private void generateRollingQrBitmap() {
        if (selectedClassroom == null) return;

        long currentSec = System.currentTimeMillis() / 1000;
        String tokenHash = String.valueOf(selectedClassroom.getClassId().hashCode() ^ currentSec);

        // Dynamic Payload: CLASSSYNC_DYNAMIC:<classId>:<timestampSec>:<lat>:<lng>:<hash>
        String qrPayload = "CLASSSYNC_DYNAMIC:" + selectedClassroom.getClassId() + ":" +
                currentSec + ":" + teacherLat + ":" + teacherLng + ":" + tokenHash;

        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(qrPayload, BarcodeFormat.QR_CODE, 600, 600);
            binding.ivQrCode.setImageBitmap(bitmap);
            binding.cardQrResult.setVisibility(View.VISIBLE);
            binding.tvQrClassTitle.setText(selectedClassroom.getClassName());
            binding.tvQrSubject.setText(selectedClassroom.getSubject() + " (" + selectedClassroom.getClassCode() + ")");
            binding.tvQrInstructions.setText("🔄 Dynamic Rolling QR (Auto-refreshes every 10s to prevent photo sharing).");
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error generating QR code: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        qrRefreshHandler.removeCallbacks(qrRefreshRunnable);
        binding = null;
    }
}
