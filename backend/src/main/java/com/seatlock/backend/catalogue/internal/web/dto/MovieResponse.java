package com.seatlock.backend.catalogue.internal.web.dto;

import com.seatlock.backend.catalogue.internal.domain.Certification;
import com.seatlock.backend.catalogue.internal.domain.Genre;
import com.seatlock.backend.catalogue.internal.domain.Movie;

import java.time.LocalDate;
import java.util.List;

public record MovieResponse(
        Long id,
        String title,
        String description,
        int durationMinutes,
        Certification certification,
        LocalDate releaseDate,
        String posterUrl,
        List<Genre> genres
) {

    public static MovieResponse from(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getDescription(),
                movie.getDurationMinutes(),
                movie.getCertification(),
                movie.getReleaseDate(),
                movie.getPosterUrl(),
                List.copyOf(movie.getGenres())
        );
    }
}
