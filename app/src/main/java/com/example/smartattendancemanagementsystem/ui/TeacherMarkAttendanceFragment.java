package com.example.smartattendancemanagementsystem.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.adapter.StudentAttendanceAdapter;
import com.example.smartattendancemanagementsystem.databinding.FragmentTeacherMarkAttendanceBinding;
import com.example.smartattendancemanagementsystem.model.AttendanceRecord;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.model.User;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TeacherMarkAttendanceFragment extends Fragment {

    private FragmentTeacherMarkAttendanceBinding binding;
    private StudentAttendanceAdapter adapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration liveAttendanceListener;

    private final List<Classroom> classrooms = new ArrayList<>();
    private final List<User> enrolledStudents = new ArrayList<>();
    private Classroom selectedClassroom;
    private String currentDateStr;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentTeacherMarkAttendanceBinding.inflate(inflater, container, false);
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

        adapter = new StudentAttendanceAdapter();
        binding.rvStudents.setAdapter(adapter);

        binding.btnSubmitBatch.setOnClickListener(this::submitBatchAttendance);

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
                        classrooms.clear();
                        List<String> names = new ArrayList<>();

                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Classroom c = doc.toObject(Classroom.class);
                            classrooms.add(c);
                            names.add(c.getClassName() + " (" + c.getClassCode() + ")");
                        }

                        if (classrooms.isEmpty()) {
                            classrooms.addAll(memoryList);
                            for (Classroom c : memoryList) {
                                names.add(c.getClassName() + " (" + c.getClassCode() + ")");
                            }
                        }

                        if (!classrooms.isEmpty()) {
                            setupDropdown(names);
                        } else {
                            binding.tvEmptyStudents.setVisibility(View.VISIBLE);
                            binding.tvEmptyStudents.setText("No classrooms found. Please create a classroom first.");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        if (!memoryList.isEmpty()) {
                            classrooms.addAll(memoryList);
                            List<String> names = new ArrayList<>();
                            for (Classroom c : memoryList) {
                                names.add(c.getClassName() + " (" + c.getClassCode() + ")");
                            }
                            setupDropdown(names);
                        } else {
                            binding.tvEmptyStudents.setVisibility(View.VISIBLE);
                            binding.tvEmptyStudents.setText("No classrooms found.");
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
            selectedClassroom = classrooms.get(position);
            loadEnrolledStudents(selectedClassroom);
        });

        selectedClassroom = classrooms.get(0);
        binding.actClassroom.setText(names.get(0), false);
        loadEnrolledStudents(selectedClassroom);
    }

    private void loadEnrolledStudents(Classroom classroom) {
        if (classroom == null || classroom.getEnrolledStudentIds() == null || classroom.getEnrolledStudentIds().isEmpty()) {
            enrolledStudents.clear();
            adapter.setStudents(enrolledStudents);
            binding.tvEmptyStudents.setVisibility(View.VISIBLE);
            binding.tvEmptyStudents.setText("No students currently enrolled in " + (classroom != null ? classroom.getClassName() : "class") + ".");
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        db.collection("users")
                .whereIn("uid", classroom.getEnrolledStudentIds())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        enrolledStudents.clear();
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            enrolledStudents.add(doc.toObject(User.class));
                        }
                        sortStudentsByRollNumber(enrolledStudents);
                        adapter.setStudents(enrolledStudents);
                        binding.tvEmptyStudents.setVisibility(enrolledStudents.isEmpty() ? View.VISIBLE : View.GONE);
                        listenToRealtimeAttendance(classroom.getClassId());
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        sortStudentsByRollNumber(enrolledStudents);
                        adapter.setStudents(enrolledStudents);
                        binding.tvEmptyStudents.setVisibility(enrolledStudents.isEmpty() ? View.VISIBLE : View.GONE);
                        listenToRealtimeAttendance(classroom.getClassId());
                    }
                });
    }

    private void listenToRealtimeAttendance(String classId) {
        if (liveAttendanceListener != null) {
            liveAttendanceListener.remove();
        }

        liveAttendanceListener = db.collection("attendance")
                .whereEqualTo("classId", classId)
                .whereEqualTo("date", currentDateStr)
                .addSnapshotListener((snapshots, error) -> {
                    if (!isAdded() || error != null || snapshots == null) return;

                    Map<String, String> currentMap = adapter.getAttendanceMap();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        AttendanceRecord record = doc.toObject(AttendanceRecord.class);
                        if (record != null && record.getUserId() != null && record.getStatus() != null) {
                            currentMap.put(record.getUserId(), record.getStatus());
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void sortStudentsByRollNumber(List<User> list) {
        Collections.sort(list, (u1, u2) -> {
            String id1 = u1 != null && u1.getStudentId() != null ? u1.getStudentId() : "";
            String id2 = u2 != null && u2.getStudentId() != null ? u2.getStudentId() : "";

            try {
                long num1 = Long.parseLong(id1.replaceAll("[^0-9]", ""));
                long num2 = Long.parseLong(id2.replaceAll("[^0-9]", ""));
                return Long.compare(num1, num2);
            } catch (Exception e) {
                return id1.compareToIgnoreCase(id2);
            }
        });
    }

    private void submitBatchAttendance(View view) {
        if (selectedClassroom == null) {
            Toast.makeText(requireContext(), "Please select a classroom", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> statusMap = adapter.getAttendanceMap();
        if (statusMap.isEmpty()) {
            Toast.makeText(requireContext(), "No students to mark attendance for", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSubmitBatch.setEnabled(false);

        for (User student : enrolledStudents) {
            String status = statusMap.getOrDefault(student.getUid(), "Present");
            String docId = student.getUid() + "_" + selectedClassroom.getClassId() + "_" + currentDateStr;

            AttendanceRecord record = new AttendanceRecord(
                    docId, student.getUid(), student.getName(), selectedClassroom.getClassId(),
                    selectedClassroom.getClassName(), currentDateStr, status, "Instructor Roll Call", System.currentTimeMillis()
            );

            db.collection("attendance").document(docId).set(record);
        }

        binding.progressBar.setVisibility(View.GONE);
        binding.btnSubmitBatch.setEnabled(true);
        Toast.makeText(requireContext(), "Class attendance submitted successfully!", Toast.LENGTH_LONG).show();
        Navigation.findNavController(view).popBackStack();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (liveAttendanceListener != null) {
            liveAttendanceListener.remove();
            liveAttendanceListener = null;
        }
        binding = null;
    }
}
