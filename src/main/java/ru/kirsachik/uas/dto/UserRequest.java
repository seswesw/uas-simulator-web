package ru.kirsachik.uas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.kirsachik.uas.model.UserRole;

public record UserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 128) String fullName,
        @NotBlank @Size(min = 4, max = 64) String password,
        @NotNull UserRole role,
        boolean active
) {
}
