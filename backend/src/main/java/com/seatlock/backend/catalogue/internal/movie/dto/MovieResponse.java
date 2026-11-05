package com.seatlock.backend.catalogue.internal.movie.dto;

import com.seatlock.backend.catalogue.internal.movie.Certification;
import com.seatlock.backend.catalogue.internal.movie.Genre;
import com.seatlock.backend.catalogue.internal.movie.Movie;
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
