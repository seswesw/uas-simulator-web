package ru.kirsachik.uas.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExternalTelemetryRequest(
        @NotNull Double latitude,
        @NotNull Double longitude,
        @Min(0) double altitudeM,
        @Min(0) double speedMs,
        @Min(0) @Max(360) double headingDeg,
        double pitchDeg,
        double rollDeg,
        @Min(0) @Max(100) double batteryPercent
) {
}
