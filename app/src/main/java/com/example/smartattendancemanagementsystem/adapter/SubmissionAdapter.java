package com.example.smartattendancemanagementsystem.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.ItemSubmissionBinding;
import com.example.smartattendancemanagementsystem.model.Submission;
import com.example.smartattendancemanagementsystem.model.User;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubmissionAdapter extends RecyclerView.Adapter<SubmissionAdapter.ViewHolder> {

    public interface OnSubmissionActionListener {
        void onGradeSubmission(Submission submission);
    }

    private List<Submission> submissions = new ArrayList<>();
    private final OnSubmissionActionListener listener;

    public SubmissionAdapter(OnSubmissionActionListener listener) {
        this.listener = listener;
    }

    public void setSubmissions(List<Submission> submissions) {
        this.submissions = submissions != null ? submissions : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSubmissionBinding binding = ItemSubmissionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(submissions.get(position));
    }

    @Override
    public int getItemCount() {
        return submissions.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemSubmissionBinding binding;

        public ViewHolder(@NonNull ItemSubmissionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Submission submission) {
            Context context = binding.getRoot().getContext();

            String studentTitle = submission.getStudentName();
            if (studentTitle == null || studentTitle.contains("@")) {
                studentTitle = "Student (ID: " + (submission.getStudentId() != null ? submission.getStudentId() : "N/A") + ")";
            }
            binding.tvStudentName.setText(studentTitle);

            // Fetch student profile for Roll Number if email was saved in legacy submission
            if (submission.getStudentId() != null) {
                FirebaseFirestore.getInstance().collection("users").document(submission.getStudentId()).get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                User user = doc.toObject(User.class);
                                if (user != null) {
                                    String name = user.getName() != null ? user.getName() : "Student";
                                    String roll = user.getStudentId() != null && !user.getStudentId().isEmpty() ? user.getStudentId() : null;
                                    if (roll != null) {
                                        binding.tvStudentName.setText(name + " (Roll: " + roll + ")");
                                    } else {
                                        binding.tvStudentName.setText(name);
                                    }
                                }
                            }
                        });
            }

            binding.tvSubmissionText.setText(submission.getSubmissionText() != null ? submission.getSubmissionText() : "");

            if (submission.getSubmittedAt() > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                binding.tvSubmissionDate.setText("Submitted: " + sdf.format(new Date(submission.getSubmittedAt())));
            } else {
                binding.tvSubmissionDate.setText("Submitted Work");
            }

            if (submission.isGraded()) {
                binding.tvScoreStatus.setText("Score: " + submission.getScore() + " | Graded");
            } else {
                binding.tvScoreStatus.setText("Status: Pending Grading");
            }

            if (submission.getFeedback() != null && !submission.getFeedback().trim().isEmpty()) {
                binding.tvFeedback.setText("Feedback: " + submission.getFeedback());
                binding.tvFeedback.setVisibility(View.VISIBLE);
            } else {
                binding.tvFeedback.setVisibility(View.GONE);
            }

            // Combine text and fileUrl to detect all submitted links
            String combinedContent = (submission.getSubmissionText() != null ? submission.getSubmissionText() : "") + " " +
                    (submission.getFileUrl() != null ? submission.getFileUrl() : "");

            List<String> detectedUrls = extractUrls(combinedContent);
            binding.layoutLinksContainer.removeAllViews();

            if (detectedUrls.isEmpty()) {
                binding.layoutLinksContainer.setVisibility(View.GONE);
            } else {
                binding.layoutLinksContainer.setVisibility(View.VISIBLE);
                for (int i = 0; i < detectedUrls.size(); i++) {
                    String fullUrl = detectedUrls.get(i);
                    String domain = extractDomainName(fullUrl);

                    LinearLayout linkRow = new LinearLayout(context);
                    linkRow.setOrientation(LinearLayout.HORIZONTAL);
                    linkRow.setGravity(Gravity.CENTER_VERTICAL);

                    MaterialButton openBtn = new MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle);
                    openBtn.setText("🔗 Open Link #" + (i + 1) + " (" + domain + ")");
                    openBtn.setTextColor(ContextCompat.getColor(context, R.color.secondary));
                    openBtn.setTextSize(12);
                    openBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    MaterialButton copyBtn = new MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle);
                    copyBtn.setText("📋 Copy");
                    copyBtn.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                    copyBtn.setTextSize(12);

                    openBtn.setOnClickListener(v -> openUrl(context, fullUrl));
                    openBtn.setOnLongClickListener(v -> {
                        copyToClipboard(context, fullUrl);
                        return true;
                    });

                    copyBtn.setOnClickListener(v -> copyToClipboard(context, fullUrl));

                    linkRow.addView(openBtn);
                    linkRow.addView(copyBtn);
                    binding.layoutLinksContainer.addView(linkRow);
                }
            }

            binding.btnGrade.setOnClickListener(v -> {
                if (listener != null) listener.onGradeSubmission(submission);
            });
        }
    }

    private void openUrl(Context context, String fullUrl) {
        try {
            String targetUrl = fullUrl;
            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                targetUrl = "http://" + targetUrl;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Cannot open link: " + fullUrl, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(Context context, String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("ClassSync Resource Link", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "📋 Link copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Copied: " + text, Toast.LENGTH_SHORT).show();
        }
    }

    public static List<String> extractUrls(String text) {
        List<String> containedUrls = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return containedUrls;

        String urlRegex = "(https?://|www\\.)[a-zA-Z0-9+&@#/%?=~_|!:,.;]*[a-zA-Z0-9+&@#/%=~_|]";
        Pattern pattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String url = matcher.group();
            if (!containedUrls.contains(url)) {
                containedUrls.add(url);
            }
        }
        return containedUrls;
    }

    private String extractDomainName(String url) {
        if (url == null) return "web";
        try {
            String clean = url.replace("https://", "").replace("http://", "").replace("www.", "");
            int slashIndex = clean.indexOf("/");
            if (slashIndex > 0) {
                clean = clean.substring(0, slashIndex);
            }
            return clean;
        } catch (Exception e) {
            return "link";
        }
    }
}
