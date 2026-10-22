package com.seatlock.backend.booking.internal.web.dto;

import com.seatlock.backend.booking.internal.domain.Booking;
import com.seatlock.backend.booking.internal.domain.BookingSeat;
import com.seatlock.backend.booking.internal.domain.BookingStatus;

import java.time.Instant;
import java.util.List;

public record BookingResponse(
        Long id,
        Long showId,
        String customerName,
        BookingStatus status,
        Instant createdAt,
        List<Seat> seats
) {

    public record Seat(Long showSeatId, String label) {
    }

    public static BookingResponse from(Booking booking, List<BookingSeat> bookingSeats) {
        List<Seat> seats = bookingSeats.stream()
                .map(seat -> new Seat(seat.getShowSeatId(), seat.getSeatLabel()))
                .toList();

        return new BookingResponse(
                booking.getId(),
                booking.getShowId(),
                booking.getCustomerName(),
                booking.getStatus(),
                booking.getCreatedAt(),
                seats
        );
    }
}
