package com.example.smartattendancemanagementsystem.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartattendancemanagementsystem.databinding.ItemClassroomBinding;
import com.example.smartattendancemanagementsystem.model.Classroom;

import java.util.ArrayList;
import java.util.List;

public class ClassroomAdapter extends RecyclerView.Adapter<ClassroomAdapter.ClassroomViewHolder> {

    public interface OnClassroomActionListener {
        void onMarkAttendance(Classroom classroom);
        void onViewRoster(Classroom classroom);
        default void onToggleManualAllowed(Classroom classroom, boolean isAllowed) {}
        default void onDeleteClassroom(Classroom classroom) {}
        default void onUnenrollClassroom(Classroom classroom) {}
        default void onEditClassroom(Classroom classroom) {}
    }

    private List<Classroom> classrooms = new ArrayList<>();
    private final OnClassroomActionListener listener;
    private boolean isTeacherMode = false;
    private boolean showActionButtons = false; // Default false for clean minimal Dashboard cards

    public ClassroomAdapter(OnClassroomActionListener listener) {
        this.listener = listener;
    }

    public void setTeacherMode(boolean isTeacherMode) {
        this.isTeacherMode = isTeacherMode;
        notifyDataSetChanged();
    }

    public void setShowActionButtons(boolean showActionButtons) {
        this.showActionButtons = showActionButtons;
        notifyDataSetChanged();
    }

    public void setClassrooms(List<Classroom> newClassrooms) {
        this.classrooms = newClassrooms != null ? newClassrooms : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ClassroomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemClassroomBinding binding = ItemClassroomBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ClassroomViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ClassroomViewHolder holder, int position) {
        holder.bind(classrooms.get(position));
    }

    @Override
    public int getItemCount() {
        return classrooms.size();
    }

    class ClassroomViewHolder extends RecyclerView.ViewHolder {

        private final ItemClassroomBinding binding;

        public ClassroomViewHolder(@NonNull ItemClassroomBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Classroom classroom) {
            binding.tvClassName.setText(classroom.getClassName() != null ? classroom.getClassName() : "Classroom");
            binding.tvSubject.setText(classroom.getSubject() != null ? classroom.getSubject() : "");
            binding.tvClassCodeBadge.setText(classroom.getClassCode() != null ? classroom.getClassCode() : "");

            int enrolledCount = classroom.getEnrolledStudentIds() != null ? classroom.getEnrolledStudentIds().size() : 0;
            binding.tvEnrolledCount.setText(enrolledCount + " Students Enrolled | Min Threshold: " + classroom.getMinThreshold() + "%");

            // Tapping the card opens Course Detail Page containing all controls
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onMarkAttendance(classroom);
            });

            if (!showActionButtons) {
                // Minimal clean card view: Hide all action buttons on Dashboard
                binding.layoutTeacherActions.setVisibility(View.GONE);
                binding.layoutStudentActions.setVisibility(View.GONE);
                binding.switchManualMarking.setVisibility(View.GONE);
            } else if (isTeacherMode) {
                binding.layoutTeacherActions.setVisibility(View.VISIBLE);
                binding.layoutStudentActions.setVisibility(View.GONE);
                binding.switchManualMarking.setVisibility(View.VISIBLE);

                binding.switchManualMarking.setOnCheckedChangeListener(null);
                binding.switchManualMarking.setChecked(classroom.isManualAttendanceAllowed());
                binding.switchManualMarking.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    classroom.setManualAttendanceAllowed(isChecked);
                    if (listener != null) listener.onToggleManualAllowed(classroom, isChecked);
                });

                binding.btnEditClassroom.setOnClickListener(v -> {
                    if (listener != null) listener.onEditClassroom(classroom);
                });

                binding.btnMarkClassAttendance.setOnClickListener(v -> {
                    if (listener != null) listener.onMarkAttendance(classroom);
                });

                binding.btnViewRoster.setOnClickListener(v -> {
                    if (listener != null) listener.onViewRoster(classroom);
                });

                binding.btnDeleteClassroom.setOnClickListener(v -> {
                    if (listener != null) listener.onDeleteClassroom(classroom);
                });
            } else {
                binding.layoutTeacherActions.setVisibility(View.GONE);
                binding.layoutStudentActions.setVisibility(View.VISIBLE);
                binding.switchManualMarking.setVisibility(View.GONE);

                binding.btnStudentMark.setOnClickListener(v -> {
                    if (listener != null) listener.onMarkAttendance(classroom);
                });

                binding.btnUnenrollClassroom.setOnClickListener(v -> {
                    if (listener != null) listener.onUnenrollClassroom(classroom);
                });
            }
        }
    }
}
