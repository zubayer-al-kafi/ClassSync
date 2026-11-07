package com.example.smartattendancemanagementsystem.ui;

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
import androidx.lifecycle.ViewModelProvider;

import com.example.smartattendancemanagementsystem.R;
import com.example.smartattendancemanagementsystem.adapter.CourseMaterialAdapter;
import com.example.smartattendancemanagementsystem.databinding.FragmentCourseMaterialsBinding;
import com.example.smartattendancemanagementsystem.model.Classroom;
import com.example.smartattendancemanagementsystem.model.CourseMaterial;
import com.example.smartattendancemanagementsystem.repository.ClassroomRepository;
import com.example.smartattendancemanagementsystem.session.SessionViewModel;
import com.example.smartattendancemanagementsystem.util.UiErrors;
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
    private SessionViewModel session;

    private final ClassroomRepository classrooms = ClassroomRepository.getInstance();

    private final List<Classroom> availableClassrooms = new ArrayList<>();
    private final List<CourseMaterial> materialsList = new ArrayList<>();
    private final List<String> addedMaterialLinks = new ArrayList<>();

    private Classroom selectedClassroom;
    private String targetClassIdFromArgs;
    private boolean isTeacher;

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
        session = new ViewModelProvider(requireActivity()).get(SessionViewModel.class);

        if (getArguments() != null) {
            targetClassIdFromArgs = getArguments().getString("classId");
        }

        adapter = new CourseMaterialAdapter();
        binding.rvMaterials.setAdapter(adapter);

        binding.btnAddMaterialLink.setOnClickListener(v -> addMaterialLink());
        binding.btnPublishPost.setOnClickListener(v -> publishMaterialPost());

        // The role comes from users/{uid} via the session, never from local storage.
        session.getState().observe(getViewLifecycleOwner(), state -> {
            if (binding == null) return;
            isTeacher = state != null && state.isReady()
                    && "Instructor".equals(state.normalizedRole());
            binding.cardInstructorPost.setVisibility(isTeacher ? View.VISIBLE : View.GONE);
        });

        if (targetClassIdFromArgs != null && !targetClassIdFromArgs.isEmpty()) {
            loadCourseMaterials(targetClassIdFromArgs);
        }

        loadClassrooms();
    }

    private void addMaterialLink() {
        String inputLink = binding.etMaterialLinkInput.getText() != null
                ? binding.etMaterialLinkInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(inputLink)) {
            toast("Please paste a valid URL / link");
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
            linkTv.setText("#" + (index + 1) + ": " + link);
            linkTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary));
            linkTv.setTextSize(12);
            linkTv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            MaterialButton removeBtn = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.borderlessButtonStyle);
            removeBtn.setText("Remove");
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
        boolean instructor = session.isInstructor();

        binding.progressBar.setVisibility(View.VISIBLE);

        classrooms.loadMine(uid, instructor)
                .addOnSuccessListener(fetched -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    availableClassrooms.clear();
                    availableClassrooms.addAll(fetched);

                    List<String> names = new ArrayList<>();
                    int selectedIndex = 0;

                    for (Classroom c : availableClassrooms) {
                        names.add(c.getClassName() + " (" + c.getClassCode() + ")");
                        if (targetClassIdFromArgs != null
                                && targetClassIdFromArgs.equals(c.getClassId())) {
                            selectedIndex = availableClassrooms.size() - 1;
                        }
                    }

                    if (!availableClassrooms.isEmpty()) {
                        setupDropdown(names, selectedIndex);
                    } else {
                        binding.tvEmptyMaterials.setText(
                                "You are not in any classroom yet. Join one with a class code first.");
                        binding.tvEmptyMaterials.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvEmptyMaterials.setText(
                            UiErrors.describe(e, "Could not load your classrooms."));
                    binding.tvEmptyMaterials.setVisibility(View.VISIBLE);
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
        if (classId == null || binding == null) return;

        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("materials")
                .whereEqualTo("classId", classId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!isAdded() || binding == null) return;

                    binding.progressBar.setVisibility(View.GONE);
                    materialsList.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        CourseMaterial material = doc.toObject(CourseMaterial.class);
                        if (material != null) materialsList.add(material);
                    }
                    Collections.sort(materialsList,
                            (m1, m2) -> Long.compare(m2.getTimestamp(), m1.getTimestamp()));
                    adapter.setMaterials(materialsList);

                    if (materialsList.isEmpty()) {
                        binding.tvEmptyMaterials.setText("No materials posted for this class yet.");
                        binding.tvEmptyMaterials.setVisibility(View.VISIBLE);
                    } else {
                        binding.tvEmptyMaterials.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvEmptyMaterials.setText(
                            UiErrors.describe(e, "Could not load the course materials."));
                    binding.tvEmptyMaterials.setVisibility(View.VISIBLE);
                });
    }

    private void publishMaterialPost() {
        if (selectedClassroom == null) {
            toast("Select a classroom first");
            return;
        }

        String title = binding.etPostTitle.getText() != null
                ? binding.etPostTitle.getText().toString().trim() : "";
        String desc = binding.etPostDescription.getText() != null
                ? binding.etPostDescription.getText().toString().trim() : "";

        if (TextUtils.isEmpty(title)) {
            toast("Please enter a title");
            return;
        }

        StringBuilder combinedFileUrl = new StringBuilder();
        for (String link : addedMaterialLinks) {
            combinedFileUrl.append(link).append(" ");
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        String uid = currentUser != null ? currentUser.getUid() : "";
        String authorName = currentUser != null && currentUser.getDisplayName() != null
                && !currentUser.getDisplayName().isEmpty()
                ? currentUser.getDisplayName() : "Instructor";

        String materialId = db.collection("materials").document().getId();

        CourseMaterial material = new CourseMaterial(
                materialId, selectedClassroom.getClassId(), selectedClassroom.getClassName(),
                title, desc, combinedFileUrl.toString().trim(), uid, authorName,
                System.currentTimeMillis()
        );

        binding.progressBar.setVisibility(View.VISIBLE);
        db.collection("materials").document(materialId).set(material)
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    binding.etPostTitle.setText("");
                    binding.etPostDescription.setText("");
                    addedMaterialLinks.clear();
                    renderMaterialLinksContainer();
                    toast("Published to course stream");
                    loadCourseMaterials(selectedClassroom.getClassId());
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(),
                            UiErrors.describe(e, "Could not publish the material."),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void toast(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
