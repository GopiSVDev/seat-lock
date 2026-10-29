package com.seatlock.backend.inventory.internal.web.dto;

import com.seatlock.backend.inventory.internal.domain.SeatType;
import com.seatlock.backend.inventory.internal.domain.ShowSeatStatus;
import com.seatlock.backend.inventory.internal.repository.SeatMapSeat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public record SeatMapResponse(
        Long showId,
        int totalSeats,
        long availableSeats,
        List<Row> rows
) {

    // Screens are laid out row by row with one seat type per row
    public record Row(String label, SeatType seatType, List<SeatInfo> seats) {
    }

    public record SeatInfo(Long id, int number, ShowSeatStatus status) {
    }

    public static SeatMapResponse from(Long showId, List<SeatMapSeat> seats) {
        List<Row> rows = seats.stream()
                .collect(Collectors.groupingBy(SeatMapSeat::rowLabel, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new Row(entry.getKey(), entry.getValue().getFirst().seatType(),
                        entry.getValue().stream()
                                .map(seat -> new SeatInfo(seat.id(), seat.seatNumber(), seat.status()))
                                .toList()))
                .toList();

        long available = seats.stream().filter(seat -> seat.status() == ShowSeatStatus.AVAILABLE).count();
        return new SeatMapResponse(showId, seats.size(), available, rows);
    }
}
