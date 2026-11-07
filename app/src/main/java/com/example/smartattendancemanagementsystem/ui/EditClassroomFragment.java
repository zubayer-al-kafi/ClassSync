package com.example.smartattendancemanagementsystem.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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
import com.example.smartattendancemanagementsystem.databinding.FragmentEditClassroomBinding;
import com.example.smartattendancemanagementsystem.model.ClassSlot;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditClassroomFragment extends Fragment {

    private FragmentEditClassroomBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private static final String[] DAYS = new String[]{
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
    };

    private final List<ClassSlot> activeSlots = new ArrayList<>();
    private String classroomId;
    private Classroom currentClassroom;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentEditClassroomBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (getArguments() != null) {
            classroomId = getArguments().getString("classId");
        }

        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                DAYS
        );
        binding.actDaySelector.setAdapter(dayAdapter);
        binding.actDaySelector.setText(DAYS[0], false);

        binding.etSlotStart.setOnClickListener(v -> openTimePicker(binding.etSlotStart, "Select Start Time"));
        binding.etSlotEnd.setOnClickListener(v -> openTimePicker(binding.etSlotEnd, "Select End Time"));

        binding.btnAddSlot.setOnClickListener(v -> addClassSlot());
        binding.btnSaveClassroom.setOnClickListener(this::saveClassroomSettings);
        binding.btnDeleteClassroom.setOnClickListener(v -> confirmAndDeleteClassroom());

        loadClassroomData();
    }

    private void openTimePicker(TextView targetView, String title) {
        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(9)
                .setMinute(0)
                .setTitleText(title)
                .build();

        timePicker.addOnPositiveButtonClickListener(v -> {
            String selectedTime = String.format(Locale.getDefault(), "%02d:%02d", timePicker.getHour(), timePicker.getMinute());
            targetView.setText(selectedTime);
        });

        timePicker.show(getParentFragmentManager(), "TIME_PICKER");
    }

    private void loadClassroomData() {
        if (classroomId == null) return;

        Classroom memoryClass = ClassroomRepository.getInstance().getClassroomById(classroomId);
        if (memoryClass != null) {
            currentClassroom = memoryClass;
            bindClassroomToUi(currentClassroom);
        }

        db.collection("classrooms").document(classroomId).get()
                .addOnSuccessListener(doc -> {
                    if (isAdded() && doc.exists()) {
                        Classroom c = doc.toObject(Classroom.class);
                        if (c != null) {
                            currentClassroom = c;
                            bindClassroomToUi(currentClassroom);
                        }
                    }
                });
    }

    private void bindClassroomToUi(Classroom c) {
        if (c == null) return;
        binding.etClassName.setText(c.getClassName());
        binding.etSubject.setText(c.getSubject());
        binding.etMinThreshold.setText(String.valueOf(c.getMinThreshold()));
        binding.switchManualAllowed.setChecked(c.isManualAttendanceAllowed());

        activeSlots.clear();
        if (c.getClassSlots() != null) {
            activeSlots.addAll(c.getClassSlots());
        }
        renderSlotsContainer();
    }

    private void addClassSlot() {
        String day = binding.actDaySelector.getText() != null ? binding.actDaySelector.getText().toString().trim() : "MONDAY";
        String start = binding.etSlotStart.getText() != null ? binding.etSlotStart.getText().toString().trim() : "";
        String end = binding.etSlotEnd.getText() != null ? binding.etSlotEnd.getText().toString().trim() : "";

        if (TextUtils.isEmpty(start) || TextUtils.isEmpty(end)) {
            Toast.makeText(requireContext(), "Please select valid start and end times", Toast.LENGTH_SHORT).show();
            return;
        }

        ClassSlot newSlot = new ClassSlot(day, start, end);
        activeSlots.add(newSlot);
        renderSlotsContainer();
        
        binding.etSlotStart.setText("");
        binding.etSlotEnd.setText("");
        Toast.makeText(requireContext(), "Added " + day + " (" + start + " - " + end + ") slot", Toast.LENGTH_SHORT).show();
    }

    private void renderSlotsContainer() {
        binding.layoutSlotsContainer.removeAllViews();

        if (activeSlots.isEmpty()) {
            TextView emptyTv = new TextView(requireContext());
            emptyTv.setText(getString(R.string.no_slots_added_yet));
            emptyTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            emptyTv.setTextSize(13);
            binding.layoutSlotsContainer.addView(emptyTv);
            return;
        }

        int slotNum = 1;
        for (ClassSlot slot : activeSlots) {
            final int currentPos = slotNum - 1;

            MaterialCardView card = new MaterialCardView(requireContext());
            card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.dark_surface_variant));
            card.setRadius(16);

            View view = getLayoutInflater().inflate(R.layout.item_schedule_class, binding.layoutSlotsContainer, false);
            TextView nameTv = view.findViewById(R.id.tv_class_name);
            TextView subjTv = view.findViewById(R.id.tv_subject);
            TextView timeTv = view.findViewById(R.id.tv_time_range);
            TextView badgeTv = view.findViewById(R.id.tv_status_badge);
            View actionsLayout = view.findViewById(R.id.layout_actions);

            nameTv.setText(slot.getDayOfWeek());
            subjTv.setText(getString(R.string.weekly_routine_slot_title, slotNum));
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

    private void saveClassroomSettings(View view) {
        String className = binding.etClassName.getText() != null ? binding.etClassName.getText().toString().trim() : "";
        String subject = binding.etSubject.getText() != null ? binding.etSubject.getText().toString().trim() : "";
        String thresholdStr = binding.etMinThreshold.getText() != null ? binding.etMinThreshold.getText().toString().trim() : "75";
        boolean manualAllowed = binding.switchManualAllowed.isChecked();

        if (TextUtils.isEmpty(className)) {
            binding.tilClassName.setError("Class Name is required");
            return;
        }

        int parsedThreshold = 75;
        try {
            parsedThreshold = Integer.parseInt(thresholdStr);
        } catch (NumberFormatException ignored) {
        }
        // Captured by the success listener below, so it must be effectively final.
        final int minThreshold = parsedThreshold;

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Editing must never resurrect a document from partial state: if the class
        // has not loaded yet there is no safe way to merge, so refuse instead of
        // writing a rebuilt copy that would drop the join code and the roster.
        if (currentClassroom == null || classroomId == null) {
            Toast.makeText(requireContext(),
                    "This classroom is still loading. Try again in a moment.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Field-scoped update. Only the editable settings are sent; classCode,
        // enrolledStudentIds, teacherId and createdAt are left untouched.
        List<ClassSlot> slots = new ArrayList<>(activeSlots);

        Map<String, Object> changes = new HashMap<>();
        changes.put("className", className);
        changes.put("subject", subject);
        changes.put("minThreshold", minThreshold);
        changes.put("manualAttendanceAllowed", manualAllowed);
        changes.put("classSlots", slots);

        binding.btnSaveClassroom.setEnabled(false);

        db.collection("classrooms").document(classroomId).update(changes)
                .addOnSuccessListener(aVoid -> {
                    currentClassroom.setClassName(className);
                    currentClassroom.setSubject(subject);
                    currentClassroom.setMinThreshold(minThreshold);
                    currentClassroom.setManualAttendanceAllowed(manualAllowed);
                    currentClassroom.setClassSlots(slots);
                    ClassroomRepository.getInstance().addClassroom(currentClassroom);

                    if (!isAdded() || binding == null) return;
                    binding.btnSaveClassroom.setEnabled(true);
                    Toast.makeText(requireContext(),
                            "Classroom routine & settings saved.", Toast.LENGTH_LONG).show();
                    Navigation.findNavController(view).popBackStack();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.btnSaveClassroom.setEnabled(true);
                    Toast.makeText(requireContext(),
                            UiErrors.describe(e, "Could not save these settings."),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void confirmAndDeleteClassroom() {
        if (currentClassroom == null) return;

        final String targetClassCode = currentClassroom.getClassCode() != null ? currentClassroom.getClassCode() : "CODE";

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 10);

        TextView promptTv = new TextView(requireContext());
        promptTv.setText("This action is PERMANENT.\n\nTo confirm deletion, please type the Class Code (" + targetClassCode + ") below:");
        promptTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        promptTv.setTextSize(14);

        final EditText etConfirm = new EditText(requireContext());
        etConfirm.setHint("Enter Class Code (" + targetClassCode + ")");
        etConfirm.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        layout.addView(promptTv);
        layout.addView(etConfirm);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ Confirm Classroom Deletion")
                .setView(layout)
                .setPositiveButton("Delete Classroom", (d, which) -> {
                    ClassroomRepository.getInstance().deleteClassroom(currentClassroom.getClassId());
                    ClassroomRepository.getInstance().releaseCode(currentClassroom.getClassCode());
                    db.collection("classrooms").document(currentClassroom.getClassId()).delete()
                            .addOnFailureListener(e -> Toast.makeText(requireContext(),
                                    UiErrors.describe(e, "Could not delete this classroom."),
                                    Toast.LENGTH_LONG).show());
                    Toast.makeText(requireContext(), currentClassroom.getClassName() + " deleted.", Toast.LENGTH_SHORT).show();

                    // Closes both EditClassroomFragment and ClassroomDetailFragment, returning to Dashboard
                    if (isAdded() && getView() != null) {
                        Navigation.findNavController(requireView()).popBackStack(R.id.dashboardFragment, false);
                    }
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
