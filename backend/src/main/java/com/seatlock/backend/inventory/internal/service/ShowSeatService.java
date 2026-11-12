package com.seatlock.backend.inventory.internal.service;

import com.seatlock.backend.catalogue.ShowScheduled;
import com.seatlock.backend.inventory.BookedSeat;
import com.seatlock.backend.inventory.ConfirmedHold;
import com.seatlock.backend.inventory.HeldSeats;
import com.seatlock.backend.inventory.InventoryApi;
import com.seatlock.backend.inventory.internal.domain.SeatType;
import com.seatlock.backend.inventory.internal.domain.ShowSeat;
import com.seatlock.backend.inventory.internal.repository.ClaimedHold;
import com.seatlock.backend.inventory.internal.repository.HoldRecord;
import com.seatlock.backend.inventory.internal.repository.HoldStore;
import com.seatlock.backend.inventory.internal.repository.LayoutSeat;
import com.seatlock.backend.inventory.internal.repository.SeatAvailability;
import com.seatlock.backend.inventory.internal.repository.SeatMapRepository;
import com.seatlock.backend.inventory.internal.repository.ShowSeatRepository;
import com.seatlock.backend.inventory.internal.web.dto.SeatAvailabilityResponse;
import com.seatlock.backend.inventory.internal.web.dto.SeatLayoutResponse;
import com.seatlock.backend.shared.exception.ConflictException;
import com.seatlock.backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShowSeatService implements InventoryApi {

    // Longer than any booking transaction should take
    private static final Duration CONFIRM_GRACE = Duration.ofSeconds(60);

    private final ShowSeatRepository showSeatRepository;
    private final SeatMapRepository seatMapRepository;
    private final HoldStore holdStore;

    @EventListener
    public void on(ShowScheduled event) {
        List<ShowSeat> showSeats = event.seats().stream().map(seat -> new ShowSeat(event.showId(), seat.seatId(),
                seat.rowLabel(), seat.seatNumber(), SeatType.valueOf(seat.seatType()), seat.seatRank(), seat.price()))
                .toList();

        showSeatRepository.saveAll(showSeats);
    }

    // No @Transactional: one SELECT each. Add readOnly back if one ever runs more queries.
    public SeatLayoutResponse getLayout(Long showId) {
        List<LayoutSeat> seats = seatMapRepository.findLayout(showId);

        if (seats.isEmpty()) {
            throw new NotFoundException("Show", showId);
        }

        return SeatLayoutResponse.from(showId, seats);
    }

    public SeatAvailabilityResponse getAvailability(Long showId) {
        SeatAvailability availability = seatMapRepository.findAvailability(showId);

        if (availability.totalSeats() == 0) {
            throw new NotFoundException("Show", showId);
        }

        // Held seats are AVAILABLE in Postgres; a seat being confirmed can be in both
        Set<Long> unavailable = new TreeSet<>(availability.unavailable());
        unavailable.addAll(holdStore.heldSeats(showId));
        return SeatAvailabilityResponse.from(showId, availability.totalSeats(), List.copyOf(unavailable));
    }

    @Override
    public HeldSeats startPayment(String holdId, String customerId, Duration window) {
        HoldRecord hold = holdStore.extend(holdId, customerId, window)
                .orElseThrow(() -> new NotFoundException("Hold", holdId));
        log.info("Hold {} kept until {} for payment", holdId, hold.expiresAt());

        List<BookedSeat> seats = showSeatRepository.findStates(hold.seatIds()).stream()
                .map(seat -> new BookedSeat(seat.showSeatId(), seat.label(), seat.price())).toList();
        return new HeldSeats(hold.showId(), seats, hold.expiresAt());
    }

    @Override
    @Transactional
    public ConfirmedHold confirmHold(String holdId, String customerId) {
        ClaimedHold hold = holdStore.claim(holdId, customerId, CONFIRM_GRACE)
                .orElseThrow(() -> new NotFoundException("Hold", holdId));

        // The guard still decides: fewer rows only if Redis lost the hold or a double-tapped confirm came second
        List<BookedSeat> booked = showSeatRepository.bookIfAvailable(hold.showId(), hold.seatIds());
        if (booked.size() != hold.seatIds().size()) {
            log.warn("Hold {}: only {} of seats {} could be booked; rolling back", holdId, booked.size(),
                    hold.seatIds());
            throw new ConflictException("One or more seats are already booked");
        }
        log.info("Hold {} confirmed: seats {} of show {} booked", holdId, hold.seatIds(), hold.showId());

        // After commit, never before: SeatHoldService relies on BOOKED being visible
        // once the keys are gone
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    holdStore.release(holdId, customerId);
                } catch (RuntimeException e) {
                    // The booking is committed; the keys expire after the grace period anyway
                    log.warn("Could not delete confirmed hold {}", holdId, e);
                }
            }
        });
        return new ConfirmedHold(hold.showId(), booked);
    }

    @Override
    @Transactional
    public void releaseSeats(Long showId, List<Long> showSeatIds) {
        Set<Long> seats = new HashSet<>(showSeatIds);
        int released = showSeatRepository.release(showId, seats);

        // Fewer means booking and inventory disagree: roll back rather than half-cancel
        if (released != seats.size()) {
            throw new IllegalStateException(
                    "Expected to release %d seats of show %d, released %d".formatted(seats.size(), showId, released));
        }
        log.info("Released seats {} of show {}", seats, showId);
    }
}
