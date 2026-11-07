package com.example.smartattendancemanagementsystem.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.databinding.ItemAttendanceBinding;
import com.example.smartattendancemanagementsystem.model.AttendanceRecord;
import com.example.smartattendancemanagementsystem.util.AppClock;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.ViewHolder> {

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.getDefault());

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

            binding.tvCourseName.setText(TextUtils.isEmpty(record.getClassName())
                    ? "Class" : record.getClassName());

            // The period is what distinguishes two sessions on the same day.
            binding.tvSlot.setText(AppClock.describeSlot(record.getSlotId()));

            binding.tvDate.setText(displayDate(record.getDate()));

            if (TextUtils.isEmpty(record.getNotes())) {
                binding.tvNotes.setVisibility(View.GONE);
            } else {
                binding.tvNotes.setText(record.getNotes());
                binding.tvNotes.setVisibility(View.VISIBLE);
            }

            String status = TextUtils.isEmpty(record.getStatus()) ? "Present" : record.getStatus();
            binding.tvStatusBadge.setText(status.toUpperCase(Locale.US));

            if ("Present".equalsIgnoreCase(status)) {
                binding.tvStatusBadge.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.status_present));
            } else if ("Late".equalsIgnoreCase(status)) {
                binding.tvStatusBadge.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.status_late));
            } else {
                binding.tvStatusBadge.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.status_absent));
            }
        }

        private String displayDate(String isoDate) {
            if (TextUtils.isEmpty(isoDate)) return "Date unknown";
            try {
                return LocalDate.parse(isoDate).format(DISPLAY_DATE);
            } catch (DateTimeParseException e) {
                return isoDate;
            }
        }
    }
}
