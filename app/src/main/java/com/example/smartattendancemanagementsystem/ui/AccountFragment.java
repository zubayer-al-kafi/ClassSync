package com.example.smartattendancemanagementsystem.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.FragmentAccountBinding;
import com.example.smartattendancemanagementsystem.model.User;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class AccountFragment extends Fragment {

    private FragmentAccountBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
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

        binding.btnEditProfile.setOnClickListener(v -> openEditProfileDialog());
        binding.btnLogout.setOnClickListener(this::performLogout);

        loadAccountProfile();
    }

    private void loadAccountProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String email = currentUser.getEmail() != null ? currentUser.getEmail() : "";
            binding.tvEmail.setText(email);

            db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        if (isAdded() && doc.exists()) {
                            currentUserModel = doc.toObject(User.class);
                            if (currentUserModel != null) {
                                bindUserToUi(currentUserModel);
                            }
                        }
                    });
        }
    }

    private void bindUserToUi(User user) {
        if (user == null) return;

        String name = user.getName() != null && !user.getName().trim().isEmpty() ? user.getName().trim() : "User";
        binding.tvFullName.setText(name);

        String firstChar = name.substring(0, 1).toUpperCase();
        binding.tvAvatarInitials.setText(firstChar);

        binding.tvEmail.setText(user.getEmail() != null ? user.getEmail() : "");
        binding.tvRoleBadge.setText("Role: " + (user.getRole() != null ? user.getRole() : "Student"));

        if (user.getStudentId() != null && !user.getStudentId().isEmpty()) {
            binding.tvStudentIdBadge.setText("Roll: " + user.getStudentId() + " 🔒");
            binding.tvStudentIdBadge.setVisibility(View.VISIBLE);
        } else {
            binding.tvStudentIdBadge.setVisibility(View.GONE);
        }

        binding.tvDepartmentBadge.setText("Dept: " + (user.getDepartment() != null ? user.getDepartment() : "Computer Science"));

        String phone = user.getPhone() != null && !user.getPhone().isEmpty() ? user.getPhone() : "Not set";
        binding.tvPhone.setText("📞 Phone: " + phone);

        String address = user.getAddress() != null && !user.getAddress().isEmpty() ? user.getAddress() : "Not set";
        binding.tvAddress.setText("📍 Address: " + address);
    }

    private void openEditProfileDialog() {
        if (currentUserModel == null) {
            Toast.makeText(requireContext(), "Profile data loading...", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        // 1. Full Name
        final EditText etName = createDialogInputField(layout, "Full Name", currentUserModel.getName(), true);

        // 2. Student ID / Roll Number (READ-ONLY / NON-EDITABLE)
        String rollText = currentUserModel.getStudentId() != null && !currentUserModel.getStudentId().isEmpty() ? currentUserModel.getStudentId() : "N/A";
        final EditText etRoll = createDialogInputField(layout, "Student ID / Roll (Locked - Cannot Be Changed)", rollText, false);

        // 3. Department
        final EditText etDept = createDialogInputField(layout, "Department", currentUserModel.getDepartment(), true);

        // 4. Phone Number
        final EditText etPhone = createDialogInputField(layout, "Phone Number", currentUserModel.getPhone(), true);

        // 5. Address
        final EditText etAddress = createDialogInputField(layout, "Address", currentUserModel.getAddress(), true);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("✏️ Edit Profile Information")
                .setView(layout)
                .setPositiveButton("Save Updates", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String dept = etDept.getText().toString().trim();
                    String phone = etPhone.getText().toString().trim();
                    String address = etAddress.getText().toString().trim();

                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    currentUserModel.setName(name);
                    currentUserModel.setDepartment(dept);
                    currentUserModel.setPhone(phone);
                    currentUserModel.setAddress(address);

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        db.collection("users").document(user.getUid()).set(currentUserModel);
                    }

                    bindUserToUi(currentUserModel);
                    Toast.makeText(requireContext(), "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private EditText createDialogInputField(LinearLayout container, String hint, String initialValue, boolean editable) {
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

    private void performLogout(View view) {
        mAuth.signOut();
        requireContext().getSharedPreferences("app_session", Context.MODE_PRIVATE).edit().clear().apply();
        Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();
        Navigation.findNavController(view).navigate(R.id.action_accountFragment_to_loginFragment);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
