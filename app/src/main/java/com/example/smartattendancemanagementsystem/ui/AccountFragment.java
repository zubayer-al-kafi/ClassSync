package com.example.smartattendancemanagementsystem.ui;

import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.FragmentAccountBinding;
import com.example.smartattendancemanagementsystem.model.User;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.example.smartattendancemanagementsystem.session.SessionViewModel;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class AccountFragment extends Fragment {

    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_ADDRESS_LENGTH = 200;

    /** Digits, spaces and the usual separators; 6-20 characters once stripped. */
    private static final Pattern PHONE_CHARS = Pattern.compile("^[+0-9 ()-]*$");

    private FragmentAccountBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SessionViewModel session;
    private User currentUserModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAccountBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        session = new ViewModelProvider(requireActivity()).get(SessionViewModel.class);

        binding.btnEditProfile.setOnClickListener(v -> openEditProfileDialog());
        binding.btnLogout.setOnClickListener(v -> confirmLogout());
        binding.btnDeleteAccount.setOnClickListener(v -> confirmDeleteAccount());

        // The email is known from Auth immediately; everything else comes from the
        // profile document through the session.
        FirebaseUser authUser = mAuth.getCurrentUser();
        if (authUser != null && authUser.getEmail() != null) {
            binding.tvEmail.setText(authUser.getEmail());
        }

        observeSession();
    }

    private void observeSession() {
        session.getState().observe(getViewLifecycleOwner(), state -> {
            if (binding == null || state == null) return;

            switch (state.getStatus()) {
                case LOADING:
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.tvAccountStatus.setVisibility(View.GONE);
                    break;

                case READY:
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvAccountStatus.setVisibility(View.GONE);
                    currentUserModel = state.getUser();
                    bindUserToUi(currentUserModel);
                    break;

                case ERROR:
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvAccountStatus.setText(state.getMessage() != null
                            ? state.getMessage()
                            : "Could not load your profile.");
                    binding.tvAccountStatus.setVisibility(View.VISIBLE);
                    binding.btnEditProfile.setEnabled(false);
                    break;

                case SIGNED_OUT:
                default:
                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnEditProfile.setEnabled(false);
                    break;
            }
        });
    }

    private void bindUserToUi(User user) {
        if (user == null) return;

        binding.btnEditProfile.setEnabled(true);

        String name = !TextUtils.isEmpty(user.getName()) ? user.getName().trim() : "User";
        binding.tvFullName.setText(name);
        binding.tvAvatarInitials.setText(name.substring(0, 1).toUpperCase());

        if (!TextUtils.isEmpty(user.getEmail())) {
            binding.tvEmail.setText(user.getEmail());
        }

        binding.tvRoleBadge.setText(getString(R.string.role_format, user.isInstructor() ? "Instructor" : "Student"));

        if (!TextUtils.isEmpty(user.getStudentId())) {
            binding.tvStudentIdBadge.setText(getString(R.string.roll_format, user.getStudentId()));
            binding.tvStudentIdBadge.setVisibility(View.VISIBLE);
        } else {
            binding.tvStudentIdBadge.setVisibility(View.GONE);
        }

        // Unknown stays unknown: an empty department is not "Computer Science".
        binding.tvDepartmentBadge.setText(getString(R.string.dept_format,
                TextUtils.isEmpty(user.getDepartment()) ? "Not set" : user.getDepartment()));

        binding.tvPhone.setText(getString(R.string.phone_format,
                TextUtils.isEmpty(user.getPhone()) ? "Not set" : user.getPhone()));

        binding.tvAddress.setText(getString(R.string.address_format,
                TextUtils.isEmpty(user.getAddress()) ? "Not set" : user.getAddress()));

        // Real binding state: the deviceId is set by markAttendance on the first scan.
        binding.tvSessionInfo.setText(getString(R.string.session_info_format,
                user.isInstructor() ? "Instructor" : "Student",
                TextUtils.isEmpty(user.getDeviceId())
                        ? "No phone bound yet"
                        : "This phone is bound to your account"));
    }

    // ------------------------------------------------------------------ edit profile

    private void openEditProfileDialog() {
        if (currentUserModel == null) {
            toast("Your profile is still loading. Try again in a moment.");
            return;
        }

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        final EditText etName = createDialogInputField(layout, "Full name",
                currentUserModel.getName(), true);
        final EditText etRoll = createDialogInputField(layout,
                "Student ID / Roll (locked - contact an administrator to change it)",
                TextUtils.isEmpty(currentUserModel.getStudentId())
                        ? "N/A" : currentUserModel.getStudentId(), false);
        final EditText etDept = createDialogInputField(layout, "Department",
                currentUserModel.getDepartment(), true);
        final EditText etPhone = createDialogInputField(layout, "Phone number",
                currentUserModel.getPhone(), true);
        final EditText etAddress = createDialogInputField(layout, "Address",
                currentUserModel.getAddress(), true);

        etRoll.setFocusable(false);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit profile")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String dept = etDept.getText().toString().trim();
                    String phone = etPhone.getText().toString().trim();
                    String address = etAddress.getText().toString().trim();

                    String problem = validate(name, phone, address);
                    if (problem != null) {
                        toast(problem);
                        return;
                    }

                    saveProfile(name, dept, phone, address);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Returns the first human-readable problem, or null when the input is usable. */
    private String validate(String name, String phone, String address) {
        if (TextUtils.isEmpty(name)) return "Name cannot be empty.";
        if (name.length() > MAX_NAME_LENGTH) {
            return "Name is too long (max " + MAX_NAME_LENGTH + " characters).";
        }
        if (address.length() > MAX_ADDRESS_LENGTH) {
            return "Address is too long (max " + MAX_ADDRESS_LENGTH + " characters).";
        }
        if (!TextUtils.isEmpty(phone)) {
            if (!PHONE_CHARS.matcher(phone).matches()) {
                return "Phone can only contain digits, spaces, +, - and brackets.";
            }
            int digits = phone.replaceAll("[^0-9]", "").length();
            if (digits < 6 || digits > 15) {
                return "Phone number looks wrong (" + digits + " digits).";
            }
        }
        return null;
    }

    private void saveProfile(String name, String department, String phone, String address) {
        FirebaseUser authUser = mAuth.getCurrentUser();
        if (authUser == null) {
            toast("You are signed out. Please sign in again.");
            return;
        }

        // Field-scoped update: a full .set() here used to rewrite the whole document,
        // including a stale deviceId that silently unbound the phone.
        Map<String, Object> changes = new HashMap<>();
        changes.put("name", name);
        changes.put("department", department);
        changes.put("phone", phone);
        changes.put("address", address);

        if (binding != null) binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("users").document(authUser.getUid())
                .update(changes)
                .addOnSuccessListener(unused -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);

                    // Keep the Auth display name in step: it is the fallback name on
                    // attendance records when the profile read fails.
                    authUser.updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build());

                    toast("Profile updated.");
                    // Re-read the canonical document rather than trusting the local copy.
                    session.reload();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(),
                            UiErrors.describe(e, "Could not save your profile."),
                            Toast.LENGTH_LONG).show();
                });
    }

    private EditText createDialogInputField(LinearLayout container, String hint,
                                            String initialValue, boolean editable) {
        TextInputLayout til = new TextInputLayout(requireContext());
        til.setHint(hint);

        EditText et = new EditText(requireContext());
        if (initialValue != null) et.setText(initialValue);
        et.setEnabled(editable);
        et.setFocusable(editable);

        til.addView(et);
        container.addView(til);
        return et;
    }

    // ------------------------------------------------------------------------ delete account

    private boolean isGoogleSignIn(FirebaseUser user) {
        if (user == null) return false;
        for (UserInfo userInfo : user.getProviderData()) {
            if (GoogleAuthProvider.PROVIDER_ID.equals(userInfo.getProviderId())) {
                return true;
            }
        }
        return false;
    }

    private void confirmDeleteAccount() {
        if (currentUserModel == null || mAuth.getCurrentUser() == null) return;

        FirebaseUser authUser = mAuth.getCurrentUser();
        boolean isGoogleUser = isGoogleSignIn(authUser);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 10);

        TextView promptTv = new TextView(requireContext());
        promptTv.setText("This action is irreversible. All your data, enrollments, and history will be permanently deleted.\n\n" +
                (isGoogleUser ? "To confirm deletion, please type 'DELETE' below:" : "Please enter your password below to confirm deletion:"));
        promptTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_absent));
        promptTv.setTextSize(14);
        promptTv.setPadding(0, 0, 0, 16);

        final EditText etConfirm = new EditText(requireContext());
        if (isGoogleUser) {
            etConfirm.setHint("Type DELETE to confirm");
            etConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        } else {
            etConfirm.setHint("Current Password");
            etConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }

        layout.addView(promptTv);
        layout.addView(etConfirm);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ Delete Profile & Data")
                .setView(layout)
                .setPositiveButton("Permanently Delete", (d, which) -> {
                    String input = etConfirm.getText().toString().trim();
                    if (TextUtils.isEmpty(input)) {
                        toast(isGoogleUser ? "You must type DELETE to confirm." : "Password is required to delete account.");
                        return;
                    }
                    if (isGoogleUser) {
                        if ("DELETE".equalsIgnoreCase(input)) {
                            performAccountDeletionWithGoogle(authUser);
                        } else {
                            toast("Confirmation text did not match.");
                        }
                    } else {
                        performAccountDeletionWithEmail(authUser, input);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                ContextCompat.getColor(requireContext(), R.color.status_absent));
    }

    private void performAccountDeletionWithGoogle(FirebaseUser authUser) {
        if (authUser == null) return;
        setLoadingState(true);
        executeDeletionProcess(authUser);
    }

    private void performAccountDeletionWithEmail(FirebaseUser authUser, String password) {
        if (authUser == null || authUser.getEmail() == null) return;
        setLoadingState(true);

        AuthCredential credential = EmailAuthProvider.getCredential(authUser.getEmail(), password);
        authUser.reauthenticate(credential).addOnSuccessListener(aVoid -> {
            executeDeletionProcess(authUser);
        }).addOnFailureListener(e -> {
            if (isAdded()) {
                setLoadingState(false);
                toast(UiErrors.describe(e, "Incorrect password. Authentication failed."));
            }
        });
    }

    private void executeDeletionProcess(FirebaseUser authUser) {
        String uid = authUser.getUid();

        db.collection("users").document(uid).delete().addOnCompleteListener(task -> {
            authUser.delete().addOnCompleteListener(authTask -> {
                if (authTask.isSuccessful()) {
                    if (isAdded()) {
                        ClassroomRepository.getInstance().clearAllData();
                        session.clear();
                        toast("Account and all data deleted permanently.");

                        NavOptions clearBackStack = new NavOptions.Builder()
                                .setPopUpTo(R.id.nav_graph, true)
                                .build();
                        Navigation.findNavController(requireView()).navigate(
                                R.id.action_accountFragment_to_loginFragment, null, clearBackStack);
                    }
                } else {
                    if (isAdded()) {
                        setLoadingState(false);
                        toast(UiErrors.describe(authTask.getException(), "Could not delete account from Auth. Please log out and sign in again before trying."));
                    }
                }
            });
        });
    }

    private void setLoadingState(boolean isLoading) {
        if (binding != null) {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnDeleteAccount.setEnabled(!isLoading);
            binding.btnLogout.setEnabled(!isLoading);
            binding.btnEditProfile.setEnabled(!isLoading);
        }
    }

    // ------------------------------------------------------------------------ logout

    private void confirmLogout() {
        if (!isAdded() || binding == null) return;

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Log out?")
                .setMessage("You will need to sign in again to mark or review attendance.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (d, which) -> performLogout())
                .show();

        // Destructive action: make it look like one.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                ContextCompat.getColor(requireContext(), R.color.status_absent));
    }

    private void performLogout() {
        if (binding == null) return;

        binding.btnLogout.setEnabled(false);
        binding.btnDeleteAccount.setEnabled(false);
        binding.btnEditProfile.setEnabled(false);
        binding.progressBar.setVisibility(View.VISIBLE);

        // Drop cached classrooms and the session first, so the next account in this
        // process cannot inherit them.
        ClassroomRepository.getInstance().clearAllData();
        session.clear();
        mAuth.signOut();

        if (!isAdded()) return;

        toast("Logged out");

        NavOptions clearBackStack = new NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .build();

        Navigation.findNavController(requireView()).navigate(
                R.id.action_accountFragment_to_loginFragment, null, clearBackStack);
    }

    private void toast(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
