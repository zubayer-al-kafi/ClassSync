package com.example.smartattendancemanagementsystem.model;

public class User {
    private String uid;
    private String name;
    private String email;
    private String role; // "Instructor" or "Student"
    private String studentId; // Roll Number / Student ID (Non-editable once set)
    private String department;
    private String phone;
    private String address;
    private String profilePictureUrl;
    private String deviceId; // Unique hardware device binding
    private long createdAt;

    public User() {
        // Firestore required
    }

    public User(String uid, String name, String email, String role, String department, long createdAt) {
        this(uid, name, email, role, null, department, null, createdAt);
    }

    public User(String uid, String name, String email, String role, String studentId, String department, String deviceId, long createdAt) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.role = role != null ? role : "Student";
        this.studentId = studentId;
        this.department = department;
        this.deviceId = deviceId;
        this.createdAt = createdAt;
    }

    public boolean isInstructor() {
        return "Instructor".equalsIgnoreCase(role) || "Teacher".equalsIgnoreCase(role);
    }

    public boolean isStudent() {
        return "Student".equalsIgnoreCase(role);
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
