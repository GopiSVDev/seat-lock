package com.seatlock.backend.catalogue.internal.web.dto;

import com.seatlock.backend.catalogue.internal.domain.Show;
import com.seatlock.backend.catalogue.internal.domain.ShowFormat;

import java.time.Instant;

public record ShowResponse(
        Long id,
        Long movieId,
        String movieTitle,
        Long screenId,
        String screenName,
        Instant startTime,
        Instant endTime,
        String language,
        ShowFormat format
) {

    public static ShowResponse from(Show show) {
        return new ShowResponse(
                show.getId(),
                show.getMovie().getId(),
                show.getMovie().getTitle(),
                show.getScreen().getId(),
                show.getScreen().getName(),
                show.getStartTime(),
                show.getEndTime(),
                show.getLanguage(),
                show.getFormat()
        );
    }
}
