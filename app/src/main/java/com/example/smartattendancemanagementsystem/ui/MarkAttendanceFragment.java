package com.example.smartattendancemanagementsystem.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.FragmentMarkAttendanceBinding;
import com.example.smartattendancemanagementsystem.model.AttendanceRecord;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MarkAttendanceFragment extends Fragment {

    private FragmentMarkAttendanceBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private final List<Classroom> joinedClassrooms = new ArrayList<>();
    private Classroom selectedClassroom;
    private String currentDateStr;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentMarkAttendanceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        currentDateStr = sdf.format(new Date());

        binding.tvSelectedDate.setText("Date: " + currentDateStr);

        binding.btnScanQrCode.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_markAttendanceFragment_to_scanQrFragment)
        );

        binding.btnSubmit.setOnClickListener(this::submitAttendance);

        loadJoinedClassrooms();
    }

    private void loadJoinedClassrooms() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String studentUid = currentUser.getUid();

        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("classrooms")
                .whereArrayContains("enrolledStudentIds", studentUid)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        joinedClassrooms.clear();
                        List<String> names = new ArrayList<>();

                        for (QueryDocumentSnapshot doc : querySnapshots) {
                            Classroom classroom = doc.toObject(Classroom.class);
                            joinedClassrooms.add(classroom);
                            names.add(classroom.getClassName() + " (" + classroom.getSubject() + ")");
                        }

                        if (!joinedClassrooms.isEmpty()) {
                            setupDropdown(names);
                        } else {
                            binding.tvPermissionNotice.setText("No joined classrooms found. Join a course using a Class Code first.");
                            binding.btnSubmit.setEnabled(false);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.tvPermissionNotice.setText("No joined classrooms found. Join a course using a Class Code first.");
                        binding.btnSubmit.setEnabled(false);
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
            selectedClassroom = joinedClassrooms.get(position);
            checkDuplicateAndPermission(selectedClassroom);
        });

        selectedClassroom = joinedClassrooms.get(0);
        binding.actClassroom.setText(names.get(0), false);
        checkDuplicateAndPermission(selectedClassroom);
    }

    private void checkDuplicateAndPermission(Classroom classroom) {
        if (classroom == null) return;

        FirebaseUser currentUser = mAuth.getCurrentUser();
        String userId = currentUser != null ? currentUser.getUid() : "";
        String docId = userId + "_" + classroom.getClassId() + "_" + currentDateStr;

        db.collection("attendance").document(docId).get()
                .addOnSuccessListener(doc -> {
                    if (isAdded()) {
                        if (doc.exists()) {
                            binding.tvPermissionNotice.setText("✅ You have ALREADY submitted attendance for today's session (" + classroom.getClassName() + "). Duplicate attendance is blocked.");
                            binding.cardPermissionNotice.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.status_present));
                            binding.btnScanQrCode.setVisibility(View.GONE);
                            binding.btnSubmit.setEnabled(false);
                            binding.rbPresent.setEnabled(false);
                            binding.rbAbsent.setEnabled(false);
                            binding.rbLate.setEnabled(false);
                        } else {
                            updatePermissionUi(classroom);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        updatePermissionUi(classroom);
                    }
                });
    }

    private void updatePermissionUi(Classroom classroom) {
        if (classroom == null) return;

        boolean allowed = classroom.isManualAttendanceAllowed();
        String start = classroom.getStartTime() != null ? classroom.getStartTime() : "09:00";
        String end = classroom.getEndTime() != null ? classroom.getEndTime() : "18:00";

        if (allowed) {
            binding.tvPermissionNotice.setText("🔓 Manual self-marking is ENABLED for " + classroom.getClassName() + " (" + start + " - " + end + ").");
            binding.cardPermissionNotice.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.secondary));
            binding.btnScanQrCode.setVisibility(View.GONE);

            binding.btnSubmit.setEnabled(true);
            binding.rbPresent.setEnabled(true);
            binding.rbAbsent.setEnabled(true);
            binding.rbLate.setEnabled(true);
        } else {
            binding.tvPermissionNotice.setText("🔒 Manual self-marking is DISABLED for " + classroom.getClassName() + ". Please scan session QR code (" + start + " - " + end + ").");
            binding.cardPermissionNotice.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.status_absent));
            binding.btnScanQrCode.setVisibility(View.VISIBLE);

            binding.btnSubmit.setEnabled(false);
            binding.rbPresent.setEnabled(false);
            binding.rbAbsent.setEnabled(false);
            binding.rbLate.setEnabled(false);
        }
    }

    private void submitAttendance(View view) {
        if (selectedClassroom == null) {
            Toast.makeText(requireContext(), "Please select a course/classroom", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!selectedClassroom.isManualAttendanceAllowed()) {
            Toast.makeText(requireContext(), "Manual self-marking is disabled. Please scan QR Code!", Toast.LENGTH_LONG).show();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        String userName = currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()
                ? currentUser.getDisplayName() : (currentUser.getEmail() != null ? currentUser.getEmail() : "Student");

        String status = "Present";
        int checkedId = binding.rgStatus.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_absent) {
            status = "Absent";
        } else if (checkedId == R.id.rb_late) {
            status = "Late";
        }

        final String finalStatus = status;
        String notes = binding.etNotes.getText() != null ? binding.etNotes.getText().toString().trim() : "";

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSubmit.setEnabled(false);

        String docId = userId + "_" + selectedClassroom.getClassId() + "_" + currentDateStr;

        db.collection("attendance").document(docId).get()
                .addOnSuccessListener(existingDoc -> {
                    if (isAdded()) {
                        if (existingDoc.exists()) {
                            binding.progressBar.setVisibility(View.GONE);
                            Toast.makeText(requireContext(), "⚠️ You have ALREADY submitted attendance for today!", Toast.LENGTH_LONG).show();
                            Navigation.findNavController(view).navigate(R.id.action_markAttendanceFragment_to_attendanceHistoryFragment);
                            return;
                        }

                        AttendanceRecord record = new AttendanceRecord(
                                docId, userId, userName, selectedClassroom.getClassId(), selectedClassroom.getClassName(),
                                currentDateStr, finalStatus, notes, System.currentTimeMillis()
                        );

                        db.collection("attendance").document(docId).set(record);
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnSubmit.setEnabled(true);
                        Toast.makeText(requireContext(), "Attendance submitted for " + selectedClassroom.getClassName() + "!", Toast.LENGTH_SHORT).show();
                        Navigation.findNavController(view).navigate(R.id.action_markAttendanceFragment_to_attendanceHistoryFragment);
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
