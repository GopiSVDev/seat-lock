package com.seatlock.backend.inventory.internal.web.dto;

import java.util.List;

// Ids of seats that can't be held right now (booked or held); every other seat in the layout is available.
public record SeatAvailabilityResponse(
        Long showId,
        int availableSeats,
        List<Long> unavailable
) {

    public static SeatAvailabilityResponse from(Long showId, int totalSeats, List<Long> unavailable) {
        return new SeatAvailabilityResponse(showId, totalSeats - unavailable.size(), unavailable);
    }
}
