package com.example.smartattendancemanagementsystem.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.FragmentMarkAttendanceBinding;
import com.example.smartattendancemanagementsystem.model.AttendanceRecord;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.repository.AttendanceRepository;
import com.example.smartattendancemanagementsystem.util.AppClock;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional self-marking, only when the instructor enabled it for the classroom.
 *
 * Enabling it opens a session window on the classroom document. That window - the
 * running period plus a short grace - is what the security rules check, so once the
 * class is over the button is dead even if the flag is still on.
 */
public class MarkAttendanceFragment extends Fragment {

    private FragmentMarkAttendanceBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private final List<Classroom> joinedClassrooms = new ArrayList<>();
    private Classroom selectedClassroom;
    private String selectedClassId;
    private String currentDate;
    private String currentSlotId;

    private boolean loading;
    private boolean loadedOnce;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentMarkAttendanceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnScanQrCode.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_markAttendanceFragment_to_scanQrFragment));

        binding.btnSubmit.setOnClickListener(this::submitAttendance);

        loadJoinedClassrooms();
    }

    @Override
    public void onResume() {
        super.onResume();
        // The window can open while this screen is alive; re-read it instead of
        // trusting the copy fetched when the screen was created.
        if (loadedOnce && !loading) {
            loadJoinedClassrooms();
        }
    }

    private void loadJoinedClassrooms() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String studentUid = currentUser.getUid();
        loading = true;
        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("classrooms")
                .whereArrayContains("enrolledStudentIds", studentUid)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    loading = false;
                    loadedOnce = true;
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    joinedClassrooms.clear();

                    List<String> names = new ArrayList<>();
                    int selectedIndex = 0;

                    for (QueryDocumentSnapshot doc : querySnapshots) {
                        Classroom classroom = doc.toObject(Classroom.class);
                        if (classroom == null) continue;

                        if (selectedClassId != null && selectedClassId.equals(classroom.getClassId())) {
                            selectedIndex = joinedClassrooms.size();
                        }
                        joinedClassrooms.add(classroom);
                        names.add(classroom.getClassName() + " (" + classroom.getSubject() + ")");
                    }

                    if (joinedClassrooms.isEmpty()) {
                        binding.tvPermissionNotice.setText(
                                "No joined classrooms found. Join a course using a Class Code first.");
                        binding.btnSubmit.setEnabled(false);
                        return;
                    }

                    setupDropdown(names, selectedIndex);
                })
                .addOnFailureListener(e -> {
                    loading = false;
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvPermissionNotice.setText(
                            UiErrors.describe(e, "Could not load your classrooms."));
                    binding.btnSubmit.setEnabled(false);
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
            selectedClassroom = joinedClassrooms.get(position);
            selectedClassId = selectedClassroom.getClassId();
            refreshForSelectedClassroom();
        });

        int index = Math.min(Math.max(defaultIndex, 0), joinedClassrooms.size() - 1);
        selectedClassroom = joinedClassrooms.get(index);
        selectedClassId = selectedClassroom.getClassId();
        binding.actClassroom.setText(names.get(index), false);
        refreshForSelectedClassroom();
    }

    /**
     * Reads the classroom's window first: the period it belongs to decides both the
     * record key and whether the student may write at all.
     */
    private void refreshForSelectedClassroom() {
        Classroom classroom = selectedClassroom;
        if (classroom == null || binding == null) return;

        long now = System.currentTimeMillis();
        boolean sessionOpen = classroom.isSessionOpen(now);

        if (sessionOpen && classroom.getSessionSlotId() != null) {
            currentSlotId = classroom.getSessionSlotId();
            currentDate = classroom.getSessionDate() != null
                    ? classroom.getSessionDate() : AppClock.todayIso();
        } else {
            currentSlotId = AppClock.resolveActiveSlot(classroom.getClassSlots());
            currentDate = AppClock.todayIso();
        }

        binding.tvSelectedDate.setText("Session: " + AppClock.describeSlot(currentSlotId)
                + " · " + (sessionOpen
                ? "open until " + AppClock.timeOfDay(classroom.getSessionEndsAtMillis())
                : "closed"));

        checkDuplicate(classroom, sessionOpen);
    }

    private void checkDuplicate(Classroom classroom, boolean sessionOpen) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String userId = currentUser != null ? currentUser.getUid() : "";

        // One document read on the deterministic key: no composite index needed, and
        // it is the same document the write would target.
        String docId = AttendanceRepository.recordId(
                userId, classroom.getClassId(), currentDate, currentSlotId);

        db.collection("attendance").document(docId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded() || binding == null) return;

                    if (snapshot.exists()) {
                        blockDuplicate(classroom);
                    } else {
                        updatePermissionUi(classroom, sessionOpen);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    // The create rule is still authoritative; show the window state.
                    updatePermissionUi(classroom, sessionOpen);
                    Toast.makeText(requireContext(),
                            UiErrors.describe(e, "Could not check today's record."),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void blockDuplicate(Classroom classroom) {
        binding.tvPermissionNotice.setText("You have already submitted attendance for this session ("
                + classroom.getClassName() + ", " + AppClock.describeSlot(currentSlotId) + ").");
        binding.cardPermissionNotice.setStrokeColor(
                ContextCompat.getColor(requireContext(), R.color.status_present));
        binding.btnScanQrCode.setVisibility(View.GONE);
        setMarkingEnabled(false);
    }

    private void updatePermissionUi(Classroom classroom, boolean sessionOpen) {
        if (binding == null) return;

        if (!classroom.isManualAttendanceAllowed()) {
            binding.tvPermissionNotice.setText("Manual self-marking is DISABLED for "
                    + classroom.getClassName() + ". Scan the live session QR instead.");
            binding.cardPermissionNotice.setStrokeColor(
                    ContextCompat.getColor(requireContext(), R.color.status_absent));
            binding.btnScanQrCode.setVisibility(View.VISIBLE);
            setMarkingEnabled(false);
            return;
        }

        if (!sessionOpen) {
            long endedAt = classroom.getSessionEndsAtMillis();
            String ended = endedAt > 0
                    ? "The period ended at " + AppClock.timeOfDay(endedAt) + ". "
                    : "";

            binding.tvPermissionNotice.setText(ended
                    + "Attendance is closed for this class, so self-marking cannot be used now.");
            binding.cardPermissionNotice.setStrokeColor(
                    ContextCompat.getColor(requireContext(), R.color.status_absent));
            binding.btnScanQrCode.setVisibility(View.GONE);
            setMarkingEnabled(false);
            return;
        }

        binding.tvPermissionNotice.setText("Manual self-marking is open until "
                + AppClock.timeOfDay(classroom.getSessionEndsAtMillis())
                + " for " + classroom.getClassName()
                + " (" + AppClock.describeSlot(currentSlotId) + ").");
        binding.cardPermissionNotice.setStrokeColor(
                ContextCompat.getColor(requireContext(), R.color.secondary));
        binding.btnScanQrCode.setVisibility(View.GONE);
        setMarkingEnabled(true);
    }

    private void setMarkingEnabled(boolean enabled) {
        binding.btnSubmit.setEnabled(enabled);
        binding.rbPresent.setEnabled(enabled);
        binding.rbAbsent.setEnabled(enabled);
        binding.rbLate.setEnabled(enabled);
    }

    private void submitAttendance(View view) {
        if (selectedClassroom == null) {
            toast("Please select a course/classroom");
            return;
        }

        if (!selectedClassroom.isManualAttendanceAllowed()) {
            toast("Manual self-marking is disabled. Please scan the QR code.");
            return;
        }

        if (!selectedClassroom.isSessionOpen(System.currentTimeMillis())) {
            toast("This class session has ended. Attendance is closed.");
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        String userName = currentUser.getDisplayName() != null
                && !currentUser.getDisplayName().isEmpty()
                ? currentUser.getDisplayName()
                : (currentUser.getEmail() != null ? currentUser.getEmail() : "Student");

        String status = "Present";
        int checkedId = binding.rgStatus.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_absent) {
            status = "Absent";
        } else if (checkedId == R.id.rb_late) {
            status = "Late";
        }

        String notes = binding.etNotes.getText() != null
                ? binding.etNotes.getText().toString().trim() : "";

        // Session values, not a fresh guess: the rules require the record to land in
        // the period the instructor opened.
        String slotId = selectedClassroom.getSessionSlotId() != null
                ? selectedClassroom.getSessionSlotId() : currentSlotId;
        String date = selectedClassroom.getSessionDate() != null
                ? selectedClassroom.getSessionDate() : currentDate;

        String docId = AttendanceRepository.recordId(
                userId, selectedClassroom.getClassId(), date, slotId);

        AttendanceRecord record = new AttendanceRecord(
                docId, userId, userName,
                selectedClassroom.getClassId(), selectedClassroom.getClassName(),
                selectedClassroom.getTeacherId(), date, slotId,
                status, notes, System.currentTimeMillis());

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSubmit.setEnabled(false);

        db.collection("attendance").document(docId).set(record)
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnSubmit.setEnabled(true);
                    toast("Attendance submitted for " + selectedClassroom.getClassName() + "!");
                    Navigation.findNavController(view)
                            .navigate(R.id.action_markAttendanceFragment_to_attendanceHistoryFragment);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnSubmit.setEnabled(true);
                    toast(UiErrors.describe(e, "Could not submit attendance."));
                    // The window may have closed between the check and the write.
                    refreshForSelectedClassroom();
                });
    }

    private void toast(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
