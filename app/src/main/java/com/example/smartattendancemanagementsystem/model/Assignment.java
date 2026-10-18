package com.example.smartattendancemanagementsystem.model;

public class Assignment {
    private String assignmentId;
    private String classId;
    private String className;
    private String title;
    private String instructions;
    private String dueDate; // "yyyy-MM-dd HH:mm"
    private int maxMarks;
    private String createdBy;
    private long timestamp;

    public Assignment() {
        // Firestore required
    }

    public Assignment(String assignmentId, String classId, String className,
                      String title, String instructions, String dueDate,
                      int maxMarks, String createdBy, long timestamp) {
        this.assignmentId = assignmentId;
        this.classId = classId;
        this.className = className;
        this.title = title;
        this.instructions = instructions;
        this.dueDate = dueDate;
        this.maxMarks = maxMarks;
        this.createdBy = createdBy;
        this.timestamp = timestamp;
    }

    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public int getMaxMarks() { return maxMarks; }
    public void setMaxMarks(int maxMarks) { this.maxMarks = maxMarks; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
