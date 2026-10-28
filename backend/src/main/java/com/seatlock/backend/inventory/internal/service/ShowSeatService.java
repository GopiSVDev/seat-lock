package com.seatlock.backend.inventory.internal.service;

import com.seatlock.backend.catalogue.ShowScheduled;
import com.seatlock.backend.inventory.BookedSeat;
import com.seatlock.backend.inventory.InventoryApi;
import com.seatlock.backend.inventory.internal.domain.SeatType;
import com.seatlock.backend.inventory.internal.domain.ShowSeat;
import com.seatlock.backend.inventory.internal.repository.SeatMapRepository;
import com.seatlock.backend.inventory.internal.repository.SeatMapSeat;
import com.seatlock.backend.inventory.internal.repository.ShowSeatRepository;
import com.seatlock.backend.inventory.internal.web.dto.SeatMapResponse;
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

    // No transaction: a single SELECT runs in its own implicit one (saves BEGIN/COMMIT on a hot path).
    // Add @Transactional(readOnly = true) back if this ever runs more than one query.
    public SeatMapResponse getSeatMap(Long showId) {
        List<SeatMapSeat> seats = seatMapRepository.findSeatMap(showId);

        if (seats.isEmpty()) {
            throw new NotFoundException("Show", showId);
        }

        return SeatMapResponse.from(showId, seats);
    }

    @Override
    @Transactional
    public List<BookedSeat> bookSeats(Long showId, List<Long> showSeatIds) {
        Set<Long> requested = new HashSet<>(showSeatIds);
        int booked = showSeatRepository.bookIfAvailable(showId, requested);
        List<ShowSeat> showSeats = showSeatRepository.findAllById(requested);

        if (booked != requested.size()) {
            boolean allValid = showSeats.size() == requested.size() && showSeats.stream().allMatch(seat -> seat.getShowId().equals(showId));

            if (!allValid) throw new NotFoundException("Seats for show", showId);

            throw new ConflictException("One or more seats are already booked");
        }

        return showSeats.stream().map(seat -> new BookedSeat(seat.getId(), seat.getLabel())).toList();
    }
}
