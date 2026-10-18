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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ManageClassroomsFragment extends Fragment implements ClassroomAdapter.OnClassroomActionListener {

    private FragmentManageClassroomsBinding binding;
    private ClassroomAdapter adapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private final List<Classroom> classrooms = new ArrayList<>();

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
        adapter.setShowActionButtons(false); // Clean minimal card style
        binding.rvClassrooms.setAdapter(adapter);

        binding.btnCreateNew.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_manageClassroomsFragment_to_createClassroomFragment)
        );

        loadClassrooms();
    }

    private void loadClassrooms() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String teacherUid = currentUser != null ? currentUser.getUid() : "";

        binding.progressBar.setVisibility(View.VISIBLE);

        List<Classroom> memoryList = ClassroomRepository.getInstance().getTeacherClassrooms(teacherUid);

        db.collection("classrooms")
                .whereEqualTo("teacherId", teacherUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        classrooms.clear();
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Classroom c = doc.toObject(Classroom.class);
                            classrooms.add(c);
                            ClassroomRepository.getInstance().addClassroom(c);
                        }
                        if (classrooms.isEmpty()) {
                            classrooms.addAll(memoryList);
                        }
                        adapter.setClassrooms(classrooms);
                        binding.tvEmptyClassrooms.setVisibility(classrooms.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        adapter.setClassrooms(memoryList);
                        binding.tvEmptyClassrooms.setVisibility(memoryList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    @Override
    public void onEditClassroom(Classroom classroom) {
        if (classroom != null && getView() != null) {
            Bundle bundle = new Bundle();
            bundle.putString("classId", classroom.getClassId());
            Navigation.findNavController(getView())
                    .navigate(R.id.action_manageClassroomsFragment_to_classroomDetailFragment, bundle);
        }
    }

    @Override
    public void onDeleteClassroom(Classroom classroom) {
        if (classroom == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Classroom")
                .setMessage("Are you sure you want to delete '" + classroom.getClassName() + "'? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    ClassroomRepository.getInstance().deleteClassroom(classroom.getClassId());
                    db.collection("classrooms").document(classroom.getClassId()).delete();
                    Toast.makeText(requireContext(), classroom.getClassName() + " deleted.", Toast.LENGTH_SHORT).show();
                    loadClassrooms();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onToggleManualAllowed(Classroom classroom, boolean isAllowed) {
        if (classroom != null && classroom.getClassId() != null) {
            classroom.setManualAttendanceAllowed(isAllowed);
            ClassroomRepository.getInstance().addClassroom(classroom);
            db.collection("classrooms").document(classroom.getClassId())
                    .update("manualAttendanceAllowed", isAllowed);
            String statusStr = isAllowed ? "enabled" : "disabled";
            Toast.makeText(requireContext(), "Manual self-marking " + statusStr + " for " + classroom.getClassName(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMarkAttendance(Classroom classroom) {
        if (classroom != null && getView() != null) {
            Bundle bundle = new Bundle();
            bundle.putString("classId", classroom.getClassId());
            Navigation.findNavController(getView()).navigate(R.id.action_manageClassroomsFragment_to_classroomDetailFragment, bundle);
        }
    }

    @Override
    public void onViewRoster(Classroom classroom) {
        if (classroom != null && getView() != null) {
            Bundle bundle = new Bundle();
            bundle.putString("classId", classroom.getClassId());
            Navigation.findNavController(getView()).navigate(R.id.action_manageClassroomsFragment_to_classroomDetailFragment, bundle);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
