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

    private List<User> students = new ArrayList<>();
    private Map<String, Integer> percentageMap = new HashMap<>();

    public void setRosterData(List<User> students, Map<String, Integer> percentageMap) {
        this.students = students != null ? students : new ArrayList<>();
        this.percentageMap = percentageMap != null ? percentageMap : new HashMap<>();
        notifyDataSetChanged();
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

            int percentage = percentageMap.getOrDefault(student.getUid(), 100);
            binding.tvPercentage.setText(percentage + "%");

            if (percentage >= 75) {
                binding.tvThresholdBadge.setText("Good Standing");
                binding.tvThresholdBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_present));
            } else if (percentage >= 60) {
                binding.tvThresholdBadge.setText("Warning");
                binding.tvThresholdBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_late));
            } else {
                binding.tvThresholdBadge.setText("Critical Alert");
                binding.tvThresholdBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_absent));
            }
        }
    }
}
