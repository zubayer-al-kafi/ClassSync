package com.example.smartattendancemanagementsystem.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.adapter.AttendanceAdapter;
import com.example.smartattendancemanagementsystem.databinding.FragmentAttendanceHistoryBinding;
import com.example.smartattendancemanagementsystem.model.AttendanceRecord;
import com.example.smartattendancemanagementsystem.repository.AttendanceRepository;
import com.example.smartattendancemanagementsystem.util.AttendanceMath;
import com.example.smartattendancemanagementsystem.util.UiErrors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

/**
 * A student's own attendance log.
 *
 * The summary is derived from the same records the list shows, so the percentage and
 * the rows can never disagree.
 */
public class AttendanceHistoryFragment extends Fragment {

    private FragmentAttendanceHistoryBinding binding;
    private AttendanceAdapter adapter;
    private final AttendanceRepository attendance = new AttendanceRepository();
    private final List<AttendanceRecord> historyRecords = new ArrayList<>();

    private boolean loading;
    private boolean loadedOnce;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAttendanceHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new AttendanceAdapter();
        binding.rvAttendanceHistory.setAdapter(adapter);
        binding.btnRetry.setOnClickListener(v -> loadAttendanceHistory());

        loadAttendanceHistory();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back from a scan or a manual mark should not show a stale list.
        if (loadedOnce && !loading) {
            loadAttendanceHistory();
        }
    }

    private void loadAttendanceHistory() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            showState("Sign in to see your attendance history.", false);
            return;
        }

        if (loading || binding == null) return;
        loading = true;

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnRetry.setVisibility(View.GONE);
        binding.tvEmptyHistory.setVisibility(View.GONE);

        attendance.forStudent(currentUser.getUid())
                .addOnSuccessListener(records -> {
                    if (!isAdded() || binding == null) {
                        loading = false;
                        return;
                    }

                    loading = false;
                    loadedOnce = true;
                    binding.progressBar.setVisibility(View.GONE);

                    historyRecords.clear();
                    historyRecords.addAll(records);
                    adapter.setRecords(historyRecords);

                    if (historyRecords.isEmpty()) {
                        binding.cardSummary.setVisibility(View.GONE);
                        showState("No attendance recorded yet. "
                                + "Sessions you scan or mark appear here.", false);
                        return;
                    }

                    renderSummary(currentUser.getUid());
                    binding.tvEmptyHistory.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    loading = false;
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    binding.cardSummary.setVisibility(View.GONE);

                    // Keep whatever is already on screen, but say why it may be stale.
                    if (historyRecords.isEmpty()) {
                        showState(UiErrors.describe(e,
                                "Could not load your attendance history."), true);
                    } else {
                        showState(UiErrors.describe(e, "Could not refresh your history."), true);
                    }
                });
    }

    private void renderSummary(String userId) {
        AttendanceMath summary = AttendanceMath.summarize(historyRecords);

        int sessions = summary.getSessionsHeld();
        int attended = summary.getAttendedSessions(userId);
        int percentage = summary.percentageFor(userId);

        int present = 0;
        int late = 0;
        int absent = 0;
        for (AttendanceRecord record : historyRecords) {
            if ("Present".equalsIgnoreCase(record.getStatus())) {
                present++;
            } else if ("Late".equalsIgnoreCase(record.getStatus())) {
                late++;
            } else {
                absent++;
            }
        }

        binding.cardSummary.setVisibility(View.VISIBLE);
        binding.tvSummaryPercent.setText(percentage + "%");
        binding.tvSummaryDetail.setText(
                attended + " of " + sessions + (sessions == 1 ? " session" : " sessions")
                        + " attended");
        binding.tvSummaryBreakdown.setText(
                "Present " + present + " · Late " + late + " · Absent " + absent);

        // Same three bands the roster screen uses, so a student and a teacher see
        // the same verdict for the same data.
        int colorRes;
        if (percentage >= 75) {
            colorRes = R.color.status_present;
        } else if (percentage >= 60) {
            colorRes = R.color.status_late;
        } else {
            colorRes = R.color.status_absent;
        }
        binding.tvSummaryPercent.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
    }

    private void showState(String message, boolean retryable) {
        if (binding == null) return;
        binding.tvEmptyHistory.setText(message);
        binding.tvEmptyHistory.setVisibility(View.VISIBLE);
        binding.btnRetry.setVisibility(retryable ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
