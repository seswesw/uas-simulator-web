package ru.kirsachik.uas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.kirsachik.uas.model.UserRole;

public record UserUpdateRequest(
        @NotBlank @Size(max = 128) String fullName,
        @NotNull UserRole role,
        boolean active,
        @Size(max = 64) String password
) {
}
