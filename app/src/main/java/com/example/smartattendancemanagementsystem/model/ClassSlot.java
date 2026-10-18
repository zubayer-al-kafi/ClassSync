package com.example.smartattendancemanagementsystem.model;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class ClassSlot {
    private String dayOfWeek; // e.g. "MONDAY", "WEDNESDAY"
    private String startTime; // e.g. "09:00"
    private String endTime;   // e.g. "10:30"

    public ClassSlot() {
        // Firestore required
    }

    public ClassSlot(String dayOfWeek, String startTime, String endTime) {
        this.dayOfWeek = dayOfWeek != null ? dayOfWeek.toUpperCase() : "MONDAY";
        this.startTime = startTime != null ? startTime : "09:00";
        this.endTime = endTime != null ? endTime : "10:30";
    }

    public boolean isSlotToday() {
        if (dayOfWeek == null) return false;
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.US);
        String todayName = dayFormat.format(calendar.getTime()).toUpperCase();
        return dayOfWeek.equalsIgnoreCase(todayName);
    }

    public boolean isSlotActiveNow() {
        if (!isSlotToday()) return false;
        try {
            Calendar now = Calendar.getInstance();
            int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

            int startMin = parseTimeToMinutes(startTime, 0);
            int endMin = parseTimeToMinutes(endTime, 23 * 60 + 59);

            return currentMinutes >= startMin && currentMinutes <= endMin;
        } catch (Exception e) {
            return true;
        }
    }

    public String getSlotStatus() {
        if (!isSlotToday()) {
            return "NO_CLASS_TODAY";
        }
        try {
            Calendar now = Calendar.getInstance();
            int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

            int startMin = parseTimeToMinutes(startTime, 0);
            int endMin = parseTimeToMinutes(endTime, 23 * 60 + 59);

            if (currentMinutes < startMin) {
                return "UPCOMING";
            } else if (currentMinutes <= endMin) {
                return "ACTIVE";
            } else {
                return "COMPLETED";
            }
        } catch (Exception e) {
            return "ACTIVE";
        }
    }

    private int parseTimeToMinutes(String timeStr, int defaultValue) {
        if (timeStr == null || timeStr.trim().isEmpty()) return defaultValue;
        try {
            String[] parts = timeStr.trim().split(":");
            int hrs = Integer.parseInt(parts[0]);
            int mins = Integer.parseInt(parts[1]);
            return hrs * 60 + mins;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
}
