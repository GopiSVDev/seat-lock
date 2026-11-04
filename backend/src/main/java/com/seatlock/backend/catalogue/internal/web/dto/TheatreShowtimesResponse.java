package com.seatlock.backend.catalogue.internal.web.dto;

import com.seatlock.backend.catalogue.internal.domain.Certification;
import com.seatlock.backend.catalogue.internal.web.dto.ShowtimesResponse.Showtime;

import java.time.LocalDate;
import java.util.List;

// A theatre's page: its shows on one date in India, grouped by movie.
// dates: the next dates with upcoming shows (for the date strip), whatever date was asked for.
public record TheatreShowtimesResponse(
        TheatreResponse theatre,
        LocalDate date,
        List<LocalDate> dates,
        List<MovieShows> movies
) {

    public record MovieShows(Long id, String title, String posterUrl, Certification certification,
                             int durationMinutes, List<Showtime> shows) {
    }
}
