package com.seatlock.backend.catalogue.internal.web.dto;

import com.seatlock.backend.catalogue.internal.domain.Certification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CreateMovieRequest(
        @NotBlank String title,
        String description,
        @Positive int durationMinutes,
        @NotNull Certification certification,
        @NotNull LocalDate releaseDate
) {
}
