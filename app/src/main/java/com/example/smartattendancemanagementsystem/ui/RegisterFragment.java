package com.example.smartattendancemanagementsystem.ui;

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
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.FragmentRegisterBinding;
import com.example.smartattendancemanagementsystem.model.User;
import com.example.smartattendancemanagementsystem.session.SessionViewModel;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

public class RegisterFragment extends Fragment {

    /**
     * The chosen role is only a request: firestore.rules rejects "Instructor" unless
     * the email appears in config/instructorAllowlist, so the dropdown cannot be
     * used to grant yourself a teacher account.
     */
    private static final String[] ROLES = new String[]{"Student", "Instructor"};

    /** Digits, spaces and the usual separators. */
    private static final java.util.regex.Pattern PHONE_CHARS =
            java.util.regex.Pattern.compile("^[+0-9 ()-]*$");

    private static final int MAX_ADDRESS_LENGTH = 200;

    private FragmentRegisterBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SessionViewModel session;

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
        session = new ViewModelProvider(requireActivity()).get(SessionViewModel.class);

        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                ROLES
        );
        binding.actRole.setAdapter(roleAdapter);
        binding.actRole.setText(ROLES[0], false);

        binding.actRole.setOnItemClickListener((parent, view1, position, id) -> {
            boolean student = "Student".equalsIgnoreCase(ROLES[position]);
            binding.tilStudentId.setVisibility(student ? View.VISIBLE : View.GONE);

            // A roll number belongs to a student only; do not carry it into the profile
            // of an instructor who briefly selected the other role.
            if (!student) {
                binding.etStudentId.setText("");
                binding.tilStudentId.setError(null);
            }
        });

        binding.btnRegister.setOnClickListener(v -> performRegistration());
        binding.tvLoginLink.setOnClickListener(v -> {
            if (!isAdded() || getView() == null) return;

            // Return to the existing login screen instead of stacking a second copy.
            NavOptions backToLogin = new NavOptions.Builder()
                    .setPopUpTo(R.id.loginFragment, false)
                    .build();

            Navigation.findNavController(requireView())
                    .navigate(R.id.action_registerFragment_to_loginFragment, null, backToLogin);
        });
    }

    private void performRegistration() {
        if (binding == null) return;

        String name = text(binding.etName);
        String email = text(binding.etEmail).toLowerCase(java.util.Locale.US);
        String password = text(binding.etPassword);
        String role = binding.actRole.getText() != null
                ? binding.actRole.getText().toString().trim() : "Student";
        String studentId = text(binding.etStudentId);
        String department = text(binding.etDepartment);
        String phone = text(binding.etPhone);
        String address = text(binding.etAddress);
        boolean isStudent = "Student".equalsIgnoreCase(role);

        if (TextUtils.isEmpty(name)) {
            binding.tilName.setError("Full name is required");
            return;
        }
        binding.tilName.setError(null);

        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.setError("Email is required");
            return;
        }
        binding.tilEmail.setError(null);

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            binding.tilPassword.setError("Password must be at least 6 characters");
            return;
        }
        binding.tilPassword.setError(null);

        if (isStudent && TextUtils.isEmpty(studentId)) {
            binding.tilStudentId.setError("Student ID / Roll Number is required for students");
            return;
        }
        binding.tilStudentId.setError(null);

        if (TextUtils.isEmpty(department)) {
            binding.tilDepartment.setError("Department is required");
            return;
        }
        // Kept in step with the minimum lengths in firestore.rules, so a value the
        // backend would reject is caught here with a useful message instead.
        if (department.length() < 2) {
            binding.tilDepartment.setError("Department name is too short");
            return;
        }
        binding.tilDepartment.setError(null);

        String phoneProblem = validatePhone(phone);
        if (phoneProblem != null) {
            binding.tilPhone.setError(phoneProblem);
            return;
        }
        binding.tilPhone.setError(null);

        if (TextUtils.isEmpty(address)) {
            binding.tilAddress.setError("Address is required");
            return;
        }
        if (address.length() < 4) {
            binding.tilAddress.setError("Address is too short");
            return;
        }
        if (address.length() > MAX_ADDRESS_LENGTH) {
            binding.tilAddress.setError("Address is too long (max "
                    + MAX_ADDRESS_LENGTH + " characters)");
            return;
        }
        binding.tilAddress.setError(null);

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnRegister.setEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null || !isAdded()) {
                        finishRegistration(null, role);
                        return;
                    }

                     User newUser = new User(firebaseUser.getUid(), name, email, role,
                             isStudent ? studentId : null, department, null,
                             System.currentTimeMillis());
                     newUser.setPhone(phone);
                     newUser.setAddress(address);

                     db.collection("users").document(firebaseUser.getUid()).set(newUser)
                            .addOnSuccessListener(aVoid -> finishRegistration(firebaseUser, role))
                            .addOnFailureListener(e -> rejectRegistration(firebaseUser, role, e));
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    showBusy(false);
                    Toast.makeText(requireContext(),
                            UiErrors.describe(e, "Registration failed."), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * The account exists in Auth but the profile write was refused. The usual cause is
     * an instructor sign-up from an email that is not on the allowlist, so roll the
     * half-created account back and explain why.
     */
    private void rejectRegistration(FirebaseUser firebaseUser, String role, Exception error) {
        if (firebaseUser != null) {
            firebaseUser.delete();
        }
        if (!isAdded() || binding == null) return;

        showBusy(false);

        boolean refused = error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED;

        String message;
        if (refused && "Instructor".equalsIgnoreCase(role)) {
            message = "Instructor access needs an approved email. "
                    + "Register as a Student, or ask an administrator to add this address.";
        } else if (refused) {
            message = "That account could not be created with these details.";
        } else {
            message = UiErrors.describe(error, "Registration failed.");
        }

        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    private void finishRegistration(FirebaseUser firebaseUser, String role) {
        if (!isAdded() || binding == null) return;

        showBusy(false);

        if (firebaseUser != null) {
            session.bind(firebaseUser.getUid());
        }

        Toast.makeText(requireContext(),
                "Account created as " + role + ".", Toast.LENGTH_SHORT).show();

        if (getView() != null) {
            // Clear both auth screens so Back from the dashboard leaves the app.
            NavOptions clearAuth = new NavOptions.Builder()
                    .setPopUpTo(R.id.loginFragment, true)
                    .build();

            Navigation.findNavController(requireView())
                    .navigate(R.id.action_registerFragment_to_dashboardFragment, null, clearAuth);
        }
    }

    private void showBusy(boolean busy) {
        binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.btnRegister.setEnabled(!busy);
    }

    private String text(android.widget.EditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    /** Returns a human-readable problem, or null when the number is usable. */
    private String validatePhone(String phone) {
        if (TextUtils.isEmpty(phone)) return "Phone number is required";
        if (!PHONE_CHARS.matcher(phone).matches()) {
            return "Use digits, spaces, +, - and brackets only";
        }
        int digits = phone.replaceAll("[^0-9]", "").length();
        if (digits < 6 || digits > 15) {
            return "Phone number looks wrong (" + digits + " digits)";
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
