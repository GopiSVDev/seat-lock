package com.seatlock.backend.inventory.internal.web.dto;

import com.seatlock.backend.inventory.internal.domain.SeatType;
import com.seatlock.backend.inventory.internal.repository.LayoutSeat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public record SeatLayoutResponse(
        Long showId,
        int totalSeats,
        List<Row> rows
) {

    // price in paise; a row has one seat type, so one price
    public record Row(String label, SeatType seatType, long price, List<SeatInfo> seats) {
    }

    public record SeatInfo(Long id, int number) {
    }

    public static SeatLayoutResponse from(Long showId, List<LayoutSeat> seats) {
        List<Row> rows = seats.stream()
                .collect(Collectors.groupingBy(LayoutSeat::rowLabel, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new Row(entry.getKey(), entry.getValue().getFirst().seatType(),
                        entry.getValue().getFirst().price(),
                        entry.getValue().stream()
                                .map(seat -> new SeatInfo(seat.id(), seat.seatNumber()))
                                .toList()))
                .toList();

        return new SeatLayoutResponse(showId, seats.size(), rows);
    }
}
