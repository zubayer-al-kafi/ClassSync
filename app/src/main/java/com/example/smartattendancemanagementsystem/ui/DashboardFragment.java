package com.example.smartattendancemanagementsystem.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.adapter.ClassroomAdapter;
import com.example.smartattendancemanagementsystem.adapter.ScheduleAdapter;
import com.example.smartattendancemanagementsystem.databinding.FragmentDashboardBinding;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.model.User;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.example.smartattendancemanagementsystem.session.SessionState;
import com.example.smartattendancemanagementsystem.session.SessionViewModel;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DashboardFragment extends Fragment
        implements ClassroomAdapter.OnClassroomActionListener, ClassroomRepository.OnClassroomDeletedListener {

    private FragmentDashboardBinding binding;
    private ClassroomAdapter teacherClassroomAdapter;
    private ClassroomAdapter studentClassroomAdapter;
    private ScheduleAdapter teacherScheduleAdapter;
    private ScheduleAdapter studentScheduleAdapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SessionViewModel session;

    private ListenerRegistration classroomsListener;
    private String renderedRole;
    private String renderedUid;

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
        session = new ViewModelProvider(requireActivity()).get(SessionViewModel.class);

        setupAdapters();

        binding.btnCreateClassroomDashboard.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_dashboardFragment_to_createClassroomFragment));

        binding.btnJoinClassroomDashboard.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_dashboardFragment_to_joinClassroomFragment));

        session.getState().observe(getViewLifecycleOwner(), this::renderSession);
    }

    private void renderSession(SessionState state) {
        if (state == null || binding == null) return;

        switch (state.getStatus()) {
            case READY:
                renderConsole(state);
                break;
            case ERROR:
                if (state.getMessage() != null && state.getMessage().contains("profile record is missing")) {
                    promptProfileCompletionInDashboard();
                } else {
                    showBanner(state.getMessage());
                }
                break;
            case SIGNED_OUT:
                detachClassroomsListener();
                ClassroomRepository.getInstance().clearAllData();
                if (getView() != null) {
                    try {
                        Navigation.findNavController(requireView())
                                .navigate(R.id.action_dashboardFragment_to_loginFragment);
                    } catch (Exception ignored) {
                        // Already on the login destination.
                    }
                }
                break;
            default:
                break;
        }
    }

    private void promptProfileCompletionInDashboard() {
        if (!isAdded() || getContext() == null) return;

        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) return;

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        // 1. Name
        final TextInputLayout tilName = new TextInputLayout(requireContext());
        tilName.setHint("Full Name");
        final EditText etName = new EditText(requireContext());
        etName.setText(firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "Student");
        tilName.addView(etName);

        // 2. Role Selector Dropdown
        final TextInputLayout tilRole = new TextInputLayout(requireContext());
        tilRole.setHint("Select Role");
        final AutoCompleteTextView actRole = new AutoCompleteTextView(requireContext());
        actRole.setInputType(android.text.InputType.TYPE_NULL); // Fix: Prevent free typing, acts as proper dropdown
        actRole.setFocusable(false);
        actRole.setClickable(true);
        actRole.setText("Student", false);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, new String[]{"Student", "Instructor"});
        actRole.setAdapter(adapter);
        actRole.setOnClickListener(v -> actRole.showDropDown());
        tilRole.addView(actRole);

        // 3. Student ID / Roll Number (Mandatory for Student)
        final TextInputLayout tilRoll = new TextInputLayout(requireContext());
        tilRoll.setHint("Student ID / Roll Number (e.g. 101, 2021-001)");
        final EditText etRoll = new EditText(requireContext());
        tilRoll.addView(etRoll);

        // 4. Department
        final TextInputLayout tilDept = new TextInputLayout(requireContext());
        tilDept.setHint("Department");
        final EditText etDept = new EditText(requireContext());
        etDept.setText("CSE");
        tilDept.addView(etDept);

        layout.addView(tilName);
        layout.addView(tilRole);
        layout.addView(tilRoll);
        layout.addView(tilDept);

        actRole.setOnItemClickListener((parent, view, position, id) -> {
            boolean isStudentRole = position == 0;
            tilRoll.setVisibility(isStudentRole ? View.VISIBLE : View.GONE);
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("👤 Complete Your Academic Profile")
                .setMessage("Welcome to ClassSync! Please complete your academic details to continue:")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("Complete Setup", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String role = actRole.getText().toString().trim();
                    String roll = etRoll.getText().toString().trim();
                    String dept = etDept.getText().toString().trim();
                    boolean isStudent = "Student".equalsIgnoreCase(role);

                    if (TextUtils.isEmpty(name)) name = "Student";
                    if (isStudent && TextUtils.isEmpty(roll)) roll = "101";

                    String email = firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "";

                    User newUser = new User(firebaseUser.getUid(), name, email, role,
                            isStudent ? roll : null, dept, null, System.currentTimeMillis());

                    db.collection("users").document(firebaseUser.getUid()).set(newUser)
                            .addOnSuccessListener(aVoid -> {
                                if (isAdded()) {
                                    Toast.makeText(requireContext(), "Profile setup complete!", Toast.LENGTH_SHORT).show();
                                    session.reload();
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (isAdded()) {
                                    session.reload();
                                }
                            });
                })
                .show();
    }

    private void renderConsole(SessionState state) {
        String role = state.normalizedRole();
        String uid = state.getUser() != null ? state.getUser().getUid() : null;
        if (uid == null) return;

        if (Objects.equals(role, renderedRole) && Objects.equals(uid, renderedUid)) return;

        renderedRole = role;
        renderedUid = uid;

        if ("Instructor".equals(role)) {
            detachClassroomsListener();
            binding.layoutTeacherConsole.setVisibility(View.VISIBLE);
            binding.layoutStudentPortal.setVisibility(View.GONE);
            loadTeacherData(uid);
        } else {
            binding.layoutTeacherConsole.setVisibility(View.GONE);
            binding.layoutStudentPortal.setVisibility(View.VISIBLE);
            loadStudentData(uid);
        }
    }

    private void showBanner(String message) {
        if (binding == null || message == null) return;
        binding.tvDashboardBanner.setText(message);
        binding.tvDashboardBanner.setVisibility(View.VISIBLE);
    }

    private void setupAdapters() {
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
                if (classroom == null) return;

                ClassroomRepository.getInstance().deleteClassroom(classroom.getClassId());
                db.collection("classrooms").document(classroom.getClassId()).delete()
                        .addOnFailureListener(e -> toast(UiErrors.describe(e, "Could not delete the course.")));
                ClassroomRepository.getInstance().releaseCode(classroom.getClassCode());

                toast("Course deleted.");
                if (renderedUid != null) loadTeacherData(renderedUid);
            }
        });
        teacherClassroomAdapter.setTeacherMode(true);
        binding.rvTeacherClassrooms.setAdapter(teacherClassroomAdapter);

        teacherScheduleAdapter = new ScheduleAdapter(new ScheduleAdapter.OnScheduleActionListener() {
            @Override
            public void onPrimaryAction(Classroom classroom) {
                if (getView() != null) {
                    Navigation.findNavController(getView())
                            .navigate(R.id.action_dashboardFragment_to_generateQrFragment);
                }
            }

            @Override
            public void onSecondaryAction(Classroom classroom) {
                if (getView() != null) {
                    Navigation.findNavController(getView())
                            .navigate(R.id.action_dashboardFragment_to_teacherMarkAttendanceFragment);
                }
            }
        }, true);
        binding.rvTeacherSchedule.setAdapter(teacherScheduleAdapter);

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
                    Navigation.findNavController(getView())
                            .navigate(R.id.action_dashboardFragment_to_scanQrFragment);
                }
            }

            @Override
            public void onSecondaryAction(Classroom classroom) {
                if (getView() != null) {
                    Navigation.findNavController(getView())
                            .navigate(R.id.action_dashboardFragment_to_markAttendanceFragment);
                }
            }
        }, false);
        binding.rvStudentSchedule.setAdapter(studentScheduleAdapter);
    }

    private void openCourseDetail(Classroom classroom) {
        if (classroom != null && getView() != null) {
            Bundle bundle = new Bundle();
            bundle.putString("classId", classroom.getClassId());
            Navigation.findNavController(getView())
                    .navigate(R.id.action_dashboardFragment_to_classroomDetailFragment, bundle);
        }
    }

    private void loadTeacherData(String teacherUid) {
        List<Classroom> cached = ClassroomRepository.getInstance().getTeacherClassrooms(teacherUid);
        teacherScheduleAdapter.setScheduledClasses(cached);
        teacherClassroomAdapter.setClassrooms(cached);

        ClassroomRepository.getInstance().loadMine(teacherUid, true)
                .addOnSuccessListener(classrooms -> {
                    if (!isAdded()) return;
                    teacherScheduleAdapter.setScheduledClasses(classrooms);
                    teacherClassroomAdapter.setClassrooms(classrooms);
                    updateTeacherEmptyState(classrooms.isEmpty());
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    updateTeacherEmptyState(cached.isEmpty());
                    if (cached.isEmpty()) {
                        showBanner(UiErrors.describe(e, "Could not load your courses."));
                    } else {
                        toast(UiErrors.describe(e, "Showing cached courses."));
                    }
                });
    }

    private void updateTeacherEmptyState(boolean empty) {
        if (binding == null) return;
        binding.tvEmptyTeacherClasses.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.tvEmptyTeacherSchedule.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void loadStudentData(String studentUid) {
        ClassroomRepository.getInstance().addClassroomDeletedListener(this);

        List<Classroom> cached = ClassroomRepository.getInstance().getStudentJoinedClassrooms(studentUid);
        applyStudentClassrooms(cached);

        detachClassroomsListener();
        classroomsListener = db.collection("classrooms")
                .whereArrayContains("enrolledStudentIds", studentUid)
                .addSnapshotListener((value, error) -> {
                    if (!isAdded()) return;

                    if (error != null || value == null) {
                        applyStudentClassrooms(cached);
                        showBanner(UiErrors.describe(error, "Could not load your classrooms."));
                        return;
                    }

                    List<Classroom> joined = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : value) {
                        Classroom classroom = doc.toObject(Classroom.class);
                        if (classroom == null) continue;
                        joined.add(classroom);
                        ClassroomRepository.getInstance().addClassroom(classroom);
                    }
                    applyStudentClassrooms(joined);
                });
    }

    private void applyStudentClassrooms(List<Classroom> classrooms) {
        if (binding == null) return;

        studentClassroomAdapter.setClassrooms(classrooms);
        studentScheduleAdapter.setScheduledClasses(classrooms);

        boolean empty = classrooms == null || classrooms.isEmpty();
        binding.tvEmptyJoinedClasses.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.tvEmptyStudentSchedule.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void detachClassroomsListener() {
        if (classroomsListener != null) {
            classroomsListener.remove();
            classroomsListener = null;
        }
    }

    @Override
    public void onClassroomDeleted(String classId, String className) {
        if (!isAdded() || getContext() == null) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Classroom removed")
                .setMessage("Your instructor deleted '"
                        + (className != null ? className : "Course")
                        + "'. You have been unenrolled.")
                .setPositiveButton("OK", null)
                .show();

        if (renderedUid != null) {
            loadStudentData(renderedUid);
        }
    }

    private void onUnenrollClassroomAction(Classroom classroom) {
        if (classroom == null) return;

        final String targetClassCode = classroom.getClassCode() != null ? classroom.getClassCode() : "";

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 10);

        TextView promptTv = new TextView(requireContext());
        promptTv.setText(getString(R.string.unenroll_prompt, classroom.getClassName(), targetClassCode));
        promptTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        promptTv.setTextSize(14);

        final EditText etConfirm = new EditText(requireContext());
        etConfirm.setHint("Enter Class Code (" + targetClassCode + ")");
        etConfirm.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        layout.addView(promptTv);
        layout.addView(etConfirm);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirm unenrollment")
                .setView(layout)
                .setPositiveButton("Unenroll", (d, which) -> {
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    String studentUid = currentUser != null ? currentUser.getUid() : null;
                    if (studentUid == null) return;

                    ClassroomRepository.getInstance().unenrollStudent(classroom.getClassId(), studentUid);
                    db.collection("classrooms").document(classroom.getClassId())
                            .update("enrolledStudentIds", FieldValue.arrayRemove(studentUid))
                            .addOnFailureListener(e ->
                                    toast(UiErrors.describe(e, "Could not leave the classroom.")));

                    toast("Left " + classroom.getClassName());
                    loadStudentData(studentUid);
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        final Button positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveBtn.setEnabled(false);

        etConfirm.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s != null ? s.toString().trim().toUpperCase() : "";
                positiveBtn.setEnabled(targetClassCode.equalsIgnoreCase(input));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
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

    private void toast(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ClassroomRepository.getInstance().removeClassroomDeletedListener(this);
        detachClassroomsListener();
        binding = null;
    }
}
