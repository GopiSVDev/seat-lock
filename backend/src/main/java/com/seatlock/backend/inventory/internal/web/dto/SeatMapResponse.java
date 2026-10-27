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

    public record Row(String label, List<SeatInfo> seats) {
    }

    public record SeatInfo(Long showSeatId, int number, SeatType seatType, ShowSeatStatus status) {
    }

    public static SeatMapResponse from(Long showId, List<SeatMapSeat> seats) {
        List<Row> rows = seats.stream()
                .collect(Collectors.groupingBy(SeatMapSeat::rowLabel, LinkedHashMap::new,
                        Collectors.mapping(seat -> new SeatInfo(seat.id(), seat.seatNumber(),
                                seat.seatType(), seat.status()), Collectors.toList())))
                .entrySet().stream()
                .map(entry -> new Row(entry.getKey(), entry.getValue()))
                .toList();

        long available = seats.stream().filter(seat -> seat.status() == ShowSeatStatus.AVAILABLE).count();
        return new SeatMapResponse(showId, seats.size(), available, rows);
    }
}
