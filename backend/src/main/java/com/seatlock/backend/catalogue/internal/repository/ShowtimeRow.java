package com.seatlock.backend.catalogue.internal.repository;

import com.seatlock.backend.catalogue.internal.domain.Language;
import com.seatlock.backend.catalogue.internal.domain.ShowFormat;

import java.time.Instant;

// One upcoming show with the theatre it plays in (flat; the service groups rows by theatre)
public record ShowtimeRow(
        Long theatreId,
        String theatreName,
        String theatreAddress,
        Long showId,
        Instant startTime,
        Language language,
        ShowFormat format,
        String screenName
) {
}
