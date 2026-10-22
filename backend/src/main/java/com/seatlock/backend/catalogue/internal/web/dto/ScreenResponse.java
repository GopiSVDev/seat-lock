package com.seatlock.backend.catalogue.internal.web.dto;

import com.seatlock.backend.catalogue.internal.domain.Screen;
import com.seatlock.backend.catalogue.internal.domain.Seat;
import com.seatlock.backend.catalogue.internal.domain.SeatType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public record ScreenResponse(
        Long id,
        Long theatreId,
        String name,
        int totalSeats,
        List<Row> rows
) {

    public record Row(String label, List<SeatInfo> seats) {
    }

    public record SeatInfo(Long id, int number, SeatType seatType) {
    }

    // Expects seats in layout order; groups them by row keeping that order
    public static ScreenResponse from(Screen screen, List<Seat> seats) {
        List<Row> rows = seats.stream()
                .collect(Collectors.groupingBy(Seat::getRowLabel, LinkedHashMap::new,
                        Collectors.mapping(seat -> new SeatInfo(seat.getId(), seat.getSeatNumber(), seat.getSeatType()),
                                Collectors.toList())))
                .entrySet().stream()
                .map(entry -> new Row(entry.getKey(), entry.getValue()))
                .toList();

        return new ScreenResponse(screen.getId(), screen.getTheatre().getId(), screen.getName(), seats.size(), rows);
    }
}
