package ru.kirsachik.uas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record DroneRequest(
        @NotBlank String callsign,
        @NotBlank String model,
        @Positive double maxSpeedMs,
        @Positive double maxAltitudeM,
        @Positive double batteryCapacityMah,
        double latitude,
        double longitude
) {
}
