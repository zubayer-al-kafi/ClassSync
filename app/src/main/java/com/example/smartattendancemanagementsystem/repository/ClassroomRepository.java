package com.example.smartattendancemanagementsystem.repository;

import com.example.smartattendancemanagementsystem.model.Classroom;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClassroomRepository {

    public interface OnClassroomDeletedListener {
        void onClassroomDeleted(String classId, String className);
    }

    private static ClassroomRepository instance;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Map<String, Classroom> classroomMap = new LinkedHashMap<>();
    private final List<OnClassroomDeletedListener> deletedListeners = new ArrayList<>();

    private ClassroomRepository() {
        // Singleton
    }

    public static synchronized ClassroomRepository getInstance() {
        if (instance == null) {
            instance = new ClassroomRepository();
        }
        return instance;
    }

    public void clearAllData() {
        classroomMap.clear();
    }

    public void addClassroomDeletedListener(OnClassroomDeletedListener listener) {
        if (listener != null && !deletedListeners.contains(listener)) {
            deletedListeners.add(listener);
        }
    }

    public void removeClassroomDeletedListener(OnClassroomDeletedListener listener) {
        deletedListeners.remove(listener);
    }

    public void addClassroom(Classroom classroom) {
        if (classroom != null && classroom.getClassId() != null) {
            classroomMap.put(classroom.getClassId(), classroom);
        }
    }

    public void deleteClassroom(String classId) {
        if (classId != null) {
            Classroom removed = classroomMap.remove(classId);
            String name = removed != null ? removed.getClassName() : "Classroom";
            for (OnClassroomDeletedListener listener : new ArrayList<>(deletedListeners)) {
                listener.onClassroomDeleted(classId, name);
            }
        }
    }

    public Classroom getClassroomById(String classId) {
        if (classId == null) return null;
        return classroomMap.get(classId);
    }

    public Classroom getClassroomByCode(String classCode) {
        if (classCode == null) return null;
        for (Classroom c : classroomMap.values()) {
            if (classCode.equalsIgnoreCase(c.getClassCode())) {
                return c;
            }
        }
        return null;
    }

    public List<Classroom> getTeacherClassrooms(String teacherUid) {
        List<Classroom> result = new ArrayList<>();
        if (teacherUid == null) return result;
        for (Classroom c : classroomMap.values()) {
            if (c.isTeacherAssigned(teacherUid)) {
                result.add(c);
            }
        }
        return result;
    }

    public List<Classroom> getStudentJoinedClassrooms(String studentUid) {
        List<Classroom> result = new ArrayList<>();
        if (studentUid == null) return result;
        for (Classroom c : classroomMap.values()) {
            if (c.getEnrolledStudentIds() != null && c.getEnrolledStudentIds().contains(studentUid)) {
                result.add(c);
            }
        }
        return result;
    }

    public boolean enrollStudent(String classCode, String studentUid) {
        Classroom c = getClassroomByCode(classCode);
        if (c != null && studentUid != null) {
            if (c.getEnrolledStudentIds() == null) {
                c.setEnrolledStudentIds(new ArrayList<>());
            }
            if (!c.getEnrolledStudentIds().contains(studentUid)) {
                c.getEnrolledStudentIds().add(studentUid);
            }
            return true;
        }
        return false;
    }

    public void unenrollStudent(String classId, String studentUid) {
        Classroom c = classroomMap.get(classId);
        if (c != null && c.getEnrolledStudentIds() != null && studentUid != null) {
            c.getEnrolledStudentIds().remove(studentUid);
        }
    }

    public Task<List<Classroom>> loadMine(String uid, boolean instructor) {
        if (uid == null) return Tasks.forResult(new ArrayList<>());

        Task<QuerySnapshot> taught = instructor
                ? db.collection("classrooms").whereEqualTo("teacherId", uid).get()
                : Tasks.forResult(null);

        Task<QuerySnapshot> enrolled = instructor
                ? Tasks.forResult(null)
                : db.collection("classrooms").whereArrayContains("enrolledStudentIds", uid).get();

        return Tasks.whenAllSuccess(taught, enrolled).onSuccessTask(results -> {
            Map<String, Classroom> merged = new LinkedHashMap<>();

            for (Object result : results) {
                if (!(result instanceof QuerySnapshot)) continue;
                for (DocumentSnapshot document : ((QuerySnapshot) result).getDocuments()) {
                    Classroom classroom = document.toObject(Classroom.class);
                    if (classroom == null || classroom.getClassId() == null) continue;
                    merged.put(classroom.getClassId(), classroom);
                    addClassroom(classroom);
                }
            }

            return Tasks.forResult(new ArrayList<>(merged.values()));
        });
    }

    public Task<String> resolveClassId(String classCode) {
        if (classCode == null || classCode.trim().isEmpty()) return Tasks.forResult(null);

        String code = classCode.trim().toUpperCase();

        return db.collection("classCodes").document(code).get()
                .onSuccessTask(codeDoc -> Tasks.forResult(
                        codeDoc.exists() ? codeDoc.getString("classId") : null));
    }

    public Task<Void> join(String classId, String studentUid) {
        if (classId == null || studentUid == null) return Tasks.forResult(null);

        return db.collection("classrooms").document(classId)
                .update("enrolledStudentIds", FieldValue.arrayUnion(studentUid));
    }

    public Task<Classroom> load(String classId) {
        if (classId == null) return Tasks.forResult(null);

        return db.collection("classrooms").document(classId).get()
                .onSuccessTask(document -> {
                    if (!document.exists()) return Tasks.forResult(null);

                    Classroom classroom = document.toObject(Classroom.class);
                    if (classroom != null) addClassroom(classroom);
                    return Tasks.forResult(classroom);
                });
    }

    public Task<Void> registerCode(String classCode, String classId) {
        if (classCode == null || classId == null) return Tasks.forResult(null);

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("classId", classId);
        value.put("createdAt", System.currentTimeMillis());

        return db.collection("classCodes").document(classCode.toUpperCase()).set(value);
    }

    public Task<Void> releaseCode(String classCode) {
        if (classCode == null) return Tasks.forResult(null);
        return db.collection("classCodes").document(classCode.toUpperCase()).delete();
    }
}
