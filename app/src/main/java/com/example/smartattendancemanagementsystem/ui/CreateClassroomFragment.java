package com.example.smartattendancemanagementsystem.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.FragmentCreateClassroomBinding;
import com.example.smartattendancemanagementsystem.model.ClassSlot;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CreateClassroomFragment extends Fragment {

    private FragmentCreateClassroomBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private static final String[] DAYS = new String[]{
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
    };

    private final List<ClassSlot> activeSlots = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentCreateClassroomBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                DAYS
        );
        binding.actDaySelector.setAdapter(dayAdapter);
        binding.actDaySelector.setText(DAYS[0], false);

        binding.btnAddSlot.setOnClickListener(v -> addClassSlot());
        binding.btnCreateClassroom.setOnClickListener(this::createClassroom);

        activeSlots.clear();
        activeSlots.add(new ClassSlot("MONDAY", "09:00", "10:30"));
        renderSlotsContainer();
    }

    private void addClassSlot() {
        String day = binding.actDaySelector.getText() != null ? binding.actDaySelector.getText().toString().trim() : "MONDAY";
        String start = binding.etSlotStart.getText() != null ? binding.etSlotStart.getText().toString().trim() : "09:00";
        String end = binding.etSlotEnd.getText() != null ? binding.etSlotEnd.getText().toString().trim() : "10:30";

        if (TextUtils.isEmpty(start) || TextUtils.isEmpty(end)) {
            Toast.makeText(requireContext(), "Please enter valid start and end times", Toast.LENGTH_SHORT).show();
            return;
        }

        ClassSlot newSlot = new ClassSlot(day, start, end);
        activeSlots.add(newSlot);
        renderSlotsContainer();
        Toast.makeText(requireContext(), "Added " + day + " (" + start + " - " + end + ") slot", Toast.LENGTH_SHORT).show();
    }

    private void renderSlotsContainer() {
        binding.layoutSlotsContainer.removeAllViews();

        if (activeSlots.isEmpty()) {
            TextView emptyTv = new TextView(requireContext());
            emptyTv.setText("No weekly class slots added yet.");
            emptyTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            emptyTv.setTextSize(13);
            binding.layoutSlotsContainer.addView(emptyTv);
            return;
        }

        int slotNum = 1;
        for (ClassSlot slot : activeSlots) {
            final int currentPos = slotNum - 1;

            View view = getLayoutInflater().inflate(R.layout.item_schedule_class, binding.layoutSlotsContainer, false);
            TextView nameTv = view.findViewById(R.id.tv_class_name);
            TextView subjTv = view.findViewById(R.id.tv_subject);
            TextView timeTv = view.findViewById(R.id.tv_time_range);
            TextView badgeTv = view.findViewById(R.id.tv_status_badge);
            View actionsLayout = view.findViewById(R.id.layout_actions);

            nameTv.setText(slot.getDayOfWeek());
            subjTv.setText("Weekly Routine Slot #" + slotNum);
            timeTv.setText("⏰ Time: " + slot.getStartTime() + " - " + slot.getEndTime());
            badgeTv.setText("Routine Slot");
            badgeTv.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary));

            actionsLayout.setVisibility(View.VISIBLE);
            MaterialButton btnDeleteSlot = view.findViewById(R.id.btn_primary_action);
            MaterialButton btnSecondary = view.findViewById(R.id.btn_secondary_action);

            btnDeleteSlot.setText("Remove Slot");
            btnDeleteSlot.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_absent));
            btnSecondary.setVisibility(View.GONE);

            btnDeleteSlot.setOnClickListener(v -> {
                if (currentPos < activeSlots.size()) {
                    activeSlots.remove(currentPos);
                    renderSlotsContainer();
                }
            });

            binding.layoutSlotsContainer.addView(view);
            slotNum++;
        }
    }

    private void createClassroom(View view) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        String teacherUid = currentUser.getUid();
        String teacherName = currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()
                ? currentUser.getDisplayName()
                : (currentUser.getEmail() != null ? currentUser.getEmail() : "Instructor");

        String className = binding.etClassName.getText() != null ? binding.etClassName.getText().toString().trim() : "";
        String subject = binding.etSubject.getText() != null ? binding.etSubject.getText().toString().trim() : "";
        String thresholdStr = binding.etMinThreshold.getText() != null ? binding.etMinThreshold.getText().toString().trim() : "75";
        boolean manualAllowed = binding.switchManualAllowed.isChecked();

        if (TextUtils.isEmpty(className)) {
            binding.tilClassName.setError("Class Name is required");
            return;
        } else {
            binding.tilClassName.setError(null);
        }

        if (TextUtils.isEmpty(subject)) {
            binding.tilSubject.setError("Subject is required");
            return;
        } else {
            binding.tilSubject.setError(null);
        }

        int minThreshold = 75;
        try {
            minThreshold = Integer.parseInt(thresholdStr);
        } catch (NumberFormatException ignored) {}

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnCreateClassroom.setEnabled(false);

        String classCode = generateClassCode();
        String classId = db.collection("classrooms").document().getId();

        Classroom classroom = new Classroom(
                classId, className, subject, classCode,
                teacherUid, teacherName, minThreshold,
                manualAllowed, new ArrayList<>(activeSlots),
                new ArrayList<>(), System.currentTimeMillis()
        );

        // The join code is published only after the classroom exists: classCodes/{CODE}
        // is what Student joins resolve against, so the two must land together.
        db.collection("classrooms").document(classId)
                .set(classroom)
                .onSuccessTask(aVoid ->
                        ClassroomRepository.getInstance().registerCode(classCode, classId))
                .addOnSuccessListener(aVoid -> {
                    ClassroomRepository.getInstance().addClassroom(classroom);

                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnCreateClassroom.setEnabled(true);
                    Toast.makeText(requireContext(),
                            "Classroom '" + className + "' created. Join code: " + classCode,
                            Toast.LENGTH_LONG).show();

                    if (getView() != null) {
                        Bundle bundle = new Bundle();
                        bundle.putString("classId", classId);
                        try {
                            Navigation.findNavController(getView())
                                    .navigate(R.id.action_createClassroomFragment_to_classroomDetailFragment, bundle);
                        } catch (Exception e) {
                            Navigation.findNavController(getView()).popBackStack();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnCreateClassroom.setEnabled(true);

                    String message = UiErrors.describe(e, "Could not create the classroom.");
                    if (!UiErrors.isOffline(e) && "You do not have permission to do that.".equals(message)) {
                        message = "Only an instructor account can create a classroom.";
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                });
    }

    private String generateClassCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
