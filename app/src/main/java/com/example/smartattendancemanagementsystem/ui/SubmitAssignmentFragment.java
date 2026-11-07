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
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SubmitAssignmentFragment extends Fragment {

    private FragmentSubmitAssignmentBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String assignmentId;
    private String classId;
    private Assignment currentAssignment;
    private Submission existingSubmission;
    private String teacherId;

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
            toast("Due date has passed. Cannot modify submission.");
            return;
        }

        String inputLink = binding.etLinkInput.getText() != null
                ? binding.etLinkInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(inputLink)) {
            toast("Please paste a valid URL / link");
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
            linkTv.setText("#" + (index + 1) + ": " + link);
            linkTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary));
            linkTv.setTextSize(12);
            linkTv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            MaterialButton removeBtn = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.borderlessButtonStyle);
            removeBtn.setText("Remove");
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
        if (assignmentId == null) {
            showNotice("This assignment could not be opened.", R.color.status_absent);
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        String studentId = currentUser.getUid();

        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("assignments").document(assignmentId).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded() || binding == null) return;

                    if (!doc.exists()) {
                        binding.progressBar.setVisibility(View.GONE);
                        showNotice("This assignment no longer exists.", R.color.status_absent);
                        return;
                    }

                    currentAssignment = doc.toObject(Assignment.class);
                    if (currentAssignment != null) {
                        binding.tvAssignmentTitle.setText(currentAssignment.getTitle());
                        binding.tvAssignmentInstructions.setText(currentAssignment.getInstructions());
                        checkDueDateAndSetNotice(currentAssignment.getDueDate());
                    }

                    // teacherId is required on the submission because the rules compare it
                    // with the classroom owner; read it from the classroom, not the client.
                    resolveTeacherId(studentId);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    showNotice(UiErrors.describe(e, "Could not load the assignment."),
                            R.color.status_absent);
                });
    }

    private void resolveTeacherId(String studentId) {
        if (classId == null && currentAssignment != null) {
            classId = currentAssignment.getClassId();
        }
        if (classId == null) {
            loadExistingSubmission(studentId);
            return;
        }

        db.collection("classrooms").document(classId).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded() || binding == null) return;
                    if (doc.exists()) {
                        teacherId = doc.getString("teacherId");
                    }
                    loadExistingSubmission(studentId);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    loadExistingSubmission(studentId);
                });
    }

    private void loadExistingSubmission(String studentId) {
        String subDocId = studentId + "_" + assignmentId;

        db.collection("submissions").document(subDocId).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    if (!doc.exists()) return;

                    existingSubmission = doc.toObject(Submission.class);
                    if (existingSubmission == null) return;

                    binding.etSubmissionText.setText(existingSubmission.getSubmissionText());

                    if (existingSubmission.getFileUrl() != null) {
                        String[] links = existingSubmission.getFileUrl().split(" ");
                        for (String l : links) {
                            if (!l.trim().isEmpty() && !addedSubmissionLinks.contains(l.trim())) {
                                addedSubmissionLinks.add(l.trim());
                            }
                        }
                        renderSubmissionLinksContainer();
                    }

                    if (existingSubmission.isGraded()) {
                        showNotice("Graded score: " + existingSubmission.getScore() + "/"
                                        + (currentAssignment != null
                                        ? currentAssignment.getMaxMarks() : 100)
                                        + "\nFeedback: " + (existingSubmission.getFeedback() != null
                                        ? existingSubmission.getFeedback() : "No feedback"),
                                R.color.status_present);
                    } else {
                        showNotice("Previously submitted. You can update your work before the due date.",
                                R.color.secondary);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    showNotice(UiErrors.describe(e, "Could not check your existing submission."),
                            R.color.status_absent);
                });
    }

    private void showNotice(String message, int colorRes) {
        if (binding == null) return;
        binding.tvStatusNotice.setText(message);
        binding.cardStatusNotice.setStrokeColor(
                ContextCompat.getColor(requireContext(), colorRes));
        binding.cardStatusNotice.setVisibility(View.VISIBLE);
    }

    private void checkDueDateAndSetNotice(String dueDateStr) {
        if (dueDateStr == null || dueDateStr.trim().isEmpty()) return;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Date due = sdf.parse(dueDateStr);
            if (due != null && System.currentTimeMillis() > due.getTime()) {
                isDueDatePassed = true;
                showNotice("Due date passed (" + dueDateStr + "). Submissions are closed.",
                        R.color.status_absent);
                binding.btnSubmitWork.setEnabled(false);
                binding.btnAddLink.setEnabled(false);
                binding.etSubmissionText.setEnabled(false);
            } else {
                showNotice("Due: " + dueDateStr
                        + ". You can update your submission anytime before the due date.",
                        R.color.secondary);
            }
        } catch (Exception e) {
            showNotice("Due: " + dueDateStr, R.color.secondary);
        }
    }

    private void submitAssignmentWork() {
        if (isDueDatePassed) {
            toast("Due date has passed. Submissions closed.");
            return;
        }

        String notes = binding.etSubmissionText.getText() != null
                ? binding.etSubmissionText.getText().toString().trim() : "";

        if (TextUtils.isEmpty(notes) && addedSubmissionLinks.isEmpty()) {
            toast("Please enter response text or add a resource link");
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
                    String displayName = resolveDisplayName(userDoc, currentUser);
                    saveSubmission(studentId, displayName, notes, combinedLinks.toString().trim());
                })
                .addOnFailureListener(e ->
                        saveSubmission(studentId, resolveDisplayName(null, currentUser),
                                notes, combinedLinks.toString().trim()));
    }

    private String resolveDisplayName(DocumentSnapshot userDoc, FirebaseUser currentUser) {
        if (userDoc != null && userDoc.exists()) {
            User user = userDoc.toObject(User.class);
            if (user != null) {
                String name = user.getName() != null ? user.getName() : "Student";
                String roll = user.getStudentId();
                return roll != null && !roll.isEmpty() ? name + " (Roll: " + roll + ")" : name;
            }
        }
        if (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
            return currentUser.getDisplayName();
        }
        return "Student";
    }

    private void saveSubmission(String studentId, String studentName, String notes, String linksStr) {
        if (!isAdded() || binding == null) return;

        String subDocId = studentId + "_" + assignmentId;

        // A revision only touches the editable fields: the rules require graded, score,
        // studentId, teacherId and classId to stay exactly as they were.
        if (existingSubmission != null) {
            Map<String, Object> fields = new HashMap<>();
            fields.put("submissionText", notes);
            fields.put("fileUrl", linksStr);
            fields.put("submittedAt", System.currentTimeMillis());

            db.collection("submissions").document(subDocId)
                    .update(fields)
                    .addOnSuccessListener(aVoid -> onSubmissionSaved())
                    .addOnFailureListener(this::onSubmissionFailed);
            return;
        }

        if (teacherId == null) {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnSubmitWork.setEnabled(true);
            toast("This class could not be verified. Try reopening the assignment.");
            return;
        }

        Submission submission = new Submission(
                subDocId, assignmentId, classId, teacherId, studentId, studentName,
                notes, linksStr, 0, null, false, System.currentTimeMillis()
        );

        db.collection("submissions").document(subDocId).set(submission)
                .addOnSuccessListener(aVoid -> onSubmissionSaved())
                .addOnFailureListener(this::onSubmissionFailed);
    }

    private void onSubmissionSaved() {
        if (!isAdded() || binding == null) return;
        binding.progressBar.setVisibility(View.GONE);
        binding.btnSubmitWork.setEnabled(true);
        toast("Assignment work submitted");
        Navigation.findNavController(requireView()).popBackStack();
    }

    private void onSubmissionFailed(Exception e) {
        if (!isAdded() || binding == null) return;
        binding.progressBar.setVisibility(View.GONE);
        binding.btnSubmitWork.setEnabled(true);
        Toast.makeText(requireContext(),
                UiErrors.describe(e, "Could not save the submission."),
                Toast.LENGTH_LONG).show();
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
