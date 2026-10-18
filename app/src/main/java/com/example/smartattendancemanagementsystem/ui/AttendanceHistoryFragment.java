package com.example.smartattendancemanagementsystem.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartattendancemanagementsystem.adapter.AttendanceAdapter;
import com.example.smartattendancemanagementsystem.databinding.FragmentAttendanceHistoryBinding;
import com.example.smartattendancemanagementsystem.model.AttendanceRecord;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class AttendanceHistoryFragment extends Fragment {

    private FragmentAttendanceHistoryBinding binding;
    private AttendanceAdapter adapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private final List<AttendanceRecord> historyRecords = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAttendanceHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        adapter = new AttendanceAdapter();
        binding.rvAttendanceHistory.setAdapter(adapter);

        loadAttendanceHistory();
    }

    private void loadAttendanceHistory() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String studentUid = currentUser != null ? currentUser.getUid() : "";

        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("attendance")
                .whereEqualTo("userId", studentUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        historyRecords.clear();
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            historyRecords.add(doc.toObject(AttendanceRecord.class));
                        }
                        adapter.setRecords(historyRecords);
                        binding.tvEmptyHistory.setVisibility(historyRecords.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.tvEmptyHistory.setVisibility(View.VISIBLE);
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
