package com.example.smartattendancemanagementsystem.model;

import com.example.smartattendancemanagementsystem.util.AppClock;

public class AttendanceRecord {
    private String attendanceId;
    private String userId;
    private String userName;
    private String classId;
    private String className;
    private String teacherId; // denormalised so instructors can query without a join
    private String date;      // "yyyy-MM-dd" in the class timezone
    private String slotId;    // "MONDAY_0900" or "GENERAL"
    private String status;    // "Present", "Absent", "Late"
    private String notes;
    private long timestamp;

    public AttendanceRecord() {
        // Firestore required
    }

    public AttendanceRecord(String attendanceId, String userId, String userName,
                            String classId, String className, String teacherId,
                            String date, String slotId, String status, String notes,
                            long timestamp) {
        this.attendanceId = attendanceId;
        this.userId = userId;
        this.userName = userName;
        this.classId = classId;
        this.className = className;
        this.teacherId = teacherId;
        this.date = date;
        this.slotId = slotId;
        this.status = status;
        this.notes = notes;
        this.timestamp = timestamp;
    }

    /** Records written before per-period attendance existed carry no slotId. */
    public String getSlotId() {
        return slotId == null || slotId.isEmpty() ? AppClock.SLOT_FALLBACK : slotId;
    }

    public void setSlotId(String slotId) { this.slotId = slotId; }

    /** True for the statuses that count towards the attendance percentage. */
    public boolean isAttended() {
        return "Present".equalsIgnoreCase(status) || "Late".equalsIgnoreCase(status);
    }

    public String getAttendanceId() { return attendanceId; }
    public void setAttendanceId(String attendanceId) { this.attendanceId = attendanceId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getTeacherId() { return teacherId; }
    public void setTeacherId(String teacherId) { this.teacherId = teacherId; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
