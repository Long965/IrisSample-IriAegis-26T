package com.iritech.irissample.model;

/**
 * Model đại diện cho một bản ghi điểm danh của sinh viên.
 * Dùng trong AttendanceManagementActivity để hiển thị danh sách điểm danh.
 */
public class AttendanceRecord {

    public static final String STATUS_ON_TIME = "ON_TIME";
    public static final String STATUS_LATE = "LATE";
    public static final String STATUS_ABSENT = "ABSENT";

    private String studentId;
    private String fullName;
    private boolean attended;
    private String checkinDate;  // Format: dd/MM/yyyy
    private String checkinTime;  // Format: HH:mm:ss
    private String status;       // ON_TIME | LATE | ABSENT

    // ===== Dùng cho chế độ "Theo kì" - gộp theo từng sinh viên =====
    private boolean isSemesterAggregate = false;
    private int onTimeSessionCount;
    private int lateSessionCount;
    private int absentSessionCount;
    private int totalSessionCount;

    // Constructor đầy đủ (giữ tương thích code cũ - attended = true mặc định là ON_TIME)
    public AttendanceRecord(String studentId, String fullName, boolean attended,
                            String checkinDate, String checkinTime) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.attended = attended;
        this.checkinDate = checkinDate;
        this.checkinTime = checkinTime;
        this.status = attended ? STATUS_ON_TIME : STATUS_ABSENT;
    }

    // Constructor đầy đủ kèm trạng thái rõ ràng (ON_TIME / LATE / ABSENT)
    public AttendanceRecord(String studentId, String fullName, String status,
                            String checkinDate, String checkinTime) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.status = status;
        this.attended = !STATUS_ABSENT.equals(status);
        this.checkinDate = checkinDate;
        this.checkinTime = checkinTime;
    }

    // Constructor cho sinh viên chưa điểm danh (vắng mặt)
    public AttendanceRecord(String studentId, String fullName) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.attended = false;
        this.checkinDate = null;
        this.checkinTime = null;
        this.status = STATUS_ABSENT;
    }

    /**
     * Constructor cho chế độ xem theo kì: mỗi sinh viên 1 dòng,
     * chứa số buổi đúng giờ / đi muộn / vắng trên tổng số buổi học trong kì.
     */
    public AttendanceRecord(String studentId, String fullName,
                            int onTimeSessionCount, int lateSessionCount,
                            int absentSessionCount, int totalSessionCount) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.isSemesterAggregate = true;
        this.onTimeSessionCount = onTimeSessionCount;
        this.lateSessionCount = lateSessionCount;
        this.absentSessionCount = absentSessionCount;
        this.totalSessionCount = totalSessionCount;
        this.attended = (onTimeSessionCount + lateSessionCount) > 0;
        this.status = STATUS_ON_TIME; // chỉ để tương thích, không dùng để lọc trong chế độ này
    }

    // Getters
    public String getStudentId() {
        return studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isAttended() {
        return attended;
    }

    public String getCheckinDate() {
        return checkinDate;
    }

    public String getCheckinTime() {
        return checkinTime;
    }

    public String getStatus() {
        return status;
    }

    public boolean isLate() {
        return STATUS_LATE.equals(status);
    }

    public boolean isAbsent() {
        return STATUS_ABSENT.equals(status);
    }

    public boolean isOnTime() {
        return STATUS_ON_TIME.equals(status);
    }

    // Setters
    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setAttended(boolean attended) {
        this.attended = attended;
    }

    public void setCheckinDate(String checkinDate) {
        this.checkinDate = checkinDate;
    }

    public void setCheckinTime(String checkinTime) {
        this.checkinTime = checkinTime;
    }

    public void setStatus(String status) {
        this.status = status;
        this.attended = !STATUS_ABSENT.equals(status);
    }

    // Helper method để hiển thị thời gian ngắn gọn (HH:mm)
    public String getShortTime() {
        if (checkinTime == null || checkinTime.isEmpty()) {
            return "--";
        }
        if (checkinTime.length() >= 5) {
            return checkinTime.substring(0, 5);
        }
        return checkinTime;
    }

    // Helper method để hiển thị ngày ngắn gọn (dd/MM)
    public String getShortDate() {
        if (checkinDate == null || checkinDate.isEmpty()) {
            return "--";
        }
        if (checkinDate.length() >= 5) {
            return checkinDate.substring(0, 5);
        }
        return checkinDate;
    }

    // ===== Getters cho chế độ Theo kì =====

    public boolean isSemesterAggregate() {
        return isSemesterAggregate;
    }

    public int getOnTimeSessionCount() {
        return onTimeSessionCount;
    }

    public int getLateSessionCount() {
        return lateSessionCount;
    }

    public int getAbsentSessionCount() {
        return absentSessionCount;
    }

    public int getTotalSessionCount() {
        return totalSessionCount;
    }

    /**
     * Trả về chuỗi "x/N buổi" tương ứng với trạng thái (tab) đang được xem.
     */
    public String getSessionFractionText(String displayStatus) {
        int count;
        if (STATUS_LATE.equals(displayStatus)) {
            count = lateSessionCount;
        } else if (STATUS_ABSENT.equals(displayStatus)) {
            count = absentSessionCount;
        } else {
            count = onTimeSessionCount;
        }
        return count + "/" + totalSessionCount + " buổi";
    }
}