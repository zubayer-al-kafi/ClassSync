package com.example.smartattendancemanagementsystem.model;

public class Submission {
    private String submissionId;
    private String assignmentId;
    private String classId;
    private String studentId;
    private String studentName;
    private String submissionText;
    private String fileUrl;
    private int score;
    private String feedback;
    private boolean graded;
    private long submittedAt;

    public Submission() {
        // Firestore required
    }

    public Submission(String submissionId, String assignmentId, String classId,
                      String studentId, String studentName, String submissionText,
                      String fileUrl, int score, String feedback, boolean graded,
                      long submittedAt) {
        this.submissionId = submissionId;
        this.assignmentId = assignmentId;
        this.classId = classId;
        this.studentId = studentId;
        this.studentName = studentName;
        this.submissionText = submissionText;
        this.fileUrl = fileUrl;
        this.score = score;
        this.feedback = feedback;
        this.graded = graded;
        this.submittedAt = submittedAt;
    }

    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }

    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getSubmissionText() { return submissionText; }
    public void setSubmissionText(String submissionText) { this.submissionText = submissionText; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public boolean isGraded() { return graded; }
    public void setGraded(boolean graded) { this.graded = graded; }

    public long getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(long submittedAt) { this.submittedAt = submittedAt; }
}
