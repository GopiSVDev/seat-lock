package com.seatlock.backend.booking.internal.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

// Max 10 seats per booking, like most ticketing sites. Seat types mirror inventory's SeatType.
public record CreateBestAvailableBookingRequest(
        @NotNull Long showId,
        @NotBlank String customerName,
        @Positive @Max(10) int quantity,
        @NotNull @Pattern(regexp = "REGULAR|PREMIUM|RECLINER") String seatType
) {
}
