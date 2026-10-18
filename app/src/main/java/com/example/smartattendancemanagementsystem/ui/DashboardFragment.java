package com.example.smartattendancemanagementsystem.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.adapter.ClassroomAdapter;
import com.example.smartattendancemanagementsystem.adapter.ScheduleAdapter;
import com.example.smartattendancemanagementsystem.databinding.FragmentDashboardBinding;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.model.User;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class DashboardFragment extends Fragment implements ClassroomAdapter.OnClassroomActionListener, ClassroomRepository.OnClassroomDeletedListener {

    private FragmentDashboardBinding binding;
    private ClassroomAdapter teacherClassroomAdapter;
    private ClassroomAdapter studentClassroomAdapter;
    private ScheduleAdapter teacherScheduleAdapter;
    private ScheduleAdapter studentScheduleAdapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration classroomsListener;
    private String activeStudentUid;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupAdapters();

        SharedPreferences prefs = requireContext().getSharedPreferences("app_session", Context.MODE_PRIVATE);
        String sessionRole = prefs.getString("role", null);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            loadUserProfile(currentUser.getUid(), currentUser.getEmail(), sessionRole);
        } else {
            prefs.edit().clear().apply();
            try {
                Navigation.findNavController(view).navigate(R.id.action_dashboardFragment_to_loginFragment);
            } catch (Exception ignored) {}
            return;
        }

        binding.btnCreateClassroomDashboard.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_dashboardFragment_to_createClassroomFragment)
        );

        binding.btnJoinClassroomDashboard.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_dashboardFragment_to_joinClassroomFragment)
        );
    }

    private void setupAdapters() {
        // Teacher Adapters
        teacherClassroomAdapter = new ClassroomAdapter(new ClassroomAdapter.OnClassroomActionListener() {
            @Override
            public void onMarkAttendance(Classroom classroom) {
                openCourseDetail(classroom);
            }

            @Override
            public void onViewRoster(Classroom classroom) {
                openCourseDetail(classroom);
            }

            @Override
            public void onEditClassroom(Classroom classroom) {
                openCourseDetail(classroom);
            }

            @Override
            public void onDeleteClassroom(Classroom classroom) {
                if (classroom != null) {
                    ClassroomRepository.getInstance().deleteClassroom(classroom.getClassId());
                    db.collection("classrooms").document(classroom.getClassId()).delete();
                    Toast.makeText(requireContext(), "Course deleted.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        teacherClassroomAdapter.setTeacherMode(true);
        binding.rvTeacherClassrooms.setAdapter(teacherClassroomAdapter);

        teacherScheduleAdapter = new ScheduleAdapter(new ScheduleAdapter.OnScheduleActionListener() {
            @Override
            public void onPrimaryAction(Classroom classroom) {
                if (getView() != null) {
                    Navigation.findNavController(getView()).navigate(R.id.action_dashboardFragment_to_generateQrFragment);
                }
            }

            @Override
            public void onSecondaryAction(Classroom classroom) {
                if (getView() != null) {
                    Navigation.findNavController(getView()).navigate(R.id.action_dashboardFragment_to_teacherMarkAttendanceFragment);
                }
            }
        }, true);
        binding.rvTeacherSchedule.setAdapter(teacherScheduleAdapter);

        // Student Adapters
        studentClassroomAdapter = new ClassroomAdapter(new ClassroomAdapter.OnClassroomActionListener() {
            @Override
            public void onMarkAttendance(Classroom classroom) {
                openCourseDetail(classroom);
            }

            @Override
            public void onViewRoster(Classroom classroom) {
                openCourseDetail(classroom);
            }

            @Override
            public void onUnenrollClassroom(Classroom classroom) {
                if (classroom != null) {
                    onUnenrollClassroomAction(classroom);
                }
            }
        });
        studentClassroomAdapter.setTeacherMode(false);
        binding.rvJoinedClassrooms.setAdapter(studentClassroomAdapter);

        studentScheduleAdapter = new ScheduleAdapter(new ScheduleAdapter.OnScheduleActionListener() {
            @Override
            public void onPrimaryAction(Classroom classroom) {
                if (getView() != null) {
                    Navigation.findNavController(getView()).navigate(R.id.action_dashboardFragment_to_scanQrFragment);
                }
            }

            @Override
            public void onSecondaryAction(Classroom classroom) {
                if (getView() != null) {
                    Navigation.findNavController(getView()).navigate(R.id.action_dashboardFragment_to_markAttendanceFragment);
                }
            }
        }, false);
        binding.rvStudentSchedule.setAdapter(studentScheduleAdapter);
    }

    private void openCourseDetail(Classroom classroom) {
        if (classroom != null && getView() != null) {
            Bundle bundle = new Bundle();
            bundle.putString("classId", classroom.getClassId());
            Navigation.findNavController(getView()).navigate(R.id.action_dashboardFragment_to_classroomDetailFragment, bundle);
        }
    }

    private void loadUserProfile(String uid, String email, String sessionRole) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (isAdded()) {
                        if (documentSnapshot.exists()) {
                            User user = documentSnapshot.toObject(User.class);
                            if (user != null) {
                                if (user.isInstructor()) {
                                    showTeacherConsole(uid);
                                } else {
                                    showStudentPortal(uid);
                                }
                                return;
                            }
                        }
                        applyFallbackRole(uid, email, sessionRole);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        applyFallbackRole(uid, email, sessionRole);
                    }
                });
    }

    private void applyFallbackRole(String uid, String email, String sessionRole) {
        if ("Instructor".equalsIgnoreCase(sessionRole) || "Teacher".equalsIgnoreCase(sessionRole)) {
            showTeacherConsole(uid);
        } else {
            showStudentPortal(uid);
        }
    }

    private void showTeacherConsole(String teacherUid) {
        binding.layoutTeacherConsole.setVisibility(View.VISIBLE);
        binding.layoutStudentPortal.setVisibility(View.GONE);
        loadTeacherData(teacherUid);
    }

    private void showStudentPortal(String studentUid) {
        this.activeStudentUid = studentUid;
        binding.layoutTeacherConsole.setVisibility(View.GONE);
        binding.layoutStudentPortal.setVisibility(View.VISIBLE);

        ClassroomRepository.getInstance().addClassroomDeletedListener(this);
        loadJoinedClassrooms(studentUid);
    }

    private void loadTeacherData(String teacherUid) {
        List<Classroom> teacherClasses = ClassroomRepository.getInstance().getTeacherClassrooms(teacherUid);
        teacherScheduleAdapter.setScheduledClasses(teacherClasses);
        teacherClassroomAdapter.setClassrooms(teacherClasses);

        db.collection("classrooms")
                .whereEqualTo("teacherId", teacherUid)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    if (isAdded()) {
                        List<Classroom> fetched = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : querySnapshots) {
                            Classroom c = doc.toObject(Classroom.class);
                            fetched.add(c);
                            ClassroomRepository.getInstance().addClassroom(c);
                        }
                        if (!fetched.isEmpty()) {
                            teacherScheduleAdapter.setScheduledClasses(fetched);
                            teacherClassroomAdapter.setClassrooms(fetched);
                        }
                    }
                });
    }

    private void loadJoinedClassrooms(String studentUid) {
        List<Classroom> memoryJoined = ClassroomRepository.getInstance().getStudentJoinedClassrooms(studentUid);
        studentScheduleAdapter.setScheduledClasses(memoryJoined);
        studentClassroomAdapter.setClassrooms(memoryJoined);

        if (classroomsListener != null) {
            classroomsListener.remove();
        }

        classroomsListener = db.collection("classrooms")
                .whereArrayContains("enrolledStudentIds", studentUid)
                .addSnapshotListener((value, error) -> {
                    if (!isAdded()) return;
                    if (error != null || value == null) {
                        studentClassroomAdapter.setClassrooms(memoryJoined);
                        studentScheduleAdapter.setScheduledClasses(memoryJoined);
                        return;
                    }

                    List<Classroom> joinedList = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : value) {
                        Classroom c = doc.toObject(Classroom.class);
                        joinedList.add(c);
                        ClassroomRepository.getInstance().addClassroom(c);
                    }
                    if (joinedList.isEmpty() && memoryJoined.isEmpty()) {
                        studentClassroomAdapter.setClassrooms(new ArrayList<>());
                        studentScheduleAdapter.setScheduledClasses(new ArrayList<>());
                    } else if (joinedList.isEmpty()) {
                        studentClassroomAdapter.setClassrooms(memoryJoined);
                        studentScheduleAdapter.setScheduledClasses(memoryJoined);
                    } else {
                        studentClassroomAdapter.setClassrooms(joinedList);
                        studentScheduleAdapter.setScheduledClasses(joinedList);
                    }
                });
    }

    @Override
    public void onClassroomDeleted(String classId, String className) {
        if (isAdded() && getContext() != null) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("📢 Classroom Notification")
                    .setMessage("Classroom '" + (className != null ? className : "Course") + "' was deleted by the instructor. You have been automatically unenrolled.")
                    .setPositiveButton("OK", null)
                    .show();

            if (activeStudentUid != null) {
                loadJoinedClassrooms(activeStudentUid);
            }
        }
    }

    private void onUnenrollClassroomAction(Classroom classroom) {
        if (classroom == null) return;

        final String targetClassCode = classroom.getClassCode() != null ? classroom.getClassCode() : "";

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 10);

        TextView promptTv = new TextView(requireContext());
        promptTv.setText("To confirm leaving '" + classroom.getClassName() + "', please type the Class Code (" + targetClassCode + ") below:");
        promptTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        promptTv.setTextSize(14);

        final EditText etConfirm = new EditText(requireContext());
        etConfirm.setHint("Enter Class Code (" + targetClassCode + ")");
        etConfirm.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        layout.addView(promptTv);
        layout.addView(etConfirm);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ Confirm Unenrollment")
                .setView(layout)
                .setPositiveButton("Unenroll", (d, which) -> {
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    String studentUid = currentUser != null ? currentUser.getUid() : null;
                    if (studentUid == null) return;

                    ClassroomRepository.getInstance().unenrollStudent(classroom.getClassId(), studentUid);
                    db.collection("classrooms").document(classroom.getClassId())
                            .update("enrolledStudentIds", FieldValue.arrayRemove(studentUid));

                    Toast.makeText(requireContext(), "Unenrolled from " + classroom.getClassName(), Toast.LENGTH_SHORT).show();
                    loadJoinedClassrooms(studentUid);
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        final Button positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveBtn.setEnabled(false);

        etConfirm.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s != null ? s.toString().trim().toUpperCase() : "";
                positiveBtn.setEnabled(targetClassCode.equalsIgnoreCase(input));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    public void onMarkAttendance(Classroom classroom) {
        openCourseDetail(classroom);
    }

    @Override
    public void onViewRoster(Classroom classroom) {
        openCourseDetail(classroom);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ClassroomRepository.getInstance().removeClassroomDeletedListener(this);
        if (classroomsListener != null) {
            classroomsListener.remove();
            classroomsListener = null;
        }
        binding = null;
    }
}
