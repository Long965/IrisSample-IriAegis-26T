package com.iritech.irissample;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Tính khung giờ điểm danh dựa trên ca học (time_slot) của môn học.
 * Định dạng time_slot: "Thứ 2 - 7:00 đến 9:00"
 *
 * Quy tắc:
 * - [Bắt đầu, Bắt đầu+15p]     : ON_TIME_WINDOW - cho điểm danh đúng giờ
 * - (Bắt đầu+15p, Kết thúc]    : WAIT_END        - chặn, chờ cuối buổi
 * - (Kết thúc, Kết thúc+15p]   : LATE_WINDOW     - cho điểm danh muộn
 * - Ngoài các khung trên       : CLOSED          - chặn, ngoài giờ điểm danh
 */
public class AttendanceTimeUtils {

    public static final String STATUS_ON_TIME_WINDOW = "ON_TIME_WINDOW";
    public static final String STATUS_WAIT_END        = "WAIT_END";
    public static final String STATUS_LATE_WINDOW     = "LATE_WINDOW";
    public static final String STATUS_CLOSED          = "CLOSED";

    public static class TimeWindow {
        public final String startTime;   // giờ bắt đầu ca học
        public final String onTimeLimit; // startTime + 15p
        public final String endTime;     // giờ kết thúc ca học
        public final String lateLimit;   // endTime + 15p

        public TimeWindow(String startTime, String onTimeLimit, String endTime, String lateLimit) {
            this.startTime = startTime;
            this.onTimeLimit = onTimeLimit;
            this.endTime = endTime;
            this.lateLimit = lateLimit;
        }
    }

    /**
     * Parse "Thứ 2 - 7:00 đến 9:00" -> khung giờ điểm danh.
     * Không parse được -> fallback mặc định 08:00 - 09:00.
     */
    public static TimeWindow parseTimeSlot(String timeSlotStr) {
        String startTime = "08:00:00";
        String endTime = "09:00:00";

        if (timeSlotStr != null && timeSlotStr.contains("-") && timeSlotStr.contains("đến")) {
            try {
                String timePart = timeSlotStr.split("-")[1].trim(); // "7:00 đến 9:00"
                String[] parts = timePart.split("đến");
                startTime = toHms(parts[0].trim());
                endTime = toHms(parts[1].trim());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String onTimeLimit = addMinutes(startTime, 15);
        String lateLimit = addMinutes(endTime, 15);
        return new TimeWindow(startTime, onTimeLimit, endTime, lateLimit);
    }

    private static String toHms(String hourMinStr) {
        String[] hm = hourMinStr.split(":");
        int hour = Integer.parseInt(hm[0].trim());
        int min = Integer.parseInt(hm[1].trim());
        return String.format(Locale.getDefault(), "%02d:%02d:00", hour, min);
    }

    private static String addMinutes(String hms, int minutesToAdd) {
        String[] p = hms.split(":");
        int hour = Integer.parseInt(p[0]);
        int min = Integer.parseInt(p[1]) + minutesToAdd;
        hour += min / 60;
        min = min % 60;
        hour = hour % 24; // đề phòng ca học gần nửa đêm
        return String.format(Locale.getDefault(), "%02d:%02d:00", hour, min);
    }

    /** Giờ hiện tại theo giờ VN, dạng "HH:mm:ss" để so sánh chuỗi trực tiếp. */
    public static String getCurrentTimeHms() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return String.format(Locale.getDefault(), "%02d:%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND));
    }

    /** Xác định trạng thái điểm danh tại thời điểm hiện tại so với khung giờ ca học. */
    public static String evaluateCheckinWindow(String currentTimeHms, TimeWindow w) {
        if (currentTimeHms.compareTo(w.startTime) < 0) {
            return STATUS_CLOSED; // chưa tới giờ học
        }
        if (currentTimeHms.compareTo(w.onTimeLimit) <= 0) {
            return STATUS_ON_TIME_WINDOW;
        }
        if (currentTimeHms.compareTo(w.endTime) <= 0) {
            return STATUS_WAIT_END;
        }
        if (currentTimeHms.compareTo(w.lateLimit) <= 0) {
            return STATUS_LATE_WINDOW;
        }
        return STATUS_CLOSED;
    }
}