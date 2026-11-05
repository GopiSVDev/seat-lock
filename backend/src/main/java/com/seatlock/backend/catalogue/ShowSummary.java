package com.seatlock.backend.catalogue;

import java.time.Instant;

// Enums as names, so other modules don't depend on catalogue's internal types
public record ShowSummary(
        Long showId,
        Instant startTime,
        String language,
        String format,
        Long movieId,
        String movieTitle,
        String moviePosterUrl,
        String movieCertification,
        Long theatreId,
        String theatreName,
        String screenName
) {
}
