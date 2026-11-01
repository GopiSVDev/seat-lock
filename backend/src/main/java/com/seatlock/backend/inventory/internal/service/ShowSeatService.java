package com.seatlock.backend.inventory.internal.service;

import com.seatlock.backend.catalogue.ShowScheduled;
import com.seatlock.backend.inventory.BookedSeat;
import com.seatlock.backend.inventory.InventoryApi;
import com.seatlock.backend.inventory.internal.domain.SeatType;
import com.seatlock.backend.inventory.internal.domain.ShowSeat;
import com.seatlock.backend.inventory.internal.repository.LayoutSeat;
import com.seatlock.backend.inventory.internal.repository.SeatAvailability;
import com.seatlock.backend.inventory.internal.repository.SeatMapRepository;
import com.seatlock.backend.inventory.internal.repository.ShowSeatRepository;
import com.seatlock.backend.inventory.internal.web.dto.SeatAvailabilityResponse;
import com.seatlock.backend.inventory.internal.web.dto.SeatLayoutResponse;
import com.seatlock.backend.shared.exception.ConflictException;
import com.seatlock.backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShowSeatService implements InventoryApi {

    private final ShowSeatRepository showSeatRepository;
    private final SeatMapRepository seatMapRepository;

    @EventListener
    public void on(ShowScheduled event) {
        List<ShowSeat> showSeats = event.seats().stream()
                .map(seat -> new ShowSeat(event.showId(), seat.seatId(), seat.rowLabel(), seat.seatNumber(),
                        SeatType.valueOf(seat.seatType())))
                .toList();

        showSeatRepository.saveAll(showSeats);
    }

    // No transaction on the seat map reads: a single SELECT runs in its own implicit one (saves BEGIN/COMMIT
    // on a hot path). Add @Transactional(readOnly = true) back if one of them ever runs more than one query.
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

        return SeatAvailabilityResponse.from(showId, availability);
    }

    @Override
    @Transactional
    public List<BookedSeat> bookSeats(Long showId, List<Long> showSeatIds) {
        Set<Long> requested = new HashSet<>(showSeatIds);
        List<BookedSeat> booked = showSeatRepository.bookIfAvailable(showId, requested);

        if (booked.size() == requested.size()) {
            return booked;
        }

        // Failure only: tell unknown seats from booked ones (the exception rolls back the partial update)
        List<ShowSeat> showSeats = showSeatRepository.findAllById(requested);
        boolean allValid = showSeats.size() == requested.size() && showSeats.stream().allMatch(seat -> seat.getShowId().equals(showId));

        if (!allValid) throw new NotFoundException("Seats for show", showId);

        throw new ConflictException("One or more seats are already booked");
    }
}
