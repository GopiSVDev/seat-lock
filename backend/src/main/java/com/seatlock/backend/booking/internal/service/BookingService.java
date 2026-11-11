package com.seatlock.backend.booking.internal.service;

import com.seatlock.backend.booking.internal.client.PaymentClient;
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
import com.seatlock.backend.booking.internal.web.dto.PaymentConfirmationRequest;
import com.seatlock.backend.catalogue.CatalogueApi;
import com.seatlock.backend.inventory.BookedSeat;
import com.seatlock.backend.inventory.HeldSeats;
import com.seatlock.backend.inventory.InventoryApi;
import com.seatlock.backend.shared.exception.ConflictException;
import com.seatlock.backend.shared.exception.DownstreamException;
import com.seatlock.backend.shared.exception.NotFoundException;
import com.seatlock.backend.shared.security.CurrentUser;
import com.seatlock.backend.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Checkout and payment never run inside a transaction: no DB connection may wait on the payment service.
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    // How long a buyer has to pay once checkout starts; the hold is kept alive that
    // long
    static final Duration PAYMENT_WINDOW = Duration.ofMinutes(10);

    // Unpaid bookings (pending, expired) are not orders yet, so "my bookings" leaves them out
    private static final Set<BookingStatus> LISTED = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED,
            BookingStatus.FAILED);

    // Same order as ix_booking_customer_show_start: forwards for upcoming, backwards for past
    private static final Sort SOONEST_FIRST = Sort.by(Sort.Order.asc("show.startTime"), Sort.Order.asc("id"));
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("show.startTime"), Sort.Order.desc("id"));

    private final CatalogueApi catalogueApi;
    private final InventoryApi inventoryApi;
    private final PaymentClient paymentClient;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final BookingWriteRepository bookingWriteRepository;
    private final TransactionTemplate transactionTemplate;

    public record Checkout(BookingResponse booking, boolean isNew) {
    }

    // One booking per hold: a repeat (double tap, retry after a payment service error) finds the same
    // booking and does not extend the hold again
    public Checkout checkout(CreateBookingRequest request, CurrentUser customer) {
        Optional<Booking> existing = bookingRepository.findByHoldIdAndCustomerId(request.holdId(), customer.id());
        Booking booking = existing.orElseGet(() -> startBooking(request.holdId(), customer));
        BookingResponse response = response(booking);

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return new Checkout(response, existing.isEmpty());
        }

        // Idempotent per booking: the same Razorpay order every time
        PaymentClient.Payment payment = paymentClient.create(booking.getId(), customer.id(), booking.getTotalAmount());
        bookingWriteRepository.setPayment(booking.getId(), payment.id());
        return new Checkout(response.withCheckout(new BookingResponse.Checkout(payment.razorpayOrderId(),
                payment.keyId(), payment.amount(), payment.currency())), existing.isEmpty());
    }

    // The browser, after Razorpay Checkout succeeded
    public BookingResponse pay(Long id, PaymentConfirmationRequest request, CurrentUser customer) {
        Booking booking = findOwn(id, customer);
        if (booking.getPaymentId() == null) {
            throw new ConflictException("Payment has not been started");
        }
        paymentClient.verify(booking.getPaymentId(), request.razorpayOrderId(), request.razorpayPaymentId(),
                request.razorpaySignature());
        return response(settlePaid(booking));
    }

    // The payment service, after Razorpay's webhook; also covers a buyer who closed
    // the browser after paying
    public void paid(Long id, Long paymentId) {
        Booking booking = bookingRepository.findById(id).orElseThrow(() -> new NotFoundException("Booking", id));
        if (booking.getPaymentId() == null) {
            // We crashed between creating the payment and saving its id
            bookingWriteRepository.setPayment(id, paymentId);
        } else if (!booking.getPaymentId().equals(paymentId)) {
            throw new ConflictException("Booking has another payment");
        }
        settlePaid(bookingRepository.findById(id).orElseThrow());
    }

    // Idempotent: cancelling a cancelled booking returns it unchanged
    @Transactional
    public BookingResponse cancel(Long id, CurrentUser customer) {
        // Microseconds: what timestamptz stores, so this response matches GET /bookings/{id}
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        boolean cancelledNow = bookingWriteRepository.cancel(id, customer.id(), now);

        // Read after the update, so this sees it (or the cancel that beat us to it)
        Booking booking = findOwn(id, customer);
        List<BookingSeat> seats = bookingSeatRepository.findByBookingIdOrderByIdAsc(id);

        if (cancelledNow) {
            inventoryApi.releaseSeats(booking.getShowId(), seats.stream().map(BookingSeat::getShowSeatId).toList());
            bookingWriteRepository.cancelSeats(id);
            // Older bookings were made before payments existed
            if (booking.getPaymentId() != null) {
                afterCommit(() -> refund(booking, "BOOKING_CANCELLED"));
            }
        } else if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new ConflictException("Show has already started");
        } else if (booking.getStatus() != BookingStatus.CANCELLED) {
            throw new ConflictException("Only confirmed bookings can be cancelled");
        }

        return BookingResponse.from(booking, seats);
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(Long id, CurrentUser customer) {
        return response(findOwn(id, customer));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> findMine(CurrentUser customer, BookingPeriod period, int page, int size) {
        Instant now = Instant.now();
        Page<Booking> bookings = switch (period) {
            case UPCOMING ->
                bookingRepository.findUpcoming(customer.id(), now, LISTED, PageRequest.of(page, size, SOONEST_FIRST));
            case PAST ->
                bookingRepository.findPast(customer.id(), now, LISTED, PageRequest.of(page, size, LATEST_FIRST));
        };

        // One query for the whole page's seats, not one per booking
        List<Long> ids = bookings.map(Booking::getId).toList();
        Map<Long, List<BookingSeat>> seatsByBooking = ids.isEmpty()
                ? Map.of()
                : bookingSeatRepository.findByBookingIdInOrderByIdAsc(ids).stream()
                        .collect(Collectors.groupingBy(seat -> seat.getBooking().getId()));

        List<BookingResponse> items = bookings.stream()
                .map(booking -> BookingResponse.from(booking, seatsByBooking.getOrDefault(booking.getId(), List.of())))
                .toList();

        return PageResponse.of(items, page, size, bookings.getTotalElements());
    }

    private Booking startBooking(String holdId, CurrentUser customer) {
        HeldSeats hold = inventoryApi.startPayment(holdId, customer.id(), PAYMENT_WINDOW);
        ShowSnapshot show = new ShowSnapshot(catalogueApi.findShowSummary(hold.showId()));
        long totalAmount = hold.seats().stream().mapToLong(BookedSeat::price).sum();

        try {
            Long id = bookingWriteRepository.insertPending(hold.showId(), show, customer.id(), customer.name(), holdId,
                    Instant.now().truncatedTo(ChronoUnit.MICROS), hold.expiresAt(), totalAmount, hold.seats());
            return bookingRepository.findById(id).orElseThrow();
        } catch (DataIntegrityViolationException e) {
            // A double-tapped checkout inserted it first (uk_booking_hold)
            return bookingRepository.findByHoldIdAndCustomerId(holdId, customer.id()).orElseThrow(() -> e);
        }
    }

    // Browser and webhook both end here; whichever comes second finds the booking already settled
    private Booking settlePaid(Booking booking) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (bookingWriteRepository.settle(booking.getId(), BookingStatus.CONFIRMED)) {
                    inventoryApi.confirmHold(booking.getHoldId(), booking.getCustomerId());
                }
            });
        } catch (NotFoundException | ConflictException seatsLost) {
            // Paid, but the hold expired or was replaced before we could book the seats
            Boolean failedNow = transactionTemplate
                    .execute(status -> bookingWriteRepository.settle(booking.getId(), BookingStatus.FAILED));
            if (Boolean.TRUE.equals(failedNow)) {
                refund(booking, "BOOKING_FAILED");
            }
        }
        return bookingRepository.findById(booking.getId()).orElseThrow();
    }

    private void refund(Booking booking, String reason) {
        try {
            paymentClient.refund(booking.getPaymentId(), reason);
        } catch (DownstreamException e) {
            // The buyer's money is held with nothing to show for it; reconciliation (steps
            // 5/6) must retry this
            log.error("Refund of booking {} ({}) failed and needs a retry", booking.getId(), reason, e);
        }
    }

    private Booking findOwn(Long id, CurrentUser customer) {
        return bookingRepository.findByIdAndCustomerId(id, customer.id())
                .orElseThrow(() -> new NotFoundException("Booking", id));
    }

    private BookingResponse response(Booking booking) {
        return BookingResponse.from(booking, bookingSeatRepository.findByBookingIdOrderByIdAsc(booking.getId()));
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
