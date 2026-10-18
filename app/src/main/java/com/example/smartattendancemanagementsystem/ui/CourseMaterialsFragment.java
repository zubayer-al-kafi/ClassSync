package com.example.smartattendancemanagementsystem.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.adapter.CourseMaterialAdapter;
import com.example.smartattendancemanagementsystem.databinding.FragmentCourseMaterialsBinding;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.model.CourseMaterial;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CourseMaterialsFragment extends Fragment {

    private FragmentCourseMaterialsBinding binding;
    private CourseMaterialAdapter adapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private final List<Classroom> availableClassrooms = new ArrayList<>();
    private final List<CourseMaterial> materialsList = new ArrayList<>();
    private final List<String> addedMaterialLinks = new ArrayList<>();

    private Classroom selectedClassroom;
    private String targetClassIdFromArgs;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentCourseMaterialsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (getArguments() != null) {
            targetClassIdFromArgs = getArguments().getString("classId");
        }

        adapter = new CourseMaterialAdapter();
        binding.rvMaterials.setAdapter(adapter);

        SharedPreferences prefs = requireContext().getSharedPreferences("app_session", Context.MODE_PRIVATE);
        String role = prefs.getString("role", "Student");
        boolean isTeacher = "Instructor".equalsIgnoreCase(role) || "Teacher".equalsIgnoreCase(role);

        binding.cardInstructorPost.setVisibility(isTeacher ? View.VISIBLE : View.GONE);

        binding.btnAddMaterialLink.setOnClickListener(v -> addMaterialLink());
        binding.btnPublishPost.setOnClickListener(v -> publishMaterialPost());

        if (targetClassIdFromArgs != null && !targetClassIdFromArgs.isEmpty()) {
            loadCourseMaterials(targetClassIdFromArgs);
        }

        loadClassrooms();
    }

    private void addMaterialLink() {
        String inputLink = binding.etMaterialLinkInput.getText() != null ? binding.etMaterialLinkInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(inputLink)) {
            Toast.makeText(requireContext(), "Please paste a valid URL / link", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!addedMaterialLinks.contains(inputLink)) {
            addedMaterialLinks.add(inputLink);
            binding.etMaterialLinkInput.setText("");
            renderMaterialLinksContainer();
        }
    }

    private void renderMaterialLinksContainer() {
        binding.layoutAddedMaterialLinksContainer.removeAllViews();

        for (int i = 0; i < addedMaterialLinks.size(); i++) {
            String link = addedMaterialLinks.get(i);
            int index = i;

            LinearLayout itemLayout = new LinearLayout(requireContext());
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setPadding(0, 4, 0, 4);

            TextView linkTv = new TextView(requireContext());
            linkTv.setText("🔗 #" + (index + 1) + ": " + link);
            linkTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary));
            linkTv.setTextSize(12);
            linkTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            MaterialButton removeBtn = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.borderlessButtonStyle);
            removeBtn.setText("❌");
            removeBtn.setPadding(0, 0, 0, 0);

            removeBtn.setOnClickListener(v -> {
                addedMaterialLinks.remove(index);
                renderMaterialLinksContainer();
            });

            itemLayout.addView(linkTv);
            itemLayout.addView(removeBtn);
            binding.layoutAddedMaterialLinksContainer.addView(itemLayout);
        }
    }

    private void loadClassrooms() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String uid = currentUser.getUid();
        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("classrooms").get()
                .addOnSuccessListener(snapshots -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        availableClassrooms.clear();
                        List<String> names = new ArrayList<>();
                        int selectedIndex = 0;

                        for (QueryDocumentSnapshot doc : snapshots) {
                            Classroom c = doc.toObject(Classroom.class);
                            if (c.isTeacherAssigned(uid) || (c.getEnrolledStudentIds() != null && c.getEnrolledStudentIds().contains(uid))) {
                                availableClassrooms.add(c);
                                names.add(c.getClassName() + " (" + c.getClassCode() + ")");

                                if (targetClassIdFromArgs != null && targetClassIdFromArgs.equals(c.getClassId())) {
                                    selectedIndex = availableClassrooms.size() - 1;
                                }
                            }
                        }

                        if (!availableClassrooms.isEmpty()) {
                            setupDropdown(names, selectedIndex);
                        } else {
                            binding.tvEmptyMaterials.setVisibility(materialsList.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    }
                });
    }

    private void setupDropdown(List<String> names, int defaultIndex) {
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                names
        );
        binding.actClassroom.setAdapter(arrayAdapter);
        binding.actClassroom.setOnItemClickListener((parent, view, position, id) -> {
            selectedClassroom = availableClassrooms.get(position);
            loadCourseMaterials(selectedClassroom.getClassId());
        });

        if (defaultIndex < availableClassrooms.size()) {
            selectedClassroom = availableClassrooms.get(defaultIndex);
            binding.actClassroom.setText(names.get(defaultIndex), false);
            loadCourseMaterials(selectedClassroom.getClassId());
        }
    }

    private void loadCourseMaterials(String classId) {
        if (classId == null) return;

        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("materials")
                .whereEqualTo("classId", classId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        materialsList.clear();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            materialsList.add(doc.toObject(CourseMaterial.class));
                        }
                        Collections.sort(materialsList, (m1, m2) -> Long.compare(m2.getTimestamp(), m1.getTimestamp()));
                        adapter.setMaterials(materialsList);
                        binding.tvEmptyMaterials.setVisibility(materialsList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.tvEmptyMaterials.setVisibility(materialsList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void publishMaterialPost() {
        if (selectedClassroom == null) {
            Toast.makeText(requireContext(), "Select a classroom first", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = binding.etPostTitle.getText() != null ? binding.etPostTitle.getText().toString().trim() : "";
        String desc = binding.etPostDescription.getText() != null ? binding.etPostDescription.getText().toString().trim() : "";

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder combinedFileUrl = new StringBuilder();
        for (String link : addedMaterialLinks) {
            combinedFileUrl.append(link).append(" ");
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        String uid = currentUser != null ? currentUser.getUid() : "";
        String authorName = currentUser != null && currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()
                ? currentUser.getDisplayName() : "Instructor";

        String materialId = db.collection("materials").document().getId();

        CourseMaterial material = new CourseMaterial(
                materialId, selectedClassroom.getClassId(), selectedClassroom.getClassName(),
                title, desc, combinedFileUrl.toString().trim(), uid, authorName, System.currentTimeMillis()
        );

        binding.progressBar.setVisibility(View.VISIBLE);
        db.collection("materials").document(materialId).set(material)
                .addOnSuccessListener(aVoid -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.etPostTitle.setText("");
                        binding.etPostDescription.setText("");
                        addedMaterialLinks.clear();
                        renderMaterialLinksContainer();
                        Toast.makeText(requireContext(), "Published to course stream!", Toast.LENGTH_SHORT).show();
                        loadCourseMaterials(selectedClassroom.getClassId());
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
