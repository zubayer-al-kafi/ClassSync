package com.example.smartattendancemanagementsystem.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.ItemStudentAttendanceBinding;
import com.example.smartattendancemanagementsystem.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudentAttendanceAdapter extends RecyclerView.Adapter<StudentAttendanceAdapter.ViewHolder> {

    private List<User> students = new ArrayList<>();
    private final Map<String, String> attendanceMap = new HashMap<>();

    public void setStudents(List<User> newStudents) {
        this.students = newStudents != null ? newStudents : new ArrayList<>();
        this.attendanceMap.clear();
        for (User u : this.students) {
            this.attendanceMap.put(u.getUid(), "Present");
        }
        notifyDataSetChanged();
    }

    public Map<String, String> getAttendanceMap() {
        return attendanceMap;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStudentAttendanceBinding binding = ItemStudentAttendanceBinding.inflate(
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
        private final ItemStudentAttendanceBinding binding;

        public ViewHolder(@NonNull ItemStudentAttendanceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(User student) {
            String roll = student.getStudentId() != null && !student.getStudentId().isEmpty()
                    ? " (Roll: " + student.getStudentId() + ")" : "";
            String name = student.getName() != null ? student.getName() : "Student";
            binding.tvStudentName.setText(name + roll);
            binding.tvStudentEmail.setText(student.getEmail() != null ? student.getEmail() : "");

            binding.rgStatus.setOnCheckedChangeListener(null);
            String status = attendanceMap.get(student.getUid());
            if ("Absent".equalsIgnoreCase(status)) {
                binding.rbAbsent.setChecked(true);
            } else if ("Late".equalsIgnoreCase(status)) {
                binding.rbLate.setChecked(true);
            } else {
                binding.rbPresent.setChecked(true);
            }

            binding.rgStatus.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.rb_absent) {
                    attendanceMap.put(student.getUid(), "Absent");
                } else if (checkedId == R.id.rb_late) {
                    attendanceMap.put(student.getUid(), "Late");
                } else {
                    attendanceMap.put(student.getUid(), "Present");
                }
            });
        }
    }
}
