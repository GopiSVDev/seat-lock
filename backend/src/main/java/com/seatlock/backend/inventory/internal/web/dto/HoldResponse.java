package com.seatlock.backend.inventory.internal.web.dto;

import com.seatlock.backend.inventory.internal.repository.SeatState;

import java.time.Instant;
import java.util.List;

public record HoldResponse(
        String id,
        Long showId,
        Instant expiresAt,
        List<Seat> seats
) {

    public static HoldResponse of(String id, Long showId, Instant expiresAt, List<SeatState> seats) {
        return new HoldResponse(id, showId, expiresAt,
                seats.stream().map(seat -> new Seat(seat.showSeatId(), seat.label())).toList());
    }

    public record Seat(Long showSeatId, String label) {
    }
}
