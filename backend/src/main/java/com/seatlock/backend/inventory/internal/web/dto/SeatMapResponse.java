package com.seatlock.backend.inventory.internal.web.dto;

import com.seatlock.backend.inventory.internal.domain.SeatType;
import com.seatlock.backend.inventory.internal.domain.ShowSeat;
import com.seatlock.backend.inventory.internal.domain.ShowSeatStatus;

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

    public static SeatMapResponse from(Long showId, List<ShowSeat> showSeats) {
        List<Row> rows = showSeats.stream()
                .collect(Collectors.groupingBy(ShowSeat::getRowLabel, LinkedHashMap::new,
                        Collectors.mapping(seat -> new SeatInfo(seat.getId(), seat.getSeatNumber(),
                                seat.getSeatType(), seat.getStatus()), Collectors.toList())))
                .entrySet().stream()
                .map(entry -> new Row(entry.getKey(), entry.getValue()))
                .toList();

        long available = showSeats.stream().filter(seat -> seat.getStatus() == ShowSeatStatus.AVAILABLE).count();
        return new SeatMapResponse(showId, showSeats.size(), available, rows);
    }
}
