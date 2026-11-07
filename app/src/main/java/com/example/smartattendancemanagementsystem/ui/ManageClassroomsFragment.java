package com.example.smartattendancemanagementsystem.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.adapter.ClassroomAdapter;
import com.example.smartattendancemanagementsystem.databinding.FragmentManageClassroomsBinding;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.example.smartattendancemanagementsystem.repository.SessionRepository;
import com.example.smartattendancemanagementsystem.util.AppClock;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManageClassroomsFragment extends Fragment implements ClassroomAdapter.OnClassroomActionListener {

    private FragmentManageClassroomsBinding binding;
    private ClassroomAdapter adapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private final ClassroomRepository classrooms = ClassroomRepository.getInstance();
    private final SessionRepository sessions = new SessionRepository();

    private final List<Classroom> items = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentManageClassroomsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        adapter = new ClassroomAdapter(this);
        adapter.setTeacherMode(true);
        adapter.setShowActionButtons(false);
        binding.rvClassrooms.setAdapter(adapter);

        binding.btnCreateNew.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_manageClassroomsFragment_to_createClassroomFragment));

        loadClassrooms();
    }

    private void loadClassrooms() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String teacherUid = currentUser != null ? currentUser.getUid() : "";

        binding.progressBar.setVisibility(View.VISIBLE);

        classrooms.loadMine(teacherUid, true)
                .addOnSuccessListener(fetched -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    render(fetched, null);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);

                    // Offline: show the last known list, but say why it may be stale.
                    List<Classroom> cached = classrooms.getTeacherClassrooms(teacherUid);
                    render(cached, UiErrors.describe(e, "Could not refresh your classrooms."));
                });
    }

    private void render(List<Classroom> source, String notice) {
        items.clear();
        if (source != null) items.addAll(source);
        adapter.setClassrooms(items);

        if (items.isEmpty()) {
            binding.tvEmptyClassrooms.setText(notice != null
                    ? notice
                    : "No classrooms yet. Create one to get a class code students can join with.");
            binding.tvEmptyClassrooms.setVisibility(View.VISIBLE);
        } else {
            binding.tvEmptyClassrooms.setVisibility(View.GONE);
            if (notice != null) {
                Toast.makeText(requireContext(), notice, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onEditClassroom(Classroom classroom) {
        openDetail(classroom);
    }

    @Override
    public void onDeleteClassroom(Classroom classroom) {
        if (classroom == null || classroom.getClassId() == null) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Classroom")
                .setMessage("Are you sure you want to delete '" + classroom.getClassName()
                        + "'? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteClassroom(classroom))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteClassroom(Classroom classroom) {
        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("classrooms").document(classroom.getClassId())
                .delete()
                .addOnSuccessListener(unused -> {
                    if (!isAdded() || binding == null) return;

                    // Free the join code so it cannot resolve to a deleted classroom later.
                    classrooms.releaseCode(classroom.getClassCode());
                    classrooms.deleteClassroom(classroom.getClassId());

                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(),
                            classroom.getClassName() + " deleted.", Toast.LENGTH_SHORT).show();
                    loadClassrooms();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(),
                            UiErrors.describe(e, "Could not delete the classroom."),
                            Toast.LENGTH_LONG).show();
                });
    }

    @Override
    public void onToggleManualAllowed(Classroom classroom, boolean isAllowed) {
        if (classroom == null || classroom.getClassId() == null) return;

        if (isAllowed) {
            openManualWindow(classroom);
        } else {
            applyManualFlag(classroom, false, null);
        }
    }

    /**
     * Enabling manual marking means opening a session, not flipping a switch: the
     * window it opens is what the students' writes are checked against.
     */
    private void openManualWindow(Classroom classroom) {
        binding.progressBar.setVisibility(View.VISIBLE);

        sessions.open(classroom.getClassId())
                .addOnSuccessListener(window -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);

                    Map<String, Object> session = new HashMap<>();
                    session.put("slotId", window.getSlotId());
                    session.put("date", window.getDate());
                    session.put("endsAtMillis", window.getEndsAtMillis());
                    classroom.setSession(session);

                    applyManualFlag(classroom, true, "Manual marking open until "
                            + AppClock.timeOfDay(window.getEndsAtMillis())
                            + " for " + classroom.getClassName());
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);

                    classroom.setManualAttendanceAllowed(false);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(requireContext(),
                            UiErrors.describe(e, "Could not open the attendance window."),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void applyManualFlag(Classroom classroom, boolean allowed, String successMessage) {
        boolean previous = classroom.isManualAttendanceAllowed();
        classroom.setManualAttendanceAllowed(allowed);

        db.collection("classrooms").document(classroom.getClassId())
                .update("manualAttendanceAllowed", allowed)
                .addOnSuccessListener(unused -> {
                    classrooms.addClassroom(classroom);
                    if (!isAdded() || binding == null) return;
                    Toast.makeText(requireContext(),
                            successMessage != null ? successMessage
                                    : "Manual self-marking disabled for " + classroom.getClassName(),
                            Toast.LENGTH_LONG).show();
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    classroom.setManualAttendanceAllowed(previous);
                    if (!isAdded() || binding == null) return;
                    Toast.makeText(requireContext(),
                            UiErrors.describe(e, "Could not change that setting."),
                            Toast.LENGTH_LONG).show();
                    adapter.notifyDataSetChanged();
                });
    }

    @Override
    public void onMarkAttendance(Classroom classroom) {
        openDetail(classroom);
    }

    @Override
    public void onViewRoster(Classroom classroom) {
        openDetail(classroom);
    }

    private void openDetail(Classroom classroom) {
        if (classroom == null || getView() == null) return;

        Bundle bundle = new Bundle();
        bundle.putString("classId", classroom.getClassId());
        Navigation.findNavController(requireView())
                .navigate(R.id.action_manageClassroomsFragment_to_classroomDetailFragment, bundle);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
