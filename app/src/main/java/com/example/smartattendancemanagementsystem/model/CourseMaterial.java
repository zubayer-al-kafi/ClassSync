package com.example.smartattendancemanagementsystem.model;

public class CourseMaterial {
    private String materialId;
    private String classId;
    private String className;
    private String title;
    private String description;
    private String fileUrl; // URL or web drive link
    private String authorId;
    private String authorName;
    private long timestamp;

    public CourseMaterial() {
        // Firestore required
    }

    public CourseMaterial(String materialId, String classId, String className,
                          String title, String description, String fileUrl,
                          String authorId, String authorName, long timestamp) {
        this.materialId = materialId;
        this.classId = classId;
        this.className = className;
        this.title = title;
        this.description = description;
        this.fileUrl = fileUrl;
        this.authorId = authorId;
        this.authorName = authorName;
        this.timestamp = timestamp;
    }

    public String getMaterialId() { return materialId; }
    public void setMaterialId(String materialId) { this.materialId = materialId; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
