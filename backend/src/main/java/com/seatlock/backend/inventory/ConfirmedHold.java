package com.seatlock.backend.inventory;

import java.util.List;

public record ConfirmedHold(Long showId, List<BookedSeat> seats) {
}
