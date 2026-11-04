package com.seatlock.backend.catalogue.internal.repository;

import com.seatlock.backend.catalogue.internal.domain.Certification;
import com.seatlock.backend.catalogue.internal.domain.Language;
import com.seatlock.backend.catalogue.internal.domain.ShowFormat;

import java.time.Instant;

public record ShowtimeRow(
        Long theatreId,
        String theatreName,
        String theatreAddress,
        Long movieId,
        String movieTitle,
        String moviePosterUrl,
        Certification movieCertification,
        int movieDurationMinutes,
        Long showId,
        Instant startTime,
        Language language,
        ShowFormat format,
        String screenName
) {
}
