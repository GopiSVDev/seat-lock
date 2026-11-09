package com.seatlock.backend.inventory.internal.repository;

public record SeatState(Long showSeatId, Long showId, String label, long price, String status) {
}
