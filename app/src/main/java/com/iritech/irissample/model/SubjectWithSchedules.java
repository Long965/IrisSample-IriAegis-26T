package com.iritech.irissample.model;

/**
 * Model cho Môn học
 * Bao gồm thông tin giảng viên phụ trách (instructor) và trạng thái phân công
 */
public class SubjectWithSchedules {
    private String subjectId;
    private String subjectName;
    private String timeSlot;
    private String createdBy;
    private String instructorId;
    private String subjectStatus;
    private String instructorName;
    private String createdAt;

    // Constructor đầy đủ
    public SubjectWithSchedules(String subjectId, String subjectName, String timeSlot,
                                String createdBy, String instructorId, String subjectStatus,
                                String instructorName, String createdAt) {
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.timeSlot = timeSlot;
        this.createdBy = createdBy;
        this.instructorId = instructorId;
        this.subjectStatus = subjectStatus;
        this.instructorName = instructorName;
        this.createdAt = createdAt;
    }

    // Constructor tương thích ngược (cho code cũ nếu cần)
    public SubjectWithSchedules(String subjectId, String subjectName, String createdBy) {
        this(subjectId, subjectName, null, createdBy, null, "UNASSIGNED", null, null);
    }

    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getInstructorId() { return instructorId; }
    public void setInstructorId(String instructorId) { this.instructorId = instructorId; }

    public String getSubjectStatus() { return subjectStatus; }
    public void setSubjectStatus(String subjectStatus) { this.subjectStatus = subjectStatus; }

    public String getInstructorName() { return instructorName; }
    public void setInstructorName(String instructorName) { this.instructorName = instructorName; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isAssigned() {
        return "ASSIGNED".equals(subjectStatus);
    }
}
