package com.seatlock.backend.catalogue.internal.repository;

import com.seatlock.backend.catalogue.internal.domain.Certification;
import com.seatlock.backend.catalogue.internal.domain.Genre;
import com.seatlock.backend.catalogue.internal.domain.Language;
import com.seatlock.backend.catalogue.internal.domain.ShowFormat;

import java.time.LocalDate;
import java.util.List;

// A movie as a list shows it. languages/formats: what upcoming shows offer in the city; null without a city.
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
