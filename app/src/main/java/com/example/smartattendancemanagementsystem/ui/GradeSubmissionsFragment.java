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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

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
        if (assignmentId == null) return;

        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("submissions")
                .whereEqualTo("assignmentId", assignmentId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        submissionsList.clear();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            submissionsList.add(doc.toObject(Submission.class));
                        }
                        adapter.setSubmissions(submissionsList);
                        binding.tvEmptySubmissions.setVisibility(submissionsList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.tvEmptySubmissions.setVisibility(submissionsList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    @Override
    public void onGradeSubmission(Submission submission) {
        if (submission == null) return;

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        // Display submitted text preview
        if (submission.getSubmissionText() != null && !submission.getSubmissionText().trim().isEmpty()) {
            TextView workTv = new TextView(requireContext());
            workTv.setText("📄 Student Work: " + submission.getSubmissionText().trim());
            workTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            workTv.setTextSize(13);
            workTv.setPadding(0, 0, 0, 10);
            layout.addView(workTv);
        }

        // Display submitted links inside dialog
        String combinedContent = (submission.getSubmissionText() != null ? submission.getSubmissionText() : "") + " " +
                (submission.getFileUrl() != null ? submission.getFileUrl() : "");

        List<String> detectedUrls = SubmissionAdapter.extractUrls(combinedContent);
        if (!detectedUrls.isEmpty()) {
            for (int i = 0; i < detectedUrls.size(); i++) {
                String fullUrl = detectedUrls.get(i);

                LinearLayout linkRow = new LinearLayout(requireContext());
                linkRow.setOrientation(LinearLayout.HORIZONTAL);
                linkRow.setGravity(Gravity.CENTER_VERTICAL);

                MaterialButton openBtn = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.borderlessButtonStyle);
                openBtn.setText("🔗 Open Link #" + (i + 1));
                openBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary));
                openBtn.setTextSize(12);

                MaterialButton copyBtn = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.borderlessButtonStyle);
                copyBtn.setText("📋 Copy");
                copyBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                copyBtn.setTextSize(12);

                openBtn.setOnClickListener(v -> openUrl(fullUrl));
                copyBtn.setOnClickListener(v -> copyToClipboard(fullUrl));

                linkRow.addView(openBtn);
                linkRow.addView(copyBtn);
                layout.addView(linkRow);
            }
        }

        final EditText etScore = new EditText(requireContext());
        etScore.setHint("Enter Marks / Score (e.g. 95)");
        etScore.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (submission.isGraded()) {
            etScore.setText(String.valueOf(submission.getScore()));
        }

        final EditText etFeedback = new EditText(requireContext());
        etFeedback.setHint("Teacher Feedback / Comments");
        if (submission.getFeedback() != null) {
            etFeedback.setText(submission.getFeedback());
        }

        layout.addView(etScore);
        layout.addView(etFeedback);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Grade Work: " + submission.getStudentName())
                .setView(layout)
                .setPositiveButton("Save Grade", (dialog, which) -> {
                    String scoreStr = etScore.getText().toString().trim();
                    String feedback = etFeedback.getText().toString().trim();

                    if (TextUtils.isEmpty(scoreStr)) {
                        Toast.makeText(requireContext(), "Please enter valid marks", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int score = 0;
                    try {
                        score = Integer.parseInt(scoreStr);
                    } catch (NumberFormatException ignored) {}

                    submission.setScore(score);
                    submission.setFeedback(feedback);
                    submission.setGraded(true);

                    db.collection("submissions").document(submission.getSubmissionId()).set(submission);
                    Toast.makeText(requireContext(), "Grade & Feedback saved!", Toast.LENGTH_SHORT).show();
                    loadSubmissions();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openUrl(String fullUrl) {
        try {
            String targetUrl = fullUrl;
            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                targetUrl = "http://" + targetUrl;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Cannot open link: " + fullUrl, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Submitted Student Link", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(requireContext(), "📋 Link copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Copied: " + text, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
