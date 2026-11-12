package com.seatlock.backend.catalogue.internal.show;

import com.seatlock.backend.catalogue.internal.movie.Certification;
import java.time.Instant;

public record ShowtimeRow(Long theatreId, String theatreName, String theatreAddress, Long movieId, String movieTitle,
        String moviePosterUrl, Certification movieCertification, int movieDurationMinutes, Long showId,
        Instant startTime, Language language, ShowFormat format, String screenName) {
}
