package ru.kirsachik.uas.dto;

import jakarta.validation.constraints.Positive;

public record WaypointRequest(
        double latitude,
        double longitude,
        @Positive double altitudeM,
        @Positive double speedMs
) {
}
