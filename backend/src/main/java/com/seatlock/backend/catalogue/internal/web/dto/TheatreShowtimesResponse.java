package com.seatlock.backend.catalogue.internal.web.dto;

import com.seatlock.backend.catalogue.internal.domain.Certification;
import com.seatlock.backend.catalogue.internal.web.dto.ShowtimesResponse.Showtime;

import java.time.LocalDate;
import java.util.List;

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
