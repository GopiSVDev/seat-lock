package com.seatlock.backend.catalogue.internal.show.dto;

import com.seatlock.backend.catalogue.internal.show.Language;
import com.seatlock.backend.catalogue.internal.show.Show;
import com.seatlock.backend.catalogue.internal.show.ShowFormat;
import java.time.Instant;

public record ShowResponse(
        Long id,
        Long movieId,
        String movieTitle,
        Long screenId,
        String screenName,
        Instant startTime,
        Instant endTime,
        Language language,
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
