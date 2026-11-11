package com.seatlock.backend.inventory;

import java.time.Instant;
import java.util.List;

// A hold being paid for: still AVAILABLE in Postgres, kept by the hold until expiresAt
public record HeldSeats(Long showId, List<BookedSeat> seats, Instant expiresAt) {
}
