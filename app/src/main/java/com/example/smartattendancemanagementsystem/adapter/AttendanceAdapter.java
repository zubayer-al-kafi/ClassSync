package com.example.smartattendancemanagementsystem.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.ItemAttendanceBinding;
import com.example.smartattendancemanagementsystem.model.AttendanceRecord;

import java.util.ArrayList;
import java.util.List;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.ViewHolder> {

    private List<AttendanceRecord> records = new ArrayList<>();

    public void setRecords(List<AttendanceRecord> records) {
        this.records = records != null ? records : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAttendanceBinding binding = ItemAttendanceBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(records.get(position));
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemAttendanceBinding binding;

        public ViewHolder(@NonNull ItemAttendanceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(AttendanceRecord record) {
            Context context = binding.getRoot().getContext();

            binding.tvCourseName.setText(record.getClassName() != null ? record.getClassName() : "Course");
            binding.tvDate.setText("Date: " + (record.getDate() != null ? record.getDate() : "N/A"));

            String status = record.getStatus() != null ? record.getStatus().toUpperCase() : "PRESENT";
            binding.tvStatusBadge.setText(status);

            if ("PRESENT".equalsIgnoreCase(status)) {
                binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_present));
            } else if ("LATE".equalsIgnoreCase(status)) {
                binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_late));
            } else {
                binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_absent));
            }
        }
    }
}
