package com.example.smartattendancemanagementsystem.model;

public class AttendanceRecord {
    private String attendanceId;
    private String userId;
    private String userName;
    private String classId;
    private String className;
    private String date; // "yyyy-MM-dd"
    private String status; // "Present", "Absent", "Late"
    private String notes;
    private long timestamp;

    public AttendanceRecord() {
        // Firestore required
    }

    public AttendanceRecord(String attendanceId, String userId, String userName,
                            String classId, String className, String date,
                            String status, String notes, long timestamp) {
        this.attendanceId = attendanceId;
        this.userId = userId;
        this.userName = userName;
        this.classId = classId;
        this.className = className;
        this.date = date;
        this.status = status;
        this.notes = notes;
        this.timestamp = timestamp;
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

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
