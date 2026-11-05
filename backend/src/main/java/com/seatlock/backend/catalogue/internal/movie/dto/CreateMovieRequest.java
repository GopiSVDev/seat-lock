package com.seatlock.backend.catalogue.internal.movie.dto;

import com.seatlock.backend.catalogue.internal.movie.Certification;
import com.seatlock.backend.catalogue.internal.movie.Genre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;
import java.util.List;

public record CreateMovieRequest(
        @NotBlank String title,
        String description,
        @Positive int durationMinutes,
        @NotNull Certification certification,
        @NotNull LocalDate releaseDate,
        @URL @Size(max = 500) String posterUrl,
        @NotEmpty @Size(max = 3) List<@NotNull Genre> genres
) {
}
