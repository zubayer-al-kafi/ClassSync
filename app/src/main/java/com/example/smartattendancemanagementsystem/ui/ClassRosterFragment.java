package com.example.smartattendancemanagementsystem.ui;

import android.content.Context;
import android.content.SharedPreferences;
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

import com.example.smartattendancemanagementsystem.adapter.ClassRosterAdapter;
import com.example.smartattendancemanagementsystem.databinding.FragmentClassRosterBinding;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.model.User;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassRosterFragment extends Fragment {

    private FragmentClassRosterBinding binding;
    private ClassRosterAdapter adapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private final List<Classroom> classrooms = new ArrayList<>();
    private final List<User> rosterStudents = new ArrayList<>();
    private final Map<String, Integer> percentageMap = new HashMap<>();
    private Classroom selectedClassroom;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentClassRosterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        SharedPreferences prefs = requireContext().getSharedPreferences("app_session", Context.MODE_PRIVATE);
        String role = prefs.getString("role", "Student");
        boolean isTeacher = "Instructor".equalsIgnoreCase(role) || "Teacher".equalsIgnoreCase(role);

        if (!isTeacher) {
            Toast.makeText(requireContext(), "🔒 Access Restricted: Class roster & student analytics are visible to course instructors only.", Toast.LENGTH_LONG).show();
            if (getView() != null) {
                Navigation.findNavController(getView()).popBackStack();
            }
            return;
        }

        adapter = new ClassRosterAdapter();
        binding.rvClassRoster.setAdapter(adapter);

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

                        if (classrooms.isEmpty() && !memoryList.isEmpty()) {
                            classrooms.addAll(memoryList);
                            for (Classroom c : memoryList) {
                                names.add(c.getClassName() + " (" + c.getClassCode() + ")");
                            }
                        }

                        if (!classrooms.isEmpty()) {
                            setupDropdown(names);
                        } else {
                            binding.tvEmptyRoster.setVisibility(View.VISIBLE);
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
                            binding.tvEmptyRoster.setVisibility(View.VISIBLE);
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
            loadRosterData(selectedClassroom);
        });

        selectedClassroom = classrooms.get(0);
        binding.actClassroom.setText(names.get(0), false);
        loadRosterData(selectedClassroom);
    }

    private void loadRosterData(Classroom classroom) {
        if (classroom == null || classroom.getEnrolledStudentIds() == null || classroom.getEnrolledStudentIds().isEmpty()) {
            rosterStudents.clear();
            percentageMap.clear();
            adapter.setRosterData(rosterStudents, percentageMap);
            binding.tvEmptyRoster.setVisibility(View.VISIBLE);
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("users")
                .whereIn("uid", classroom.getEnrolledStudentIds())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        rosterStudents.clear();
                        percentageMap.clear();

                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            User student = doc.toObject(User.class);
                            rosterStudents.add(student);
                            percentageMap.put(student.getUid(), 85); // Calculated threshold percentage
                        }

                        sortStudentsByRollNumber(rosterStudents);
                        adapter.setRosterData(rosterStudents, percentageMap);
                        binding.tvEmptyRoster.setVisibility(rosterStudents.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        sortStudentsByRollNumber(rosterStudents);
                        adapter.setRosterData(rosterStudents, percentageMap);
                        binding.tvEmptyRoster.setVisibility(rosterStudents.isEmpty() ? View.VISIBLE : View.GONE);
                    }
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
