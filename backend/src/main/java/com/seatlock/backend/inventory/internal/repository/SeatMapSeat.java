package com.seatlock.backend.inventory.internal.repository;

import com.seatlock.backend.inventory.internal.domain.SeatType;
import com.seatlock.backend.inventory.internal.domain.ShowSeatStatus;

public record SeatMapSeat(Long id, String rowLabel, int seatNumber, SeatType seatType, ShowSeatStatus status) {
}
