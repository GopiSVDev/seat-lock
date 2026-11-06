package com.seatlock.backend.booking.internal.service;

import com.seatlock.backend.booking.internal.domain.Booking;
import com.seatlock.backend.booking.internal.domain.BookingPeriod;
import com.seatlock.backend.booking.internal.domain.BookingSeat;
import com.seatlock.backend.booking.internal.domain.BookingStatus;
import com.seatlock.backend.booking.internal.domain.ShowSnapshot;
import com.seatlock.backend.booking.internal.repository.BookingRepository;
import com.seatlock.backend.booking.internal.repository.BookingSeatRepository;
import com.seatlock.backend.booking.internal.repository.BookingWriteRepository;
import com.seatlock.backend.booking.internal.web.dto.BookingResponse;
import com.seatlock.backend.booking.internal.web.dto.CreateBookingRequest;
import com.seatlock.backend.catalogue.CatalogueApi;
import com.seatlock.backend.inventory.BookedSeat;
import com.seatlock.backend.inventory.ConfirmedHold;
import com.seatlock.backend.inventory.InventoryApi;
import com.seatlock.backend.shared.exception.ConflictException;
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

    // Same order as ix_booking_customer_show_start: forwards for upcoming, backwards for past
    private static final Sort SOONEST_FIRST = Sort.by(Sort.Order.asc("show.startTime"), Sort.Order.asc("id"));
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("show.startTime"), Sort.Order.desc("id"));

    private final CatalogueApi catalogueApi;
    private final InventoryApi inventoryApi;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final BookingWriteRepository bookingWriteRepository;

    @Transactional
    public BookingResponse create(CreateBookingRequest request, CurrentUser customer) {
        ConfirmedHold hold = inventoryApi.confirmHold(request.holdId(), customer.id());
        Long showId = hold.showId();
        ShowSnapshot show = new ShowSnapshot(catalogueApi.findShowSummary(showId));
        List<BookedSeat> bookedSeats = hold.seats();

        // Microseconds: what timestamptz stores, so this response matches GET /bookings/{id}
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Long id = bookingWriteRepository.insert(showId, show, customer.id(), customer.name(),
                BookingStatus.CONFIRMED, createdAt, bookedSeats.stream().map(BookedSeat::showSeatId).toList(),
                bookedSeats.stream().map(BookedSeat::label).toList());

        List<BookingResponse.Seat> seats = bookedSeats.stream()
                .map(seat -> new BookingResponse.Seat(seat.showSeatId(), seat.label()))
                .toList();

        return new BookingResponse(id, customer.name(), BookingStatus.CONFIRMED, createdAt, null,
                BookingResponse.Show.from(showId, show), seats);
    }

    // Idempotent: cancelling a cancelled booking returns it unchanged
    @Transactional
    public BookingResponse cancel(Long id, CurrentUser customer) {
        // Microseconds, as for createdAt
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        boolean cancelledNow = bookingWriteRepository.cancel(id, customer.id(), now);

        // Read after the update, so this sees it (or the cancel that beat us to it)
        Booking booking = bookingRepository.findByIdAndCustomerId(id, customer.id())
                .orElseThrow(() -> new NotFoundException("Booking", id));
        List<BookingSeat> seats = bookingSeatRepository.findByBookingIdOrderByIdAsc(id);

        if (cancelledNow) {
            inventoryApi.releaseSeats(booking.getShowId(), seats.stream().map(BookingSeat::getShowSeatId).toList());
            bookingWriteRepository.cancelSeats(id);
        } else if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new ConflictException("Show has already started");
        }

        return BookingResponse.from(booking, seats);
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(Long id, CurrentUser customer) {
        Booking booking = bookingRepository.findByIdAndCustomerId(id, customer.id())
                .orElseThrow(() -> new NotFoundException("Booking", id));
        return BookingResponse.from(booking, bookingSeatRepository.findByBookingIdOrderByIdAsc(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> findMine(CurrentUser customer, BookingPeriod period, int page, int size) {
        Instant now = Instant.now();
        Page<Booking> bookings = switch (period) {
            case UPCOMING -> bookingRepository.findUpcoming(customer.id(), now,
                    PageRequest.of(page, size, SOONEST_FIRST));
            case PAST -> bookingRepository.findPast(customer.id(), now,
                    PageRequest.of(page, size, LATEST_FIRST));
        };

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
