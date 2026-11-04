package com.seatlock.backend.inventory.internal.service;

import com.seatlock.backend.catalogue.ShowScheduled;
import com.seatlock.backend.inventory.BookedSeat;
import com.seatlock.backend.inventory.InventoryApi;
import com.seatlock.backend.inventory.internal.domain.SeatType;
import com.seatlock.backend.inventory.internal.domain.ShowSeat;
import com.seatlock.backend.inventory.internal.repository.CandidateSeat;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShowSeatService implements InventoryApi {

    // Bigger window: fewer round trips, but concurrent buyers skip more seats.
    private static final int CANDIDATE_WINDOW = 50;

    private final ShowSeatRepository showSeatRepository;
    private final SeatMapRepository seatMapRepository;

    @EventListener
    public void on(ShowScheduled event) {
        List<ShowSeat> showSeats = event.seats().stream()
                .map(seat -> new ShowSeat(event.showId(), seat.seatId(), seat.rowLabel(), seat.seatNumber(),
                        SeatType.valueOf(seat.seatType()), seat.seatRank()))
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

        // Failure only: unknown (404) vs booked (409); the exception rolls back the partial update
        List<ShowSeat> showSeats = showSeatRepository.findAllById(requested);
        boolean allValid = showSeats.size() == requested.size() && showSeats.stream().allMatch(seat -> seat.getShowId().equals(showId));

        if (!allValid) throw new NotFoundException("Seats for show", showId);

        throw new ConflictException("One or more seats are already booked");
    }

    // Earlier windows stay locked, so a block may span two; unbooked seats are released at commit.
    @Override
    @Transactional
    public List<BookedSeat> bookBestAvailable(Long showId, int quantity, String seatType) {
        String type = SeatType.valueOf(seatType).name();
        List<CandidateSeat> candidates = new ArrayList<>();
        int afterRank = 0;

        while (true) {
            List<CandidateSeat> window = showSeatRepository.lockAvailable(showId, type, afterRank, CANDIDATE_WINDOW);
            candidates.addAll(window);

            List<Long> block = bestBlock(candidates, quantity);
            if (block != null) {
                // Locked by us and AVAILABLE: the guarded update books all of them
                return showSeatRepository.bookIfAvailable(showId, block);
            }

            if (window.size() < CANDIDATE_WINDOW) break;
            afterRank = window.getLast().seatRank();
        }

        if (!showSeatRepository.existsByShowId(showId)) throw new NotFoundException("Show", showId);

        throw new ConflictException("No " + quantity + " adjacent " + type + " seats available");
    }

    // Lowest rank sum of `quantity` consecutive seats in one row; null if none
    private static List<Long> bestBlock(List<CandidateSeat> candidates, int quantity) {
        Map<String, List<CandidateSeat>> rows = candidates.stream()
                .collect(Collectors.groupingBy(CandidateSeat::rowLabel, Collectors.toCollection(ArrayList::new)));

        List<CandidateSeat> best = null;
        long bestScore = Long.MAX_VALUE;

        for (List<CandidateSeat> row : rows.values()) {
            row.sort(Comparator.comparingInt(CandidateSeat::seatNumber));
            int runStart = 0;

            for (int i = 0; i < row.size(); i++) {
                if (i > 0 && row.get(i).seatNumber() != row.get(i - 1).seatNumber() + 1) runStart = i;
                if (i - runStart + 1 < quantity) continue;

                List<CandidateSeat> block = row.subList(i - quantity + 1, i + 1);
                long score = block.stream().mapToLong(CandidateSeat::seatRank).sum();
                if (score < bestScore) {
                    best = block;
                    bestScore = score;
                }
            }
        }

        return best == null ? null : best.stream().map(CandidateSeat::id).toList();
    }
}
