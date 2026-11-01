package com.seatlock.backend.inventory.internal.repository;

import java.util.List;

public record SeatAvailability(int totalSeats, List<Long> unavailable) {
}
