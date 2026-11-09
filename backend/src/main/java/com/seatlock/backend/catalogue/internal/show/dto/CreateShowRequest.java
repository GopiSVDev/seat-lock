package com.seatlock.backend.catalogue.internal.show.dto;

import com.seatlock.backend.catalogue.internal.screen.SeatType;
import com.seatlock.backend.catalogue.internal.show.Language;
import com.seatlock.backend.catalogue.internal.show.ShowFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Map;

public record CreateShowRequest(
        @NotNull Long movieId,
        @NotNull Long screenId,
        @NotNull @Future Instant startTime,
        @NotNull Language language,
        @NotNull ShowFormat format,
        // Paise per seat type; exactly the screen's seat types
        @NotEmpty Map<SeatType, @NotNull @Positive Long> prices
) {
}
