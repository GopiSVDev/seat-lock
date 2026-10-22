package com.seatlock.backend.catalogue.internal.web.dto;

import com.seatlock.backend.catalogue.internal.domain.Certification;
import com.seatlock.backend.catalogue.internal.domain.Movie;

import java.time.LocalDate;

public record MovieResponse(
        Long id,
        String title,
        String description,
        int durationMinutes,
        Certification certification,
        LocalDate releaseDate
) {

    public static MovieResponse from(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getDescription(),
                movie.getDurationMinutes(),
                movie.getCertification(),
                movie.getReleaseDate()
        );
    }
}
