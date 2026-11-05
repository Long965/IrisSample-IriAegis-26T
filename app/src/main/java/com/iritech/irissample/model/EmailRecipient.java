package com.iritech.irissample.model;

/**
 * Model cho email recipient (người nhận báo cáo điểm danh)
 * Mỗi môn học có danh sách email recipients riêng
 */
public class EmailRecipient {
    private int emailId;
    private String subjectId;
    private String emailAddress;
    private String recipientName;
    private String addedDate;
    private boolean isSelected; // Cho checkbox selection trong UI

    public EmailRecipient() {
    }

    public EmailRecipient(int emailId, String subjectId, String emailAddress, 
                          String recipientName, String addedDate) {
        this.emailId = emailId;
        this.subjectId = subjectId;
        this.emailAddress = emailAddress;
        this.recipientName = recipientName;
        this.addedDate = addedDate;
        this.isSelected = false;
    }

    // Getters and Setters
    public int getEmailId() {
        return emailId;
    }

    public void setEmailId(int emailId) {
        this.emailId = emailId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getAddedDate() {
        return addedDate;
    }

    public void setAddedDate(String addedDate) {
        this.addedDate = addedDate;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    /**
     * Hiển thị tên hoặc email nếu không có tên
     */
    public String getDisplayName() {
        if (recipientName != null && !recipientName.trim().isEmpty()) {
            return recipientName;
        }
        return emailAddress;
    }
}
