package com.example.smartattendancemanagementsystem.model;

public class Announcement {
    private String announcementId;
    private String classId;
    private String className;
    private String title;
    private String content;
    private String authorId;
    private String authorName;
    private long timestamp;

    public Announcement() {
        // Firestore required
    }

    public Announcement(String announcementId, String classId, String className,
                        String title, String content, String authorId,
                        String authorName, long timestamp) {
        this.announcementId = announcementId;
        this.classId = classId;
        this.className = className;
        this.title = title;
        this.content = content;
        this.authorId = authorId;
        this.authorName = authorName;
        this.timestamp = timestamp;
    }

    public String getAnnouncementId() { return announcementId; }
    public void setAnnouncementId(String announcementId) { this.announcementId = announcementId; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
