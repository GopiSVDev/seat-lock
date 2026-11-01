package com.seatlock.backend.inventory.internal.web.dto;

import com.seatlock.backend.inventory.internal.repository.SeatAvailability;

import java.util.List;

// Ids of seats that can't be booked right now; every other seat in the layout is available.
public record SeatAvailabilityResponse(
        Long showId,
        int availableSeats,
        List<Long> unavailable
) {

    public static SeatAvailabilityResponse from(Long showId, SeatAvailability availability) {
        return new SeatAvailabilityResponse(showId,
                availability.totalSeats() - availability.unavailable().size(), availability.unavailable());
    }
}
