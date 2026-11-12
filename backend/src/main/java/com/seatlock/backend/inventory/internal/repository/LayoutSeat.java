package com.seatlock.backend.inventory.internal.repository;

import com.seatlock.backend.inventory.internal.domain.SeatType;

public record LayoutSeat(Long id, String rowLabel, int seatNumber, SeatType seatType, long price) {
}
