package ru.kirsachik.uas.service;

public record WeatherSnapshot(
        String code,
        String name,
        double windSpeedMs,
        double precipitationMmH,
        double visibilityKm,
        double speedMultiplier,
        double accelerationMultiplier,
        double batteryDrainMultiplier,
        double stabilityMultiplier,
        String riskLevel
) {
}
