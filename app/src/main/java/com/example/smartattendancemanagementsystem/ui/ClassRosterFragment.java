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
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.adapter.ClassRosterAdapter;
import com.example.smartattendancemanagementsystem.databinding.FragmentClassRosterBinding;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.model.User;
import com.example.smartattendancemanagementsystem.repository.AttendanceRepository;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.example.smartattendancemanagementsystem.repository.UserRepository;
import com.example.smartattendancemanagementsystem.session.SessionState;
import com.example.smartattendancemanagementsystem.session.SessionViewModel;
import com.example.smartattendancemanagementsystem.util.AttendanceMath;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassRosterFragment extends Fragment {

    private FragmentClassRosterBinding binding;
    private ClassRosterAdapter adapter;
    private SessionViewModel session;

    private final ClassroomRepository classrooms = ClassroomRepository.getInstance();
    private final UserRepository users = new UserRepository();
    private final AttendanceRepository attendance = new AttendanceRepository();

    private final List<Classroom> teacherClassrooms = new ArrayList<>();
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

        session = new ViewModelProvider(requireActivity()).get(SessionViewModel.class);

        adapter = new ClassRosterAdapter();
        binding.rvClassRoster.setAdapter(adapter);

        session.getState().observe(getViewLifecycleOwner(), this::renderSession);
    }

    private void renderSession(SessionState state) {
        if (state == null || binding == null) return;
        if (state.getStatus() != SessionState.Status.READY) return;

        if (!"Instructor".equals(state.normalizedRole())) {
            Toast.makeText(requireContext(),
                    "Class roster and student analytics are visible to course instructors only.",
                    Toast.LENGTH_LONG).show();
            if (getView() != null) {
                Navigation.findNavController(requireView()).popBackStack();
            }
            return;
        }

        loadTeacherClassrooms(state.getUser().getUid());
    }

    private void loadTeacherClassrooms(String teacherUid) {
        binding.progressBar.setVisibility(View.VISIBLE);

        classrooms.loadMine(teacherUid, true)
                .addOnSuccessListener(fetched -> {
                    if (!isAdded()) return;

                    binding.progressBar.setVisibility(View.GONE);
                    teacherClassrooms.clear();
                    teacherClassrooms.addAll(fetched);

                    if (teacherClassrooms.isEmpty()) {
                        showEmpty("No classrooms yet. Create one to see roster analytics.");
                        return;
                    }

                    List<String> names = new ArrayList<>();
                    for (Classroom c : teacherClassrooms) {
                        names.add(c.getClassName() + " (" + c.getClassCode() + ")");
                    }
                    setupDropdown(names);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;

                    binding.progressBar.setVisibility(View.GONE);

                    List<Classroom> cached = classrooms.getTeacherClassrooms(teacherUid);
                    if (cached.isEmpty()) {
                        showEmpty(UiErrors.describe(e, "Could not load your classrooms."));
                        return;
                    }

                    teacherClassrooms.clear();
                    teacherClassrooms.addAll(cached);

                    List<String> names = new ArrayList<>();
                    for (Classroom c : cached) {
                        names.add(c.getClassName() + " (" + c.getClassCode() + ")");
                    }
                    setupDropdown(names);
                    Toast.makeText(requireContext(),
                            UiErrors.describe(e, "Showing cached data."), Toast.LENGTH_SHORT).show();
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
            loadRosterData(selectedClassroom);
        });

        if (!teacherClassrooms.isEmpty()) {
            selectedClassroom = teacherClassrooms.get(0);
            binding.actClassroom.setText(names.get(0), false);
            loadRosterData(selectedClassroom);
        }
    }

    /**
     * Loads students and attendance together, then computes the percentage from the
     * records. Students are fetched by document id so a roster larger than the
     * whereIn() cap is still complete.
     */
    private void loadRosterData(Classroom classroom) {
        if (classroom == null || binding == null) return;

        adapter.setThreshold(classroom.getMinThreshold());

        List<String> enrolled = classroom.getEnrolledStudentIds();
        if (enrolled == null || enrolled.isEmpty()) {
            rosterStudents.clear();
            percentageMap.clear();
            adapter.setRosterData(rosterStudents, percentageMap);
            showEmpty("No students enrolled in " + classroom.getClassName() + " yet.");
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvEmptyRoster.setVisibility(View.GONE);

        String teacherId = classroom.getTeacherId() != null
                ? classroom.getTeacherId()
                : session.getUid();

        Tasks.whenAllSuccess(
                        users.loadAll(enrolled),
                        attendance.forClass(teacherId, classroom.getClassId()))
                .addOnSuccessListener(results -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);

                    @SuppressWarnings("unchecked")
                    List<User> students = (List<User>) results.get(0);
                    @SuppressWarnings("unchecked")
                    List<com.example.smartattendancemanagementsystem.model.AttendanceRecord> records =
                            (List<com.example.smartattendancemanagementsystem.model.AttendanceRecord>) results.get(1);

                    AttendanceMath summary = AttendanceMath.summarize(records);

                    rosterStudents.clear();
                    rosterStudents.addAll(students);
                    sortStudentsByRollNumber(rosterStudents);

                    percentageMap.clear();
                    for (User student : rosterStudents) {
                        percentageMap.put(student.getUid(), summary.percentageFor(student.getUid()));
                    }

                    adapter.setRosterData(rosterStudents, percentageMap);

                    if (rosterStudents.isEmpty()) {
                        showEmpty("No enrolled student records were found for this classroom.");
                    } else {
                        binding.tvEmptyRoster.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    showEmpty(UiErrors.describe(e, "Could not load the roster."));
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

    private void showEmpty(String message) {
        if (binding == null) return;
        binding.tvEmptyRoster.setText(message);
        binding.tvEmptyRoster.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
