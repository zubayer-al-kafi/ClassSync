package com.example.smartattendancemanagementsystem.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.databinding.FragmentJoinClassroomBinding;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class JoinClassroomFragment extends Fragment {

    private FragmentJoinClassroomBinding binding;
    private FirebaseAuth mAuth;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentJoinClassroomBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        binding.btnJoinClassroom.setOnClickListener(this::joinClassroom);
    }

    private void joinClassroom(View view) {
        String codeInput = binding.etClassCode.getText() != null
                ? binding.etClassCode.getText().toString().trim().toUpperCase() : "";

        if (TextUtils.isEmpty(codeInput) || codeInput.length() < 4) {
            binding.tilClassCode.setError("Enter a valid Class Code");
            return;
        }
        binding.tilClassCode.setError(null);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        String studentUid = currentUser.getUid();

        showBusy(true);

        // Step 1: Resolve classId from classCodes/{CODE} which is publicly readable by signedIn users
        ClassroomRepository.getInstance().resolveClassId(codeInput)
                .addOnSuccessListener(classId -> {
                    if (!isAdded() || binding == null) return;

                    if (TextUtils.isEmpty(classId)) {
                        showBusy(false);
                        binding.tilClassCode.setError("No classroom matches code " + codeInput);
                        return;
                    }

                    checkMembershipAndEnroll(classId, studentUid, view);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    showBusy(false);
                    binding.tilClassCode.setError(UiErrors.describe(e, "Could not look up that code."));
                });
    }

    private void checkMembershipAndEnroll(String classId, String studentUid, View view) {
        ClassroomRepository repo = ClassroomRepository.getInstance();

        // Attempt reading classroom to check if student is already enrolled
        repo.load(classId)
                .addOnSuccessListener(classroom -> {
                    if (!isAdded() || binding == null) return;

                    if (classroom != null && classroom.getEnrolledStudentIds() != null
                            && classroom.getEnrolledStudentIds().contains(studentUid)) {
                        showBusy(false);
                        Toast.makeText(requireContext(),
                                "You are already enrolled in " + classroom.getClassName(),
                                Toast.LENGTH_SHORT).show();
                        Navigation.findNavController(view).popBackStack();
                        return;
                    }

                    enroll(classId, studentUid, view);
                })
                .addOnFailureListener(e -> {
                    // Normal case for non-members (read is denied until enrolled): proceed to join
                    if (!isAdded() || binding == null) return;
                    enroll(classId, studentUid, view);
                });
    }

    private void enroll(String classId, String studentUid, View view) {
        ClassroomRepository repo = ClassroomRepository.getInstance();

        repo.join(classId, studentUid)
                .addOnSuccessListener(aVoid -> repo.load(classId)
                        .addOnSuccessListener(classroom -> finishJoin(
                                classroom != null ? classroom.getClassName() : null, view))
                        .addOnFailureListener(e -> finishJoin(null, view)))
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    showBusy(false);
                    binding.tilClassCode.setError(UiErrors.describe(e, "Could not join this classroom."));
                });
    }

    private void finishJoin(String className, View view) {
        if (!isAdded() || binding == null) return;

        showBusy(false);
        Toast.makeText(requireContext(),
                className != null ? "Joined " + className + " successfully!" : "Joined the classroom successfully!",
                Toast.LENGTH_SHORT).show();
        Navigation.findNavController(view).popBackStack();
    }

    private void showBusy(boolean busy) {
        if (binding != null) {
            binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
            binding.btnJoinClassroom.setEnabled(!busy);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
