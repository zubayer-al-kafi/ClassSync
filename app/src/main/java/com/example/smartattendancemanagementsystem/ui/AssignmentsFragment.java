package com.example.smartattendancemanagementsystem.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.adapter.AssignmentAdapter;
import com.example.smartattendancemanagementsystem.databinding.FragmentAssignmentsBinding;
import com.example.smartattendancemanagementsystem.model.Assignment;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.example.smartattendancemanagementsystem.session.SessionViewModel;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AssignmentsFragment extends Fragment implements AssignmentAdapter.OnAssignmentActionListener {

    private FragmentAssignmentsBinding binding;
    private AssignmentAdapter adapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SessionViewModel session;

    private final ClassroomRepository classrooms = ClassroomRepository.getInstance();

    private final List<Classroom> availableClassrooms = new ArrayList<>();
    private final List<Assignment> assignmentsList = new ArrayList<>();
    private final List<String> addedAssignmentLinks = new ArrayList<>();

    private Classroom selectedClassroom;
    private String targetClassIdFromArgs;
    private boolean isTeacher;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAssignmentsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        session = new ViewModelProvider(requireActivity()).get(SessionViewModel.class);

        if (getArguments() != null) {
            targetClassIdFromArgs = getArguments().getString("classId");
        }

        adapter = new AssignmentAdapter(this, false);
        binding.rvAssignments.setAdapter(adapter);

        binding.btnAddAssignmentLink.setOnClickListener(v -> addAssignmentLink());
        binding.etDueDate.setOnClickListener(v -> openDateTimePickerDialog());
        binding.btnPostAssignment.setOnClickListener(v -> postNewAssignment());

        session.getState().observe(getViewLifecycleOwner(), state -> {
            if (binding == null) return;

            isTeacher = state != null && state.isReady()
                    && "Instructor".equals(state.normalizedRole());
            binding.cardCreateAssignment.setVisibility(isTeacher ? View.VISIBLE : View.GONE);
            adapter.setTeacherMode(isTeacher);
        });

        if (targetClassIdFromArgs != null && !targetClassIdFromArgs.isEmpty()) {
            loadAssignments(targetClassIdFromArgs);
        }

        loadClassrooms();
    }

    private void addAssignmentLink() {
        String inputLink = binding.etAssignmentLinkInput.getText() != null
                ? binding.etAssignmentLinkInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(inputLink)) {
            toast("Please paste a valid URL / link");
            return;
        }

        if (!addedAssignmentLinks.contains(inputLink)) {
            addedAssignmentLinks.add(inputLink);
            binding.etAssignmentLinkInput.setText("");
            renderAssignmentLinksContainer();
        }
    }

    private void renderAssignmentLinksContainer() {
        binding.layoutAddedAssignmentLinksContainer.removeAllViews();

        for (int i = 0; i < addedAssignmentLinks.size(); i++) {
            String link = addedAssignmentLinks.get(i);
            int index = i;

            LinearLayout itemLayout = new LinearLayout(requireContext());
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setPadding(0, 4, 0, 4);

            TextView linkTv = new TextView(requireContext());
            linkTv.setText("#" + (index + 1) + ": " + link);
            linkTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary));
            linkTv.setTextSize(12);
            linkTv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            MaterialButton removeBtn = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.borderlessButtonStyle);
            removeBtn.setText("Remove");
            removeBtn.setPadding(0, 0, 0, 0);

            removeBtn.setOnClickListener(v -> {
                addedAssignmentLinks.remove(index);
                renderAssignmentLinksContainer();
            });

            itemLayout.addView(linkTv);
            itemLayout.addView(removeBtn);
            binding.layoutAddedAssignmentLinksContainer.addView(itemLayout);
        }
    }

    private void openDateTimePickerDialog() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select assignment due date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selection);

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String datePart = dateFormat.format(calendar.getTime());

            MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(23)
                    .setMinute(59)
                    .setTitleText("Select due time")
                    .build();

            timePicker.addOnPositiveButtonClickListener(v -> {
                String timePart = String.format(Locale.getDefault(), "%02d:%02d",
                        timePicker.getHour(), timePicker.getMinute());
                binding.etDueDate.setText(datePart + " " + timePart);
            });

            timePicker.show(getParentFragmentManager(), "TIME_PICKER");
        });

        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    private void loadClassrooms() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String uid = currentUser.getUid();
        boolean instructor = session.isInstructor();

        binding.progressBar.setVisibility(View.VISIBLE);

        classrooms.loadMine(uid, instructor)
                .addOnSuccessListener(fetched -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    availableClassrooms.clear();
                    availableClassrooms.addAll(fetched);

                    List<String> names = new ArrayList<>();
                    int selectedIndex = 0;

                    for (Classroom c : availableClassrooms) {
                        names.add(c.getClassName() + " (" + c.getClassCode() + ")");
                        if (targetClassIdFromArgs != null
                                && targetClassIdFromArgs.equals(c.getClassId())) {
                            selectedIndex = availableClassrooms.size() - 1;
                        }
                    }

                    if (!availableClassrooms.isEmpty()) {
                        setupDropdown(names, selectedIndex);
                    } else {
                        binding.tvEmptyAssignments.setText(
                                "You are not in any classroom yet. Join one with a class code first.");
                        binding.tvEmptyAssignments.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvEmptyAssignments.setText(
                            UiErrors.describe(e, "Could not load your classrooms."));
                    binding.tvEmptyAssignments.setVisibility(View.VISIBLE);
                });
    }

    private void setupDropdown(List<String> names, int defaultIndex) {
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                names
        );
        binding.actClassroom.setAdapter(arrayAdapter);
        binding.actClassroom.setOnItemClickListener((parent, view, position, id) -> {
            selectedClassroom = availableClassrooms.get(position);
            loadAssignments(selectedClassroom.getClassId());
        });

        if (defaultIndex < availableClassrooms.size()) {
            selectedClassroom = availableClassrooms.get(defaultIndex);
            binding.actClassroom.setText(names.get(defaultIndex), false);
            loadAssignments(selectedClassroom.getClassId());
        }
    }

    private void loadAssignments(String classId) {
        if (classId == null || binding == null) return;

        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("assignments")
                .whereEqualTo("classId", classId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    assignmentsList.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Assignment assignment = doc.toObject(Assignment.class);
                        if (assignment != null) assignmentsList.add(assignment);
                    }
                    Collections.sort(assignmentsList,
                            (a1, a2) -> Long.compare(a2.getTimestamp(), a1.getTimestamp()));
                    adapter.setAssignments(assignmentsList);

                    if (assignmentsList.isEmpty()) {
                        binding.tvEmptyAssignments.setText("No assignments posted for this class yet.");
                        binding.tvEmptyAssignments.setVisibility(View.VISIBLE);
                    } else {
                        binding.tvEmptyAssignments.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvEmptyAssignments.setText(
                            UiErrors.describe(e, "Could not load the assignments."));
                    binding.tvEmptyAssignments.setVisibility(View.VISIBLE);
                });
    }

    private void postNewAssignment() {
        if (selectedClassroom == null) {
            toast("Select a classroom first");
            return;
        }

        String title = binding.etAssignmentTitle.getText() != null
                ? binding.etAssignmentTitle.getText().toString().trim() : "";
        String instructions = binding.etAssignmentInstructions.getText() != null
                ? binding.etAssignmentInstructions.getText().toString().trim() : "";
        String dueDate = binding.etDueDate.getText() != null
                ? binding.etDueDate.getText().toString().trim() : "";
        String maxMarksStr = binding.etMaxMarks.getText() != null
                ? binding.etMaxMarks.getText().toString().trim() : "100";

        if (TextUtils.isEmpty(title)) {
            toast("Assignment title is required");
            return;
        }

        StringBuilder combinedInstructions = new StringBuilder(instructions);
        if (!addedAssignmentLinks.isEmpty()) {
            combinedInstructions.append("\nResource links: ");
            for (String link : addedAssignmentLinks) {
                combinedInstructions.append(link).append(" ");
            }
        }

        int maxMarks = 100;
        try {
            maxMarks = Integer.parseInt(maxMarksStr);
        } catch (NumberFormatException ignored) {
        }

        if (TextUtils.isEmpty(dueDate)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            dueDate = sdf.format(new Date(System.currentTimeMillis() + 7L * 24 * 3600 * 1000));
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        String uid = currentUser != null ? currentUser.getUid() : "";

        String assignmentId = db.collection("assignments").document().getId();

        Assignment assignment = new Assignment(
                assignmentId, selectedClassroom.getClassId(), selectedClassroom.getClassName(),
                title, combinedInstructions.toString().trim(), dueDate, maxMarks, uid,
                System.currentTimeMillis()
        );

        binding.progressBar.setVisibility(View.VISIBLE);
        db.collection("assignments").document(assignmentId).set(assignment)
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    binding.etAssignmentTitle.setText("");
                    binding.etAssignmentInstructions.setText("");
                    binding.etDueDate.setText("");
                    addedAssignmentLinks.clear();
                    renderAssignmentLinksContainer();
                    toast("Assignment posted");
                    loadAssignments(selectedClassroom.getClassId());
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(),
                            UiErrors.describe(e, "Could not post the assignment."),
                            Toast.LENGTH_LONG).show();
                });
    }

    @Override
    public void onAssignmentAction(Assignment assignment) {
        if (assignment == null || getView() == null) return;

        Bundle bundle = new Bundle();
        bundle.putString("assignmentId", assignment.getAssignmentId());
        bundle.putString("classId", assignment.getClassId());

        if (isTeacher) {
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_assignmentsFragment_to_gradeSubmissionsFragment, bundle);
        } else {
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_assignmentsFragment_to_submitAssignmentFragment, bundle);
        }
    }

    private void toast(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
