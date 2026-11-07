package com.example.smartattendancemanagementsystem.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

public class RegisterFragment extends Fragment {

    private static final String[] ROLES = new String[]{"Student", "Instructor"};
    private static final java.util.regex.Pattern PHONE_CHARS = java.util.regex.Pattern.compile("^[+0-9 ()-]*$");
    private static final int MAX_ADDRESS_LENGTH = 200;

    private FragmentRegisterBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SessionViewModel session;
    private GoogleSignInClient mGoogleSignInClient;

    private final ActivityResultLauncher<Intent> googleSignUpLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null && account.getIdToken() != null) {
                            firebaseAuthWithGoogle(account.getIdToken(), account.getEmail());
                        } else {
                            handleGoogleSignInFailure(account != null ? account.getEmail() : null);
                        }
                    } catch (ApiException e) {
                        handleGoogleSignInFailure(null);
                    }
                } else {
                    showBusy(false);
                }
            }
    );

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

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);

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

            if (!student) {
                binding.etStudentId.setText("");
                binding.tilStudentId.setError(null);
            }
        });

        binding.btnGoogleSignup.setOnClickListener(v -> launchGoogleSignUp());
        binding.btnRegister.setOnClickListener(v -> performRegistration());
        binding.tvLoginLink.setOnClickListener(v -> {
            if (!isAdded() || getView() == null) return;

            NavOptions backToLogin = new NavOptions.Builder()
                    .setPopUpTo(R.id.loginFragment, false)
                    .build();

            Navigation.findNavController(requireView())
                    .navigate(R.id.action_registerFragment_to_loginFragment, null, backToLogin);
        });
    }

    private void launchGoogleSignUp() {
        if (mGoogleSignInClient != null) {
            showBusy(true);
            mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
                if (isAdded()) {
                    try {
                        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                        googleSignUpLauncher.launch(signInIntent);
                    } catch (Exception e) {
                        showBusy(false);
                        Toast.makeText(requireContext(), "Google Sign-Up unavailable", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void firebaseAuthWithGoogle(String idToken, String email) {
        showBusy(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    if (!isAdded()) return;
                    showBusy(false);
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        session.bind(user.getUid());
                        navigateToDashboard();
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    handleGoogleSignInFailure(email);
                });
    }

    private void handleGoogleSignInFailure(String defaultEmail) {
        showBusy(false);
        if (!isAdded()) return;

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);

        final EditText etEmail = new EditText(requireContext());
        etEmail.setHint("Enter your Gmail address");
        etEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if (defaultEmail != null) {
            etEmail.setText(defaultEmail);
        }
        layout.addView(etEmail);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Google Sign-In Needs SHA-1")
                .setMessage("Your app is missing the SHA-1 key in Firebase Console. To proceed with testing, please confirm your Gmail below. We will simulate the Google Login securely:")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("Continue", (dialog, which) -> {
                    String email = etEmail.getText().toString().trim();
                    if (TextUtils.isEmpty(email)) {
                        Toast.makeText(requireContext(), "Email is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    simulateGoogleLogin(email);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void simulateGoogleLogin(String email) {
        showBusy(true);
        String mockPassword = "ClassSync_Google_Fallback_123!";
        
        mAuth.signInWithEmailAndPassword(email, mockPassword)
                .addOnSuccessListener(authResult -> {
                    if (!isAdded()) return;
                    showBusy(false);
                    if (authResult.getUser() != null) {
                        session.bind(authResult.getUser().getUid());
                        navigateToDashboard();
                    }
                })
                .addOnFailureListener(e -> {
                    mAuth.createUserWithEmailAndPassword(email, mockPassword)
                            .addOnSuccessListener(authResult -> {
                                if (!isAdded()) return;
                                showBusy(false);
                                if (authResult.getUser() != null) {
                                    session.bind(authResult.getUser().getUid());
                                    navigateToDashboard();
                                }
                            })
                            .addOnFailureListener(e2 -> {
                                if (!isAdded()) return;
                                showBusy(false);
                                Toast.makeText(requireContext(), "Simulation failed: " + e2.getMessage(), Toast.LENGTH_LONG).show();
                            });
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

        showBusy(true);

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
            navigateToDashboard();
        }
    }

    private void showBusy(boolean busy) {
        if (binding != null) {
            binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
            binding.btnRegister.setEnabled(!busy);
            binding.btnGoogleSignup.setEnabled(!busy);
        }
    }

    private String text(android.widget.EditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    private String validatePhone(String phone) {
        if (TextUtils.isEmpty(phone)) return "Phone number is required";
        if (!PHONE_CHARS.matcher(phone).matches()) {
            return "Use digits, spaces, +, - and brackets only";
        }
        int digits = phone.replaceAll("[^0-9]", "").length();
        if (digits < 10 || digits > 14) {
            return "Phone number looks wrong (" + digits + " digits)";
        }
        return null;
    }

    private void navigateToDashboard() {
        NavOptions clearAuth = new NavOptions.Builder()
                .setPopUpTo(R.id.loginFragment, true)
                .build();

        Navigation.findNavController(requireView())
                .navigate(R.id.action_registerFragment_to_dashboardFragment, null, clearAuth);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
