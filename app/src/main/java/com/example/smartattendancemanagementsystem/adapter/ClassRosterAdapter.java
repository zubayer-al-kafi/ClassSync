package com.example.smartattendancemanagementsystem.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.ItemClassRosterBinding;
import com.example.smartattendancemanagementsystem.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassRosterAdapter extends RecyclerView.Adapter<ClassRosterAdapter.ViewHolder> {

    /** Fallback band boundaries when the classroom has no threshold configured. */
    private static final int DEFAULT_THRESHOLD = 75;

    private List<User> students = new ArrayList<>();
    private Map<String, Integer> percentageMap = new HashMap<>();
    private int minThreshold = DEFAULT_THRESHOLD;

    public void setRosterData(List<User> students, Map<String, Integer> percentageMap) {
        this.students = students != null ? students : new ArrayList<>();
        this.percentageMap = percentageMap != null ? percentageMap : new HashMap<>();
        notifyDataSetChanged();
    }

    /** The classroom's minimum attendance percentage, used to grade the badge. */
    public void setThreshold(int minThreshold) {
        this.minThreshold = minThreshold > 0 ? minThreshold : DEFAULT_THRESHOLD;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemClassRosterBinding binding = ItemClassRosterBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(students.get(position));
    }

    @Override
    public int getItemCount() {
        return students.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemClassRosterBinding binding;

        public ViewHolder(@NonNull ItemClassRosterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(User student) {
            Context context = binding.getRoot().getContext();

            String roll = student.getStudentId() != null && !student.getStudentId().isEmpty()
                    ? " (Roll: " + student.getStudentId() + ")" : "";
            String name = student.getName() != null ? student.getName() : "Student";
            binding.tvStudentName.setText(name + roll);
            binding.tvStudentEmail.setText(student.getEmail() != null ? student.getEmail() : "");

            Integer percentage = percentageMap.get(student.getUid());

            // No recorded sessions yet: showing 0% would read as "never attended".
            if (percentage == null) {
                binding.tvPercentage.setText("—");
                binding.tvThresholdBadge.setText("No sessions yet");
                binding.tvThresholdBadge.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.dark_surface_variant));
                return;
            }

            binding.tvPercentage.setText(percentage + "%");

            if (percentage >= minThreshold) {
                binding.tvThresholdBadge.setText("Good Standing");
                binding.tvThresholdBadge.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.status_present));
            } else if (percentage >= Math.max(0, minThreshold - 15)) {
                binding.tvThresholdBadge.setText("Warning");
                binding.tvThresholdBadge.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.status_late));
            } else {
                binding.tvThresholdBadge.setText("Critical Alert");
                binding.tvThresholdBadge.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.status_absent));
            }
        }
    }
}
