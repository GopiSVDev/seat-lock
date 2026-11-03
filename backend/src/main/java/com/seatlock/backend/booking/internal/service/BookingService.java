package com.seatlock.backend.booking.internal.service;

import com.seatlock.backend.booking.internal.domain.Booking;
import com.seatlock.backend.booking.internal.domain.BookingStatus;
import com.seatlock.backend.booking.internal.repository.BookingRepository;
import com.seatlock.backend.booking.internal.repository.BookingSeatRepository;
import com.seatlock.backend.booking.internal.repository.BookingWriteRepository;
import com.seatlock.backend.booking.internal.web.dto.BookingResponse;
import com.seatlock.backend.booking.internal.web.dto.CreateBestAvailableBookingRequest;
import com.seatlock.backend.booking.internal.web.dto.CreateBookingRequest;
import com.seatlock.backend.inventory.BookedSeat;
import com.seatlock.backend.inventory.InventoryApi;
import com.seatlock.backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final InventoryApi inventoryApi;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final BookingWriteRepository bookingWriteRepository;

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        List<BookedSeat> bookedSeats = inventoryApi.bookSeats(request.showId(), request.showSeatIds());
        return confirm(request.showId(), request.customerName(), bookedSeats);
    }

    @Transactional
    public BookingResponse createBestAvailable(CreateBestAvailableBookingRequest request) {
        List<BookedSeat> bookedSeats = inventoryApi.bookBestAvailable(request.showId(), request.quantity(),
                request.seatType());
        return confirm(request.showId(), request.customerName(), bookedSeats);
    }

    // Runs inside the caller's transaction, after inventory has booked the seats
    private BookingResponse confirm(Long showId, String customerName, List<BookedSeat> bookedSeats) {
        // Microseconds: what timestamptz stores, so this response matches GET /bookings/{id}
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Long id = bookingWriteRepository.insert(showId, customerName, BookingStatus.CONFIRMED,
                createdAt, bookedSeats.stream().map(BookedSeat::showSeatId).toList(),
                bookedSeats.stream().map(BookedSeat::label).toList());

        List<BookingResponse.Seat> seats = bookedSeats.stream()
                .map(seat -> new BookingResponse.Seat(seat.showSeatId(), seat.label()))
                .toList();
        return new BookingResponse(id, showId, customerName, BookingStatus.CONFIRMED, createdAt, seats);
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking", id));
        return BookingResponse.from(booking, bookingSeatRepository.findByBookingIdOrderByIdAsc(id));
    }
}
