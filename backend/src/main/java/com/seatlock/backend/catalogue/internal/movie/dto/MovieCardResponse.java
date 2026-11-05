package com.seatlock.backend.catalogue.internal.movie.dto;

import com.seatlock.backend.catalogue.internal.movie.Certification;
import com.seatlock.backend.catalogue.internal.movie.Genre;
import com.seatlock.backend.catalogue.internal.movie.MovieCard;
import com.seatlock.backend.catalogue.internal.show.Language;
import com.seatlock.backend.catalogue.internal.show.ShowFormat;
import java.time.LocalDate;
import java.util.List;

public record MovieCardResponse(
        Long id,
        String title,
        String posterUrl,
        List<Genre> genres,
        Certification certification,
        int durationMinutes,
        LocalDate releaseDate,
        List<Language> languages,
        List<ShowFormat> formats
) {

    public static MovieCardResponse from(MovieCard card) {
        return new MovieCardResponse(card.id(), card.title(), card.posterUrl(), card.genres(), card.certification(),
                card.durationMinutes(), card.releaseDate(), card.languages(), card.formats());
    }
}
