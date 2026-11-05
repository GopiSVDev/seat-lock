package com.seatlock.backend.booking.internal.service;

import com.seatlock.backend.booking.internal.domain.Booking;
import com.seatlock.backend.booking.internal.domain.BookingSeat;
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
import com.seatlock.backend.shared.security.CurrentUser;
import com.seatlock.backend.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    // Matches ix_booking_customer_created
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final InventoryApi inventoryApi;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final BookingWriteRepository bookingWriteRepository;

    @Transactional
    public BookingResponse create(CreateBookingRequest request, CurrentUser customer) {
        List<BookedSeat> bookedSeats = inventoryApi.bookSeats(request.showId(), request.showSeatIds());
        return confirm(request.showId(), customer, bookedSeats);
    }

    @Transactional
    public BookingResponse createBestAvailable(CreateBestAvailableBookingRequest request, CurrentUser customer) {
        List<BookedSeat> bookedSeats = inventoryApi.bookBestAvailable(request.showId(), request.quantity(),
                request.seatType());
        return confirm(request.showId(), customer, bookedSeats);
    }

    // Runs inside the caller's transaction, after inventory has booked the seats
    private BookingResponse confirm(Long showId, CurrentUser customer, List<BookedSeat> bookedSeats) {
        // Microseconds: what timestamptz stores, so this response matches GET /bookings/{id}
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Long id = bookingWriteRepository.insert(showId, customer.id(), customer.name(), BookingStatus.CONFIRMED,
                createdAt, bookedSeats.stream().map(BookedSeat::showSeatId).toList(),
                bookedSeats.stream().map(BookedSeat::label).toList());

        List<BookingResponse.Seat> seats = bookedSeats.stream()
                .map(seat -> new BookingResponse.Seat(seat.showSeatId(), seat.label()))
                .toList();
        return new BookingResponse(id, showId, customer.name(), BookingStatus.CONFIRMED, createdAt, seats);
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(Long id, CurrentUser customer) {
        Booking booking = bookingRepository.findByIdAndCustomerId(id, customer.id())
                .orElseThrow(() -> new NotFoundException("Booking", id));
        return BookingResponse.from(booking, bookingSeatRepository.findByBookingIdOrderByIdAsc(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> findMine(CurrentUser customer, int page, int size) {
        Page<Booking> bookings = bookingRepository.findByCustomerId(customer.id(),
                PageRequest.of(page, size, NEWEST_FIRST));

        // One query for the whole page's seats, not one per booking
        List<Long> ids = bookings.map(Booking::getId).toList();
        Map<Long, List<BookingSeat>> seatsByBooking = ids.isEmpty() ? Map.of()
                : bookingSeatRepository.findByBookingIdInOrderByIdAsc(ids).stream()
                        .collect(Collectors.groupingBy(seat -> seat.getBooking().getId()));

        List<BookingResponse> items = bookings.stream()
                .map(booking -> BookingResponse.from(booking,
                        seatsByBooking.getOrDefault(booking.getId(), List.of())))
                .toList();
        return PageResponse.of(items, page, size, bookings.getTotalElements());
    }
}
