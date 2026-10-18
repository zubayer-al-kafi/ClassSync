package com.example.smartattendancemanagementsystem.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.FragmentSubmitAssignmentBinding;
import com.example.smartattendancemanagementsystem.model.Assignment;
import com.example.smartattendancemanagementsystem.model.Submission;
import com.example.smartattendancemanagementsystem.model.User;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SubmitAssignmentFragment extends Fragment {

    private FragmentSubmitAssignmentBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String assignmentId;
    private String classId;
    private Assignment currentAssignment;
    private final List<String> addedSubmissionLinks = new ArrayList<>();
    private boolean isDueDatePassed = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentSubmitAssignmentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (getArguments() != null) {
            assignmentId = getArguments().getString("assignmentId");
            classId = getArguments().getString("classId");
        }

        binding.btnAddLink.setOnClickListener(v -> addSubmissionLink());
        binding.btnSubmitWork.setOnClickListener(v -> submitAssignmentWork());

        loadAssignmentAndExistingSubmission();
    }

    private void addSubmissionLink() {
        if (isDueDatePassed) {
            Toast.makeText(requireContext(), "Due date has passed. Cannot modify submission.", Toast.LENGTH_SHORT).show();
            return;
        }

        String inputLink = binding.etLinkInput.getText() != null ? binding.etLinkInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(inputLink)) {
            Toast.makeText(requireContext(), "Please paste a valid URL / link", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!addedSubmissionLinks.contains(inputLink)) {
            addedSubmissionLinks.add(inputLink);
            binding.etLinkInput.setText("");
            renderSubmissionLinksContainer();
        }
    }

    private void renderSubmissionLinksContainer() {
        binding.layoutAddedLinksContainer.removeAllViews();

        for (int i = 0; i < addedSubmissionLinks.size(); i++) {
            String link = addedSubmissionLinks.get(i);
            int index = i;

            LinearLayout itemLayout = new LinearLayout(requireContext());
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setPadding(0, 4, 0, 4);

            TextView linkTv = new TextView(requireContext());
            linkTv.setText("🔗 #" + (index + 1) + ": " + link);
            linkTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary));
            linkTv.setTextSize(12);
            linkTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            MaterialButton removeBtn = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.borderlessButtonStyle);
            removeBtn.setText("❌");
            removeBtn.setPadding(0, 0, 0, 0);
            removeBtn.setEnabled(!isDueDatePassed);

            removeBtn.setOnClickListener(v -> {
                addedSubmissionLinks.remove(index);
                renderSubmissionLinksContainer();
            });

            itemLayout.addView(linkTv);
            itemLayout.addView(removeBtn);
            binding.layoutAddedLinksContainer.addView(itemLayout);
        }
    }

    private void loadAssignmentAndExistingSubmission() {
        if (assignmentId == null) return;

        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("assignments").document(assignmentId).get()
                .addOnSuccessListener(doc -> {
                    if (isAdded() && doc.exists()) {
                        currentAssignment = doc.toObject(Assignment.class);
                        if (currentAssignment != null) {
                            binding.tvAssignmentTitle.setText(currentAssignment.getTitle());
                            binding.tvAssignmentInstructions.setText(currentAssignment.getInstructions());

                            checkDueDateAndSetNotice(currentAssignment.getDueDate());
                        }
                    }
                });

        FirebaseUser currentUser = mAuth.getCurrentUser();
        String studentId = currentUser != null ? currentUser.getUid() : "";
        String subDocId = studentId + "_" + assignmentId;

        db.collection("submissions").document(subDocId).get()
                .addOnSuccessListener(doc -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        if (doc.exists()) {
                            Submission submission = doc.toObject(Submission.class);
                            if (submission != null) {
                                binding.etSubmissionText.setText(submission.getSubmissionText());

                                if (submission.getFileUrl() != null) {
                                    String[] links = submission.getFileUrl().split(" ");
                                    for (String l : links) {
                                        if (!l.trim().isEmpty() && !addedSubmissionLinks.contains(l.trim())) {
                                            addedSubmissionLinks.add(l.trim());
                                        }
                                    }
                                    renderSubmissionLinksContainer();
                                }

                                if (submission.isGraded()) {
                                    binding.tvStatusNotice.setText("✅ Graded Score: " + submission.getScore() + "/" + (currentAssignment != null ? currentAssignment.getMaxMarks() : 100) +
                                            "\nFeedback: " + (submission.getFeedback() != null ? submission.getFeedback() : "Good work"));
                                    binding.cardStatusNotice.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.status_present));
                                } else {
                                    binding.tvStatusNotice.setText("⏳ Previously Submitted! You can update work anytime before the due date.");
                                    binding.cardStatusNotice.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.secondary));
                                }
                            }
                        }
                    }
                });
    }

    private void checkDueDateAndSetNotice(String dueDateStr) {
        if (dueDateStr == null || dueDateStr.trim().isEmpty()) return;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Date due = sdf.parse(dueDateStr);
            if (due != null && System.currentTimeMillis() > due.getTime()) {
                isDueDatePassed = true;
                binding.tvStatusNotice.setText("🔒 Due date passed (" + dueDateStr + "). Submissions are closed.");
                binding.cardStatusNotice.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.status_absent));
                binding.btnSubmitWork.setEnabled(false);
                binding.btnAddLink.setEnabled(false);
                binding.etSubmissionText.setEnabled(false);
            } else {
                binding.tvStatusNotice.setText("⏰ Due: " + dueDateStr + ". You can update your submission anytime before due date.");
            }
        } catch (Exception e) {
            binding.tvStatusNotice.setText("⏰ Due: " + dueDateStr);
        }
    }

    private void submitAssignmentWork() {
        if (isDueDatePassed) {
            Toast.makeText(requireContext(), "Due date has passed. Submissions closed.", Toast.LENGTH_SHORT).show();
            return;
        }

        String notes = binding.etSubmissionText.getText() != null ? binding.etSubmissionText.getText().toString().trim() : "";

        if (TextUtils.isEmpty(notes) && addedSubmissionLinks.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter response text or add a resource link", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder combinedLinks = new StringBuilder();
        for (String l : addedSubmissionLinks) {
            combinedLinks.append(l).append(" ");
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String studentId = currentUser.getUid();

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSubmitWork.setEnabled(false);

        db.collection("users").document(studentId).get()
                .addOnSuccessListener(userDoc -> {
                    String displayName = "Student";
                    if (userDoc.exists()) {
                        User user = userDoc.toObject(User.class);
                        if (user != null) {
                            String name = user.getName() != null ? user.getName() : "Student";
                            String roll = user.getStudentId() != null && !user.getStudentId().isEmpty() ? user.getStudentId() : null;

                            if (roll != null) {
                                displayName = name + " (Roll: " + roll + ")";
                            } else {
                                displayName = name;
                            }
                        }
                    } else if (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
                        displayName = currentUser.getDisplayName();
                    }

                    saveSubmissionToFirestore(studentId, displayName, notes, combinedLinks.toString().trim());
                })
                .addOnFailureListener(e -> {
                    saveSubmissionToFirestore(studentId, "Student", notes, combinedLinks.toString().trim());
                });
    }

    private void saveSubmissionToFirestore(String studentId, String studentName, String notes, String linksStr) {
        String subDocId = studentId + "_" + assignmentId;

        Submission submission = new Submission(
                subDocId, assignmentId, classId, studentId, studentName,
                notes, linksStr, 0, null, false, System.currentTimeMillis()
        );

        db.collection("submissions").document(subDocId).set(submission)
                .addOnSuccessListener(aVoid -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnSubmitWork.setEnabled(true);
                        Toast.makeText(requireContext(), "Assignment work submitted successfully!", Toast.LENGTH_SHORT).show();
                        Navigation.findNavController(requireView()).popBackStack();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnSubmitWork.setEnabled(true);
                        Toast.makeText(requireContext(), "Failed to save submission: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
