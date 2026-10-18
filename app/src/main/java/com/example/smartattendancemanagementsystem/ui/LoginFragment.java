package com.example.smartattendancemanagementsystem.ui;

import android.content.Context;
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
import com.example.smartattendancemanagementsystem.databinding.FragmentLoginBinding;
import com.example.smartattendancemanagementsystem.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            navigateToDashboard(view);
            return;
        }

        binding.btnLogin.setOnClickListener(this::performLogin);
        binding.tvRegisterLink.setOnClickListener(v -> {
            if (isAdded() && getView() != null) {
                Navigation.findNavController(requireView()).navigate(R.id.action_loginFragment_to_registerFragment);
            }
        });
    }

    private void performLogin(View view) {
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.setError("Email is required");
            return;
        } else {
            binding.tilEmail.setError(null);
        }

        if (TextUtils.isEmpty(password)) {
            binding.tilPassword.setError("Password is required");
            return;
        } else {
            binding.tilPassword.setError(null);
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnLogin.setEnabled(false);

        try {
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        if (isAdded()) {
                            binding.progressBar.setVisibility(View.GONE);
                            binding.btnLogin.setEnabled(true);
                            FirebaseUser user = authResult.getUser();
                            if (user != null) {
                                fetchUserRoleAndNavigate(user.getUid(), email);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (isAdded() && binding != null) {
                            binding.progressBar.setVisibility(View.GONE);
                            binding.btnLogin.setEnabled(true);
                            Toast.makeText(requireContext(), "Login Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (Exception e) {
            if (binding != null) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnLogin.setEnabled(true);
            }
            if (getContext() != null) {
                Toast.makeText(requireContext(), "Login Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void fetchUserRoleAndNavigate(String uid, String email) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (isAdded() && getView() != null) {
                        String role = "Student";
                        if (doc.exists()) {
                            User user = doc.toObject(User.class);
                            if (user != null && user.getRole() != null) {
                                role = user.getRole();
                            }
                        }
                        saveSession(role, email);
                        navigateToDashboard(requireView());
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded() && getView() != null) {
                        saveSession("Student", email);
                        navigateToDashboard(requireView());
                    }
                });
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

    private void navigateToDashboard(View view) {
        if (isAdded() && getView() != null) {
            Navigation.findNavController(requireView()).navigate(R.id.action_loginFragment_to_dashboardFragment);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
