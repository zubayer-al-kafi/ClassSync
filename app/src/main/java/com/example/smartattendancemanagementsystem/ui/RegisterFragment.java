package com.example.smartattendancemanagementsystem.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.FragmentRegisterBinding;
import com.example.smartattendancemanagementsystem.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterFragment extends Fragment {

    private FragmentRegisterBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private static final String[] ROLES = new String[]{"Student", "Instructor"};

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                ROLES
        );
        binding.actRole.setAdapter(roleAdapter);
        binding.actRole.setText(ROLES[0], false);

        binding.actRole.setOnItemClickListener((parent, view1, position, id) -> {
            String selectedRole = ROLES[position];
            if ("Student".equalsIgnoreCase(selectedRole)) {
                binding.tilStudentId.setVisibility(View.VISIBLE);
            } else {
                binding.tilStudentId.setVisibility(View.GONE);
            }
        });

        binding.btnRegister.setOnClickListener(v -> performRegistration());
        binding.tvLoginLink.setOnClickListener(v -> {
            if (isAdded() && getView() != null) {
                Navigation.findNavController(requireView()).navigate(R.id.action_registerFragment_to_loginFragment);
            }
        });
    }

    private void performRegistration() {
        if (binding == null) return;

        try {
            String name = binding.etName.getText() != null ? binding.etName.getText().toString().trim() : "";
            String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
            String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";
            String role = binding.actRole.getText() != null ? binding.actRole.getText().toString().trim() : "Student";
            String studentId = binding.etStudentId.getText() != null ? binding.etStudentId.getText().toString().trim() : "";

            if (TextUtils.isEmpty(name)) {
                binding.tilName.setError("Full name is required");
                return;
            } else {
                binding.tilName.setError(null);
            }

            if (TextUtils.isEmpty(email)) {
                binding.tilEmail.setError("Email is required");
                return;
            } else {
                binding.tilEmail.setError(null);
            }

            if (TextUtils.isEmpty(password) || password.length() < 6) {
                binding.tilPassword.setError("Password must be at least 6 characters");
                return;
            } else {
                binding.tilPassword.setError(null);
            }

            if ("Student".equalsIgnoreCase(role) && TextUtils.isEmpty(studentId)) {
                binding.tilStudentId.setError("Student ID / Roll Number is required for students");
                return;
            } else {
                binding.tilStudentId.setError(null);
            }

            binding.progressBar.setVisibility(View.VISIBLE);
            binding.btnRegister.setEnabled(false);

            if (mAuth == null) {
                mAuth = FirebaseAuth.getInstance();
            }

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        if (isAdded()) {
                            FirebaseUser firebaseUser = authResult.getUser();
                            String uid = firebaseUser != null ? firebaseUser.getUid() : "uid_" + System.currentTimeMillis();

                            User newUser = new User(uid, name, email, role, studentId, "Computer Science", null, System.currentTimeMillis());

                            if (db == null) {
                                db = FirebaseFirestore.getInstance();
                            }

                            db.collection("users").document(uid).set(newUser)
                                    .addOnSuccessListener(aVoid -> {
                                        if (isAdded() && getView() != null) {
                                            binding.progressBar.setVisibility(View.GONE);
                                            binding.btnRegister.setEnabled(true);
                                            saveSession(role, email);
                                            Toast.makeText(requireContext(), "Account registered successfully!", Toast.LENGTH_SHORT).show();
                                            Navigation.findNavController(requireView()).navigate(R.id.action_registerFragment_to_dashboardFragment);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        if (isAdded() && getView() != null) {
                                            binding.progressBar.setVisibility(View.GONE);
                                            binding.btnRegister.setEnabled(true);
                                            saveSession(role, email);
                                            Toast.makeText(requireContext(), "Account created successfully!", Toast.LENGTH_SHORT).show();
                                            Navigation.findNavController(requireView()).navigate(R.id.action_registerFragment_to_dashboardFragment);
                                        }
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (isAdded() && binding != null) {
                            binding.progressBar.setVisibility(View.GONE);
                            binding.btnRegister.setEnabled(true);
                            Toast.makeText(requireContext(), "Registration Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });

        } catch (Exception e) {
            if (binding != null) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnRegister.setEnabled(true);
            }
            if (getContext() != null) {
                Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void saveSession(String role, String email) {
        if (getContext() != null) {
            requireContext().getSharedPreferences("app_session", Context.MODE_PRIVATE)
                    .edit()
                    .putString("role", role)
                    .putString("email", email)
                    .apply();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
