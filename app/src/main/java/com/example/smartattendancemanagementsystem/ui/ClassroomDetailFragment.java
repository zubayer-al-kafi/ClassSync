package com.example.smartattendancemanagementsystem.ui;

import android.content.res.ColorStateList;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.FragmentClassroomDetailBinding;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.example.smartattendancemanagementsystem.session.SessionState;
import com.example.smartattendancemanagementsystem.session.SessionViewModel;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class ClassroomDetailFragment extends Fragment {

    private FragmentClassroomDetailBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String classId;
    private Classroom currentClassroom;
    private boolean isInstructor = false;
    private SessionViewModel session;
    private String appliedRole;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentClassroomDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (getArguments() != null) {
            classId = getArguments().getString("classId");
        }

        session = new ViewModelProvider(requireActivity()).get(SessionViewModel.class);

        // The role comes from users/{uid} via the security-rules-protected profile.
        applyRole("Student");
        session.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;
            if (state.getStatus() == SessionState.Status.READY) {
                applyRole(state.normalizedRole());
            }
        });

        setupTabSwitchers();
        setupClickListeners();
        loadClassroomData();
    }

    /** Swaps the tool panels and tab labels for the given role. Idempotent. */
    private void applyRole(String role) {
        if (binding == null) return;

        String normalized = "Instructor".equalsIgnoreCase(role) ? "Instructor" : "Student";
        if (normalized.equals(appliedRole)) return;
        appliedRole = normalized;

        isInstructor = "Instructor".equals(normalized);

        if (isInstructor) {
            binding.layoutInstructorTools.setVisibility(View.VISIBLE);
            binding.layoutStudentTools.setVisibility(View.GONE);
            binding.btnTabPeople.setText("👥 People");
            binding.tvPeopleTitle.setText("👥 Class Roster & Student Analytics");
            binding.tvPeopleSubtitle.setText("View enrolled student roll list and attendance percentages");
            binding.btnTabAttendance.setText("⏱️ Roll Call");
        } else {
            binding.layoutInstructorTools.setVisibility(View.GONE);
            binding.layoutStudentTools.setVisibility(View.VISIBLE);
            binding.btnTabPeople.setText("📊 History");
            binding.tvPeopleTitle.setText("📊 My Attendance History");
            binding.tvPeopleSubtitle.setText("View your personal attendance record log and session dates");
            binding.btnTabAttendance.setText("✍️ Mark Attendance");
        }
    }

    private void setupTabSwitchers() {
        binding.btnTabStream.setOnClickListener(v -> selectTab(1));
        binding.btnTabClasswork.setOnClickListener(v -> selectTab(2));
        binding.btnTabPeople.setOnClickListener(v -> selectTab(3));
        binding.btnTabAttendance.setOnClickListener(v -> selectTab(4));

        selectTab(1); // Default Stream Tab
    }

    private void selectTab(int tabIndex) {
        int primaryColor = ContextCompat.getColor(requireContext(), R.color.primary);
        int surfaceVariantColor = ContextCompat.getColor(requireContext(), R.color.dark_surface_variant);

        binding.btnTabStream.setBackgroundTintList(ColorStateList.valueOf(tabIndex == 1 ? primaryColor : surfaceVariantColor));
        binding.btnTabClasswork.setBackgroundTintList(ColorStateList.valueOf(tabIndex == 2 ? primaryColor : surfaceVariantColor));
        binding.btnTabPeople.setBackgroundTintList(ColorStateList.valueOf(tabIndex == 3 ? primaryColor : surfaceVariantColor));
        binding.btnTabAttendance.setBackgroundTintList(ColorStateList.valueOf(tabIndex == 4 ? primaryColor : surfaceVariantColor));

        binding.sectionStreamHub.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        binding.sectionClassworkHub.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);
        binding.sectionPeopleHub.setVisibility(tabIndex == 3 ? View.VISIBLE : View.GONE);
        binding.sectionAttendanceHub.setVisibility(tabIndex == 4 ? View.VISIBLE : View.GONE);
    }

    private void setupClickListeners() {
        // Instructor Actions
        binding.btnTeacherMark.setOnClickListener(v -> navigateTo(R.id.action_classroomDetailFragment_to_teacherMarkAttendanceFragment));
        binding.btnTeacherQr.setOnClickListener(v -> navigateTo(R.id.action_classroomDetailFragment_to_generateQrFragment));
        binding.btnEditRoutine.setOnClickListener(v -> {
            if (currentClassroom != null && getView() != null) {
                Bundle b = new Bundle();
                b.putString("classId", currentClassroom.getClassId());
                Navigation.findNavController(getView()).navigate(R.id.action_classroomDetailFragment_to_editClassroomFragment, b);
            }
        });

        binding.switchManualMarking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (currentClassroom != null) {
                currentClassroom.setManualAttendanceAllowed(isChecked);
                ClassroomRepository.getInstance().addClassroom(currentClassroom);
                db.collection("classrooms").document(currentClassroom.getClassId()).update("manualAttendanceAllowed", isChecked);
                Toast.makeText(requireContext(), "Manual marking " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
            }
        });

        // Student Actions
        binding.btnStudentScan.setOnClickListener(v -> navigateTo(R.id.action_classroomDetailFragment_to_scanQrFragment));
        binding.btnStudentMark.setOnClickListener(v -> navigateTo(R.id.action_classroomDetailFragment_to_markAttendanceFragment));
        binding.btnUnenroll.setOnClickListener(v -> unenrollStudent());

        // Category Cards Navigation
        binding.cardOpenMaterials.setOnClickListener(v -> openCategory(R.id.action_classroomDetailFragment_to_courseMaterialsFragment));
        binding.cardOpenAssignments.setOnClickListener(v -> openCategory(R.id.action_classroomDetailFragment_to_assignmentsFragment));

        // Restrict Roster to Instructors only; Students see personal Attendance History
        binding.cardOpenPeople.setOnClickListener(v -> {
            if (isInstructor) {
                openCategory(R.id.action_classroomDetailFragment_to_classRosterFragment);
            } else {
                navigateTo(R.id.action_classroomDetailFragment_to_attendanceHistoryFragment);
            }
        });
    }

    private void openCategory(int actionId) {
        if (getView() != null) {
            Bundle b = new Bundle();
            if (currentClassroom != null) {
                b.putString("classId", currentClassroom.getClassId());
            }
            try {
                Navigation.findNavController(getView()).navigate(actionId, b);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Opening section...", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void navigateTo(int actionId) {
        if (getView() != null) {
            try {
                Navigation.findNavController(getView()).navigate(actionId);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Opening section...", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadClassroomData() {
        if (classId == null) return;

        Classroom memoryClass = ClassroomRepository.getInstance().getClassroomById(classId);
        if (memoryClass != null) {
            currentClassroom = memoryClass;
            bindClassroomDetails(currentClassroom);
        }

        db.collection("classrooms").document(classId).get()
                .addOnSuccessListener(doc -> {
                    if (isAdded() && doc.exists()) {
                        Classroom c = doc.toObject(Classroom.class);
                        if (c != null) {
                            currentClassroom = c;
                            bindClassroomDetails(currentClassroom);
                            ensureCodePublished(c);
                        }
                    }
                });
    }

    /**
     * Classrooms created before classCodes/{CODE} existed have no code mapping, so a
     * student's join resolves to nothing even though the code on screen is right. The
     * teacher opening the class republishes it; the rules allow only the class teacher
     * to write that document.
     */
    private void ensureCodePublished(Classroom classroom) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || classroom == null) return;
        if (classroom.getTeacherId() == null || !classroom.getTeacherId().equals(user.getUid())) return;

        final String code = classroom.getClassCode();
        if (code == null || code.trim().isEmpty()) return;

        ClassroomRepository repo = ClassroomRepository.getInstance();
        repo.resolveClassId(code)
                .addOnSuccessListener(mappedClassId -> {
                    if (mappedClassId == null) {
                        repo.registerCode(code, classroom.getClassId());
                    }
                });
    }

    private void bindClassroomDetails(Classroom c) {
        if (c == null) return;
        binding.tvCourseTitle.setText(c.getClassName() != null ? c.getClassName() : "Course");
        binding.tvClassCodeBadge.setText(c.getClassCode() != null ? c.getClassCode() : "");

        String teacher = c.getTeacherName() != null ? c.getTeacherName() : "Instructor";
        binding.tvCourseSubject.setText((c.getSubject() != null ? c.getSubject() : "") + " (" + teacher + ")");

        int enrolled = c.getEnrolledStudentIds() != null ? c.getEnrolledStudentIds().size() : 0;
        binding.tvCourseInfo.setText("Enrolled: " + enrolled + " Students | Min Threshold: " + c.getMinThreshold() + "%");

        binding.switchManualMarking.setOnCheckedChangeListener(null);
        binding.switchManualMarking.setChecked(c.isManualAttendanceAllowed());
        binding.switchManualMarking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            c.setManualAttendanceAllowed(isChecked);
            ClassroomRepository.getInstance().addClassroom(c);
            db.collection("classrooms").document(c.getClassId())
                    .update("manualAttendanceAllowed", isChecked)
                    .addOnFailureListener(e -> {
                        c.setManualAttendanceAllowed(!isChecked);
                        Toast.makeText(requireContext(),
                                UiErrors.describe(e, "Could not change the manual marking setting."),
                                Toast.LENGTH_LONG).show();
                    });
            Toast.makeText(requireContext(), "Manual marking " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });
    }

    private void unenrollStudent() {
        if (currentClassroom == null) return;

        final String targetClassCode = currentClassroom.getClassCode() != null ? currentClassroom.getClassCode() : "";

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 10);

        TextView promptTv = new TextView(requireContext());
        promptTv.setText("To confirm leaving '" + currentClassroom.getClassName() + "', please type the Class Code (" + targetClassCode + ") below:");
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

                    ClassroomRepository.getInstance().unenrollStudent(currentClassroom.getClassId(), studentUid);
                    db.collection("classrooms").document(currentClassroom.getClassId())
                            .update("enrolledStudentIds", FieldValue.arrayRemove(studentUid));

                    Toast.makeText(requireContext(), "Unenrolled from " + currentClassroom.getClassName(), Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(requireView()).popBackStack();
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
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
