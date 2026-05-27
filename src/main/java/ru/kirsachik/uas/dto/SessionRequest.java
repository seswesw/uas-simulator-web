package ru.kirsachik.uas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SessionRequest(
        @NotBlank String name,
        String description,
        @NotNull Long droneId,
        @NotEmpty List<WaypointRequest> waypoints
) {
}
