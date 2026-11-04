package com.seatlock.backend.catalogue.internal.web.dto;

import com.seatlock.backend.catalogue.internal.domain.Language;
import com.seatlock.backend.catalogue.internal.domain.ShowFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// The "pick a theatre and time" page: shows of one movie in one city on one date (India), grouped by theatre.
// dates: the next dates with upcoming shows (for the date strip), whatever date was asked for.
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
