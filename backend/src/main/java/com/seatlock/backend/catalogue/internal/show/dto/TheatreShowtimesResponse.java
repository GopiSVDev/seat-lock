package com.seatlock.backend.catalogue.internal.show.dto;

import com.seatlock.backend.catalogue.internal.movie.Certification;
import com.seatlock.backend.catalogue.internal.show.dto.ShowtimesResponse.Showtime;
import com.seatlock.backend.catalogue.internal.theatre.dto.TheatreResponse;
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
