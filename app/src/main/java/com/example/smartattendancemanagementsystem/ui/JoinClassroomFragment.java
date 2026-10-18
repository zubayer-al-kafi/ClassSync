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

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.FragmentJoinClassroomBinding;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class JoinClassroomFragment extends Fragment {

    private FragmentJoinClassroomBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

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
        db = FirebaseFirestore.getInstance();

        binding.btnJoinClassroom.setOnClickListener(this::joinClassroom);
    }

    private void joinClassroom(View view) {
        String codeInput = binding.etClassCode.getText() != null ? binding.etClassCode.getText().toString().trim() : "";

        if (TextUtils.isEmpty(codeInput) || codeInput.length() < 4) {
            binding.tilClassCode.setError("Enter a valid Class Code");
            return;
        } else {
            binding.tilClassCode.setError(null);
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        String studentUid = currentUser.getUid();

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnJoinClassroom.setEnabled(false);

        db.collection("classrooms")
                .whereEqualTo("classCode", codeInput.toUpperCase())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnJoinClassroom.setEnabled(true);

                        if (!queryDocumentSnapshots.isEmpty()) {
                            QueryDocumentSnapshot doc = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                            Classroom classroom = doc.toObject(Classroom.class);

                            ClassroomRepository.getInstance().enrollStudent(codeInput, studentUid);

                            db.collection("classrooms").document(classroom.getClassId())
                                    .update("enrolledStudentIds", FieldValue.arrayUnion(studentUid));

                            Toast.makeText(requireContext(), "Joined " + classroom.getClassName() + " successfully!", Toast.LENGTH_SHORT).show();
                            Navigation.findNavController(view).popBackStack();
                        } else {
                            binding.tilClassCode.setError("No classroom found matching code: " + codeInput);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnJoinClassroom.setEnabled(true);
                        boolean success = ClassroomRepository.getInstance().enrollStudent(codeInput, studentUid);
                        if (success) {
                            Toast.makeText(requireContext(), "Joined classroom!", Toast.LENGTH_SHORT).show();
                            Navigation.findNavController(view).popBackStack();
                        } else {
                            binding.tilClassCode.setError("Invalid Class Code");
                        }
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
