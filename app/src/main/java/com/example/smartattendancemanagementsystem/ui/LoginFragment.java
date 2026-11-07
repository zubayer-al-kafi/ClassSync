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
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.FragmentLoginBinding;
import com.example.smartattendancemanagementsystem.session.SessionViewModel;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private FirebaseAuth mAuth;
    private SessionViewModel session;

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
        session = new ViewModelProvider(requireActivity()).get(SessionViewModel.class);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            session.bind(currentUser.getUid());
            navigateToDashboard();
            return;
        }

        binding.btnLogin.setOnClickListener(this::performLogin);
        binding.tvRegisterLink.setOnClickListener(v -> {
            if (isAdded() && getView() != null) {
                Navigation.findNavController(requireView())
                        .navigate(R.id.action_loginFragment_to_registerFragment);
            }
        });
    }

    private void performLogin(View view) {
        String email = binding.etEmail.getText() != null
                ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null
                ? binding.etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.setError("Email is required");
            return;
        }
        binding.tilEmail.setError(null);

        if (TextUtils.isEmpty(password)) {
            binding.tilPassword.setError("Password is required");
            return;
        }
        binding.tilPassword.setError(null);

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnLogin.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    if (!isAdded()) return;

                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnLogin.setEnabled(true);

                    FirebaseUser user = authResult.getUser();
                    if (user == null) {
                        Toast.makeText(requireContext(),
                                "Sign-in succeeded but no account was returned.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // The role is read from users/{uid} and observed by every screen.
                    session.bind(user.getUid());
                    navigateToDashboard();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnLogin.setEnabled(true);
                    Toast.makeText(requireContext(), UiErrors.describe(e, "Sign-in failed."),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void navigateToDashboard() {
        if (!isAdded() || getView() == null) return;

        // Drop the auth screen: Back from the dashboard must not return to login.
        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(R.id.loginFragment, true)
                .build();

        Navigation.findNavController(requireView())
                .navigate(R.id.action_loginFragment_to_dashboardFragment, null, options);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
