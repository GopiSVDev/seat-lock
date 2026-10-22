package com.seatlock.backend.booking.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateBookingRequest(
        @NotNull Long showId,
        @NotBlank String customerName,
        @NotEmpty List<@NotNull Long> showSeatIds
) {
}
