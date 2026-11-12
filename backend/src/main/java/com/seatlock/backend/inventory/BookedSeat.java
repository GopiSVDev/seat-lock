package com.seatlock.backend.inventory;

// price in paise
public record BookedSeat(Long showSeatId, String label, long price) {
}
