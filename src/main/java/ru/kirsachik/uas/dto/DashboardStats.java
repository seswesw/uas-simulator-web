package ru.kirsachik.uas.dto;

public record DashboardStats(
        int totalDrones,
        int activeDrones,
        int totalSessions,
        int runningSessions,
        int unacknowledgedAlerts,
        Long activeSessionId
) {
}
