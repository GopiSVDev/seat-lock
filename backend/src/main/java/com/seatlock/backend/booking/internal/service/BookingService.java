package com.seatlock.backend.booking.internal.service;

import com.seatlock.backend.booking.internal.domain.Booking;
import com.seatlock.backend.booking.internal.domain.BookingSeat;
import com.seatlock.backend.booking.internal.repository.BookingRepository;
import com.seatlock.backend.booking.internal.repository.BookingSeatRepository;
import com.seatlock.backend.booking.internal.web.dto.BookingResponse;
import com.seatlock.backend.booking.internal.web.dto.CreateBookingRequest;
import com.seatlock.backend.inventory.BookedSeat;
import com.seatlock.backend.inventory.InventoryApi;
import com.seatlock.backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final InventoryApi inventoryApi;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        List<BookedSeat> bookedSeats = inventoryApi.bookSeats(request.showId(), request.showSeatIds());

        Booking booking = bookingRepository.save(new Booking(request.showId(), request.customerName()));

        List<BookingSeat> bookingSeats = bookingSeatRepository.saveAll(bookedSeats.stream()
                .map(seat -> new BookingSeat(booking, seat.showSeatId(), seat.label()))
                .toList());

        return BookingResponse.from(booking, bookingSeats);
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking", id));
        return BookingResponse.from(booking, bookingSeatRepository.findByBookingIdOrderByIdAsc(id));
    }
}
