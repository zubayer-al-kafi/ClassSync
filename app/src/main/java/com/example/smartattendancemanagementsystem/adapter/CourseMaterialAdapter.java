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
import com.example.smartattendancemanagementsystem.databinding.ItemCourseMaterialBinding;
import com.example.smartattendancemanagementsystem.model.CourseMaterial;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class CourseMaterialAdapter extends RecyclerView.Adapter<CourseMaterialAdapter.ViewHolder> {

    private List<CourseMaterial> materials = new ArrayList<>();

    public void setMaterials(List<CourseMaterial> materials) {
        this.materials = materials != null ? materials : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCourseMaterialBinding binding = ItemCourseMaterialBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(materials.get(position));
    }

    @Override
    public int getItemCount() {
        return materials.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemCourseMaterialBinding binding;

        public ViewHolder(@NonNull ItemCourseMaterialBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(CourseMaterial material) {
            Context context = binding.getRoot().getContext();

            binding.tvMaterialTitle.setText(material.getTitle() != null ? material.getTitle() : "Announcement");
            binding.tvMaterialAuthor.setText("Posted by " + (material.getAuthorName() != null ? material.getAuthorName() : "Instructor"));
            binding.tvMaterialDesc.setText(material.getDescription() != null ? material.getDescription() : "");

            // Extract all URLs from description and fileUrl
            String combinedText = (material.getDescription() != null ? material.getDescription() : "") + " " +
                    (material.getFileUrl() != null ? material.getFileUrl() : "");

            List<String> detectedUrls = SubmissionAdapter.extractUrls(combinedText);
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
                    openBtn.setText("📥 Open #" + (i + 1) + " (" + domain + ")");
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

    private String extractDomainName(String url) {
        if (url == null) return "file";
        try {
            String clean = url.replace("https://", "").replace("http://", "").replace("www.", "");
            int slashIndex = clean.indexOf("/");
            if (slashIndex > 0) {
                clean = clean.substring(0, slashIndex);
            }
            return clean;
        } catch (Exception e) {
            return "attachment";
        }
    }
}
