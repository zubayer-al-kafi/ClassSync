package com.example.smartattendancemanagementsystem.model;

import java.util.ArrayList;
import java.util.List;

public class Classroom {
    private String classId;
    private String className;
    private String subject;
    private String classCode;
    private String teacherId;
    private String teacherName;
    private List<String> coTeacherIds;
    private List<String> coTeacherNames;
    private int minThreshold;
    private boolean manualAttendanceAllowed;
    private List<ClassSlot> classSlots;
    private List<String> enrolledStudentIds;
    private long createdAt;

    public Classroom() {
        this.coTeacherIds = new ArrayList<>();
        this.coTeacherNames = new ArrayList<>();
        this.classSlots = new ArrayList<>();
        this.enrolledStudentIds = new ArrayList<>();
        this.minThreshold = 75;
        this.manualAttendanceAllowed = false;
    }

    public Classroom(String classId, String className, String subject, String classCode,
                     String teacherId, String teacherName, int minThreshold,
                     boolean manualAttendanceAllowed, List<ClassSlot> classSlots,
                     List<String> enrolledStudentIds, long createdAt) {
        this.classId = classId;
        this.className = className;
        this.subject = subject;
        this.classCode = classCode;
        this.teacherId = teacherId;
        this.teacherName = teacherName;
        this.coTeacherIds = new ArrayList<>();
        this.coTeacherNames = new ArrayList<>();
        this.minThreshold = minThreshold;
        this.manualAttendanceAllowed = manualAttendanceAllowed;
        this.classSlots = classSlots != null ? new ArrayList<>(classSlots) : new ArrayList<>();
        this.enrolledStudentIds = enrolledStudentIds != null ? new ArrayList<>(enrolledStudentIds) : new ArrayList<>();
        this.createdAt = createdAt;
    }

    public boolean isTeacherAssigned(String uid) {
        if (uid == null) return false;
        if (uid.equals(teacherId)) return true;
        return coTeacherIds != null && coTeacherIds.contains(uid);
    }

    public void addCoTeacher(String uid, String name) {
        if (coTeacherIds == null) coTeacherIds = new ArrayList<>();
        if (coTeacherNames == null) coTeacherNames = new ArrayList<>();
        if (uid != null && !coTeacherIds.contains(uid)) {
            coTeacherIds.add(uid);
            coTeacherNames.add(name != null ? name : "Co-Instructor");
        }
    }

    public List<ClassSlot> getTodaySlots() {
        List<ClassSlot> todayList = new ArrayList<>();
        if (classSlots != null) {
            for (ClassSlot slot : classSlots) {
                if (slot != null && slot.isSlotToday()) {
                    todayList.add(slot);
                }
            }
        }
        return todayList;
    }

    public boolean isScheduledToday() {
        if (classSlots == null || classSlots.isEmpty()) {
            return true;
        }
        return !getTodaySlots().isEmpty();
    }

    public boolean isClassActiveNow() {
        if (!isScheduledToday()) return false;
        List<ClassSlot> todaySlots = getTodaySlots();
        if (todaySlots.isEmpty()) return true;

        for (ClassSlot slot : todaySlots) {
            if (slot.isSlotActiveNow()) {
                return true;
            }
        }
        return false;
    }

    public String getClassStatus() {
        if (!isScheduledToday()) {
            return "NO_CLASS_TODAY";
        }
        List<ClassSlot> todaySlots = getTodaySlots();
        if (todaySlots.isEmpty()) return "ACTIVE";

        boolean hasUpcoming = false;
        boolean hasCompleted = false;

        for (ClassSlot slot : todaySlots) {
            String status = slot.getSlotStatus();
            if ("ACTIVE".equalsIgnoreCase(status)) {
                return "ACTIVE";
            } else if ("UPCOMING".equalsIgnoreCase(status)) {
                hasUpcoming = true;
            } else if ("COMPLETED".equalsIgnoreCase(status)) {
                hasCompleted = true;
            }
        }

        if (hasUpcoming) return "UPCOMING";
        if (hasCompleted) return "COMPLETED";
        return "NO_CLASS_TODAY";
    }

    public String getStartTime() {
        List<ClassSlot> todaySlots = getTodaySlots();
        if (!todaySlots.isEmpty() && todaySlots.get(0).getStartTime() != null) {
            return todaySlots.get(0).getStartTime();
        }
        return "09:00";
    }

    public String getEndTime() {
        List<ClassSlot> todaySlots = getTodaySlots();
        if (!todaySlots.isEmpty() && todaySlots.get(0).getEndTime() != null) {
            return todaySlots.get(0).getEndTime();
        }
        return "18:00";
    }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getClassCode() { return classCode; }
    public void setClassCode(String classCode) { this.classCode = classCode; }

    public String getTeacherId() { return teacherId; }
    public void setTeacherId(String teacherId) { this.teacherId = teacherId; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public List<String> getCoTeacherIds() { return coTeacherIds; }
    public void setCoTeacherIds(List<String> coTeacherIds) { this.coTeacherIds = coTeacherIds; }

    public List<String> getCoTeacherNames() { return coTeacherNames; }
    public void setCoTeacherNames(List<String> coTeacherNames) { this.coTeacherNames = coTeacherNames; }

    public int getMinThreshold() { return minThreshold; }
    public void setMinThreshold(int minThreshold) { this.minThreshold = minThreshold; }

    public boolean isManualAttendanceAllowed() { return manualAttendanceAllowed; }
    public void setManualAttendanceAllowed(boolean manualAttendanceAllowed) { this.manualAttendanceAllowed = manualAttendanceAllowed; }

    public List<ClassSlot> getClassSlots() { return classSlots; }
    public void setClassSlots(List<ClassSlot> classSlots) { this.classSlots = classSlots; }

    public List<String> getEnrolledStudentIds() { return enrolledStudentIds; }
    public void setEnrolledStudentIds(List<String> enrolledStudentIds) { this.enrolledStudentIds = enrolledStudentIds; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
