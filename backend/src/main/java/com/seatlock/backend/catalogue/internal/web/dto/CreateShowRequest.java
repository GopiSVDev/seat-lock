package com.seatlock.backend.catalogue.internal.web.dto;

import com.seatlock.backend.catalogue.internal.domain.ShowFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateShowRequest(
        @NotNull Long movieId,
        @NotNull Long screenId,
        @NotNull @Future Instant startTime,
        @NotBlank String language,
        @NotNull ShowFormat format
) {
}
