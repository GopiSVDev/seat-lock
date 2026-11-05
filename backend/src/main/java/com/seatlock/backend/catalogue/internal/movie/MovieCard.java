package com.seatlock.backend.catalogue.internal.movie;

import com.seatlock.backend.catalogue.internal.show.Language;
import com.seatlock.backend.catalogue.internal.show.ShowFormat;
import java.time.LocalDate;
import java.util.List;

// languages/formats: null without a city
public record MovieCard(
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
}
