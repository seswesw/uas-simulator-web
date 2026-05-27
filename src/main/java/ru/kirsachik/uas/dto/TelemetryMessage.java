package ru.kirsachik.uas.dto;

import java.time.Instant;

public record TelemetryMessage(
        Long sessionId,
        Long droneId,
        String callsign,
        Instant timestamp,
        double latitude,
        double longitude,
        double altitudeM,
        double speedMs,
        double headingDeg,
        double pitchDeg,
        double rollDeg,
        double batteryPercent,
        int waypointIndex,
        int totalWaypoints,
        double progressPercent,
        String sessionStatus,
        String droneStatus,
        String weatherCode,
        String weatherName,
        double windSpeedMs,
        double precipitationMmH,
        double visibilityKm,
        double weatherSpeedMultiplier,
        double weatherBatteryMultiplier
) {
}
