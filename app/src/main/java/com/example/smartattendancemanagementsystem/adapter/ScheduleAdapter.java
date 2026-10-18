package com.example.smartattendancemanagementsystem.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.ItemScheduleClassBinding;
import com.example.smartattendancemanagementsystem.model.Classroom;

import java.util.ArrayList;
import java.util.List;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    public interface OnScheduleActionListener {
        void onPrimaryAction(Classroom classroom);
        void onSecondaryAction(Classroom classroom);
    }

    private List<Classroom> scheduledClasses = new ArrayList<>();
    private final OnScheduleActionListener listener;
    private final boolean isTeacherMode;

    public ScheduleAdapter(OnScheduleActionListener listener, boolean isTeacherMode) {
        this.listener = listener;
        this.isTeacherMode = isTeacherMode;
    }

    public void setScheduledClasses(List<Classroom> scheduledClasses) {
        this.scheduledClasses = scheduledClasses != null ? scheduledClasses : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemScheduleClassBinding binding = ItemScheduleClassBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(scheduledClasses.get(position));
    }

    @Override
    public int getItemCount() {
        return scheduledClasses.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemScheduleClassBinding binding;

        public ViewHolder(@NonNull ItemScheduleClassBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Classroom classroom) {
            Context context = binding.getRoot().getContext();

            binding.tvClassName.setText(classroom.getClassName() != null ? classroom.getClassName() : "Classroom");
            binding.tvSubject.setText(classroom.getSubject() != null ? classroom.getSubject() : "");
            binding.tvTimeRange.setText("⏰ Time: " + classroom.getStartTime() + " - " + classroom.getEndTime());

            String status = classroom.getClassStatus();
            if ("ACTIVE".equalsIgnoreCase(status)) {
                binding.tvStatusBadge.setText("🟢 LIVE NOW");
                binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_present));
            } else if ("UPCOMING".equalsIgnoreCase(status)) {
                binding.tvStatusBadge.setText("🟡 UPCOMING");
                binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_late));
            } else {
                binding.tvStatusBadge.setText("⚪ ENDED");
                binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.dark_surface_variant));
            }

            if (isTeacherMode) {
                binding.btnPrimaryAction.setText("Generate QR");
                binding.btnSecondaryAction.setText("Roll Call");
            } else {
                binding.btnPrimaryAction.setText("Scan QR");
                binding.btnSecondaryAction.setText("Self-Mark");
            }

            binding.btnPrimaryAction.setOnClickListener(v -> {
                if (listener != null) listener.onPrimaryAction(classroom);
            });

            binding.btnSecondaryAction.setOnClickListener(v -> {
                if (listener != null) listener.onSecondaryAction(classroom);
            });
        }
    }
}
