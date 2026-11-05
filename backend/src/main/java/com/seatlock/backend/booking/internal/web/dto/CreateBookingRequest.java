package com.seatlock.backend.booking.internal.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateBookingRequest(
        @NotNull Long showId,
        @NotEmpty List<@NotNull Long> showSeatIds
) {
}
