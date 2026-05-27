package ru.kirsachik.uas.dto;

import jakarta.validation.constraints.Size;

public record ActionReviewRequest(
        boolean reviewed,
        @Size(max = 512) String adminComment
) {
}
