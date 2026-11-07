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
import com.example.smartattendancemanagementsystem.repository.AttendanceRepository;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.example.smartattendancemanagementsystem.repository.UserRepository;
import com.example.smartattendancemanagementsystem.util.AppClock;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Instructor roll call.
 *
 * Writes go out as batched commits with per-student outcome reporting, instead of a
 * loop of unobserved set() calls followed by an unconditional "success" toast.
 */
public class TeacherMarkAttendanceFragment extends Fragment {

    /** Firestore allows 500 writes per commit; stay under it. */
    private static final int BATCH_SIZE = 450;

    private FragmentTeacherMarkAttendanceBinding binding;
    private StudentAttendanceAdapter adapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration liveAttendanceListener;

    private final ClassroomRepository classrooms = ClassroomRepository.getInstance();
    private final UserRepository users = new UserRepository();

    private final List<Classroom> teacherClassrooms = new ArrayList<>();
    private final List<User> enrolledStudents = new ArrayList<>();

    private Classroom selectedClassroom;
    private String currentDate;
    private String currentSlotId;

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

        currentDate = AppClock.todayIso();
        binding.tvSelectedDate.setText("Date: " + currentDate);

        adapter = new StudentAttendanceAdapter();
        binding.rvStudents.setAdapter(adapter);

        binding.btnSubmitBatch.setOnClickListener(this::submitBatchAttendance);

        loadTeacherClassrooms();
    }

    private void loadTeacherClassrooms() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String teacherUid = currentUser != null ? currentUser.getUid() : "";

        binding.progressBar.setVisibility(View.VISIBLE);

        classrooms.loadMine(teacherUid, true)
                .addOnSuccessListener(fetched -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    applyClassrooms(fetched);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);

                    List<Classroom> cached = classrooms.getTeacherClassrooms(teacherUid);
                    if (!cached.isEmpty()) {
                        applyClassrooms(cached);
                        toast(UiErrors.describe(e, "Showing cached classrooms."));
                    } else {
                        showEmpty(UiErrors.describe(e, "Could not load your classrooms."));
                    }
                });
    }

    private void applyClassrooms(List<Classroom> fetched) {
        teacherClassrooms.clear();
        teacherClassrooms.addAll(fetched);

        if (teacherClassrooms.isEmpty()) {
            showEmpty("No classrooms found. Please create a classroom first.");
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
            loadEnrolledStudents(selectedClassroom);
        });

        selectedClassroom = teacherClassrooms.get(0);
        binding.actClassroom.setText(names.get(0), false);
        loadEnrolledStudents(selectedClassroom);
    }

    private void loadEnrolledStudents(Classroom classroom) {
        if (classroom == null || binding == null) return;

        currentSlotId = AppClock.resolveActiveSlot(classroom.getClassSlots());

        List<String> enrolled = classroom.getEnrolledStudentIds();
        if (enrolled == null || enrolled.isEmpty()) {
            enrolledStudents.clear();
            adapter.setStudents(enrolledStudents);
            showEmpty("No students currently enrolled in " + classroom.getClassName() + ".");
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        users.loadAll(enrolled)
                .addOnSuccessListener(students -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    enrolledStudents.clear();
                    enrolledStudents.addAll(students);
                    sortStudentsByRollNumber(enrolledStudents);

                    adapter.setStudents(enrolledStudents);
                    binding.tvEmptyStudents.setVisibility(enrolledStudents.isEmpty()
                            ? View.VISIBLE : View.GONE);

                    listenToRealtimeAttendance(classroom);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    showEmpty(UiErrors.describe(e, "Could not load the enrolled students."));
                });
    }

    private void listenToRealtimeAttendance(Classroom classroom) {
        detachListener();

        liveAttendanceListener = db.collection("attendance")
                .whereEqualTo("teacherId", classroom.getTeacherId())
                .whereEqualTo("classId", classroom.getClassId())
                .whereEqualTo("date", currentDate)
                .addSnapshotListener((snapshots, error) -> {
                    if (!isAdded() || error != null || snapshots == null) return;

                    Map<String, String> currentMap = adapter.getAttendanceMap();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        AttendanceRecord record = doc.toObject(AttendanceRecord.class);
                        if (record == null || record.getUserId() == null) continue;
                        if (!currentSlotId.equals(record.getSlotId())) continue;
                        if (record.getStatus() == null) continue;
                        currentMap.put(record.getUserId(), record.getStatus());
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void detachListener() {
        if (liveAttendanceListener != null) {
            liveAttendanceListener.remove();
            liveAttendanceListener = null;
        }
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
            toast("Please select a classroom");
            return;
        }

        Map<String, String> statusMap = adapter.getAttendanceMap();
        if (statusMap.isEmpty()) {
            toast("No students to mark attendance for");
            return;
        }

        String slotId = currentSlotId != null
                ? currentSlotId
                : AppClock.resolveActiveSlot(selectedClassroom.getClassSlots());

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSubmitBatch.setEnabled(false);

        List<Task<Void>> commits = new ArrayList<>();
        WriteBatch batch = db.batch();
        int pending = 0;

        for (User student : enrolledStudents) {
            String status = statusMap.get(student.getUid());
            if (status == null) status = "Present";

            String docId = AttendanceRepository.recordId(
                    student.getUid(), selectedClassroom.getClassId(), currentDate, slotId);

            AttendanceRecord record = new AttendanceRecord(
                    docId, student.getUid(), student.getName(),
                    selectedClassroom.getClassId(), selectedClassroom.getClassName(),
                    selectedClassroom.getTeacherId(), currentDate, slotId,
                    status, "Instructor roll call", System.currentTimeMillis());

            // merge() creates the record when it is missing and only rewrites the
            // mutable fields when the instructor corrects an existing entry.
            batch.set(db.collection("attendance").document(docId), record,
                    SetOptions.merge());
            pending++;

            if (pending >= BATCH_SIZE) {
                commits.add(batch.commit());
                batch = db.batch();
                pending = 0;
            }
        }

        if (pending > 0) {
            commits.add(batch.commit());
        }

        final int attempted = enrolledStudents.size();

        Tasks.whenAllComplete(commits)
                .addOnCompleteListener(completed -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnSubmitBatch.setEnabled(true);

                    int failures = 0;
                    String firstError = null;
                    for (Task<Void> commit : commits) {
                        if (!commit.isSuccessful()) {
                            failures++;
                            if (firstError == null) {
                                firstError = UiErrors.describe(commit.getException(),
                                        "A batch could not be written.");
                            }
                        }
                    }

                    if (failures == 0) {
                        toast("Attendance saved for " + attempted + " students.");
                        Navigation.findNavController(view).popBackStack();
                        return;
                    }

                    String message = (attempted - failures) + " of " + attempted
                            + " students saved. " + firstError;
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                });
    }

    private void showEmpty(String message) {
        if (binding == null) return;
        binding.tvEmptyStudents.setText(message);
        binding.tvEmptyStudents.setVisibility(View.VISIBLE);
    }

    private void toast(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        detachListener();
        binding = null;
    }
}
