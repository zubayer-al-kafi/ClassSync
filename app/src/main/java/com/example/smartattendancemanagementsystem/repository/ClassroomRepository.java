package com.example.smartattendancemanagementsystem.repository;

import com.example.smartattendancemanagementsystem.model.Classroom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassroomRepository {

    public interface OnClassroomDeletedListener {
        void onClassroomDeleted(String classId, String className);
    }

    private static ClassroomRepository instance;
    private final Map<String, Classroom> classroomMap = new HashMap<>();
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
}
