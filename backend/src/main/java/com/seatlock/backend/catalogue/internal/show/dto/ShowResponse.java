package com.seatlock.backend.catalogue.internal.show.dto;

import com.seatlock.backend.catalogue.internal.screen.SeatType;
import com.seatlock.backend.catalogue.internal.show.Language;
import com.seatlock.backend.catalogue.internal.show.Show;
import com.seatlock.backend.catalogue.internal.show.ShowFormat;
import com.seatlock.backend.catalogue.internal.show.ShowPrice;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ShowResponse(
        Long id,
        Long movieId,
        String movieTitle,
        Long screenId,
        String screenName,
        Instant startTime,
        Instant endTime,
        Language language,
        ShowFormat format,
        Map<SeatType, Long> prices
) {

    public static ShowResponse from(Show show, List<ShowPrice> prices) {
        Map<SeatType, Long> byType = new EnumMap<>(SeatType.class);
        prices.forEach(price -> byType.put(price.getSeatType(), price.getPrice()));

        return new ShowResponse(
                show.getId(),
                show.getMovie().getId(),
                show.getMovie().getTitle(),
                show.getScreen().getId(),
                show.getScreen().getName(),
                show.getStartTime(),
                show.getEndTime(),
                show.getLanguage(),
                show.getFormat(),
                byType
        );
    }
}
