package com.example.smartattendancemanagementsystem.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.adapter.SubmissionAdapter;
import com.example.smartattendancemanagementsystem.databinding.FragmentGradeSubmissionsBinding;
import com.example.smartattendancemanagementsystem.model.Submission;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GradeSubmissionsFragment extends Fragment implements SubmissionAdapter.OnSubmissionActionListener {

    private FragmentGradeSubmissionsBinding binding;
    private SubmissionAdapter adapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String assignmentId;
    private final List<Submission> submissionsList = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentGradeSubmissionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (getArguments() != null) {
            assignmentId = getArguments().getString("assignmentId");
        }

        adapter = new SubmissionAdapter(this);
        binding.rvSubmissions.setAdapter(adapter);

        loadSubmissions();
    }

    private void loadSubmissions() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (assignmentId == null || currentUser == null) {
            showEmpty("This assignment could not be opened.");
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        // teacherId is part of the query because the rules only let an instructor read
        // submissions whose teacherId is their own uid.
        db.collection("submissions")
                .whereEqualTo("teacherId", currentUser.getUid())
                .whereEqualTo("assignmentId", assignmentId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    submissionsList.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Submission submission = doc.toObject(Submission.class);
                        if (submission != null) submissionsList.add(submission);
                    }
                    adapter.setSubmissions(submissionsList);
                    binding.tvEmptySubmissions.setVisibility(
                            submissionsList.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    showEmpty(UiErrors.describe(e, "Could not load the submissions."));
                });
    }

    private void showEmpty(String message) {
        if (binding == null) return;
        binding.tvEmptySubmissions.setText(message);
        binding.tvEmptySubmissions.setVisibility(View.VISIBLE);
    }

    @Override
    public void onGradeSubmission(Submission submission) {
        if (submission == null || submission.getSubmissionId() == null) return;

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        if (submission.getSubmissionText() != null && !submission.getSubmissionText().trim().isEmpty()) {
            TextView workTv = new TextView(requireContext());
            workTv.setText("Student work: " + submission.getSubmissionText().trim());
            workTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            workTv.setTextSize(13);
            workTv.setPadding(0, 0, 0, 10);
            layout.addView(workTv);
        }

        String combinedContent = (submission.getSubmissionText() != null ? submission.getSubmissionText() : "")
                + " " + (submission.getFileUrl() != null ? submission.getFileUrl() : "");

        List<String> detectedUrls = SubmissionAdapter.extractUrls(combinedContent);
        for (int i = 0; i < detectedUrls.size(); i++) {
            String fullUrl = detectedUrls.get(i);

            LinearLayout linkRow = new LinearLayout(requireContext());
            linkRow.setOrientation(LinearLayout.HORIZONTAL);
            linkRow.setGravity(Gravity.CENTER_VERTICAL);

            MaterialButton openBtn = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.borderlessButtonStyle);
            openBtn.setText("Open link #" + (i + 1));
            openBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary));
            openBtn.setTextSize(12);

            MaterialButton copyBtn = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.borderlessButtonStyle);
            copyBtn.setText("Copy");
            copyBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            copyBtn.setTextSize(12);

            openBtn.setOnClickListener(v -> openUrl(fullUrl));
            copyBtn.setOnClickListener(v -> copyToClipboard(fullUrl));

            linkRow.addView(openBtn);
            linkRow.addView(copyBtn);
            layout.addView(linkRow);
        }

        final EditText etScore = new EditText(requireContext());
        etScore.setHint("Enter marks / score (e.g. 95)");
        etScore.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (submission.isGraded()) {
            etScore.setText(String.valueOf(submission.getScore()));
        }

        final EditText etFeedback = new EditText(requireContext());
        etFeedback.setHint("Teacher feedback / comments");
        if (submission.getFeedback() != null) {
            etFeedback.setText(submission.getFeedback());
        }

        layout.addView(etScore);
        layout.addView(etFeedback);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Grade work: " + submission.getStudentName())
                .setView(layout)
                .setPositiveButton("Save Grade", (dialog, which) -> {
                    String scoreStr = etScore.getText().toString().trim();
                    String feedback = etFeedback.getText().toString().trim();

                    if (TextUtils.isEmpty(scoreStr)) {
                        toast("Please enter valid marks");
                        return;
                    }

                    int score;
                    try {
                        score = Integer.parseInt(scoreStr);
                    } catch (NumberFormatException e) {
                        toast("Marks must be a number");
                        return;
                    }

                    saveGrade(submission, score, feedback);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Only the three grading fields are written: the rules reject anything else. */
    private void saveGrade(Submission submission, int score, String feedback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("score", score);
        fields.put("feedback", feedback);
        fields.put("graded", true);

        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("submissions").document(submission.getSubmissionId())
                .update(fields)
                .addOnSuccessListener(unused -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    toast("Grade and feedback saved");
                    loadSubmissions();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(),
                            UiErrors.describe(e, "Could not save the grade."),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void openUrl(String fullUrl) {
        try {
            String targetUrl = fullUrl;
            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                targetUrl = "http://" + targetUrl;
            }
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)));
        } catch (Exception e) {
            toast("Cannot open link: " + fullUrl);
        }
    }

    private void copyToClipboard(String text) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Submitted student link", text));
                toast("Link copied to clipboard");
            }
        } catch (Exception e) {
            toast("Copied: " + text);
        }
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
