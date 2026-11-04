package com.seatlock.backend.catalogue.internal.web.dto;

import com.seatlock.backend.catalogue.internal.domain.Language;
import com.seatlock.backend.catalogue.internal.domain.ShowFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ShowtimesResponse(
        Long movieId,
        Long cityId,
        LocalDate date,
        List<LocalDate> dates,
        List<TheatreShows> theatres
) {

    public record TheatreShows(Long id, String name, String address, List<Showtime> shows) {
    }

    public record Showtime(Long id, Instant startTime, Language language, ShowFormat format, String screenName) {
    }
}
