package com.seatlock.backend.inventory.internal.service;

import com.seatlock.backend.inventory.internal.domain.SeatType;
import com.seatlock.backend.inventory.internal.repository.CandidateSeat;
import com.seatlock.backend.inventory.internal.repository.HoldRecord;
import com.seatlock.backend.inventory.internal.repository.HoldStore;
import com.seatlock.backend.inventory.internal.repository.SeatState;
import com.seatlock.backend.inventory.internal.repository.ShowSeatRepository;
import com.seatlock.backend.inventory.internal.web.dto.HoldResponse;
import com.seatlock.backend.shared.exception.ConflictException;
import com.seatlock.backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatHoldService {

    private static final Duration HOLD_TTL = Duration.ofMinutes(5);

    // Another buyer can hold the picked block between our read and our hold; then pick again
    private static final int BEST_AVAILABLE_ATTEMPTS = 3;

    private final HoldStore holdStore;
    private final ShowSeatRepository showSeatRepository;

    public HoldResponse hold(Long showId, List<Long> showSeatIds, String customerId) {
        List<Long> seats = showSeatIds.stream().distinct().sorted().toList();
        String holdId = UUID.randomUUID().toString();

        Instant expiresAt = holdStore.place(holdId, showId, customerId, seats, HOLD_TTL)
                .orElseThrow(() -> new ConflictException("One or more seats are already held or booked"));

        // Checked after the hold, not before: confirm deletes a hold's keys only after BOOKED commits,
        // so if our hold got the keys, any booking of these seats is already visible here
        List<SeatState> states = showSeatRepository.findStates(seats);
        boolean allOfShow = states.size() == seats.size()
                && states.stream().allMatch(seat -> seat.showId().equals(showId));

        if (!allOfShow) {
            holdStore.release(holdId, customerId);
            throw new NotFoundException("Seats for show", showId);
        }

        if (states.stream().anyMatch(seat -> !"AVAILABLE".equals(seat.status()))) {
            holdStore.release(holdId, customerId);
            throw new ConflictException("One or more seats are already held or booked");
        }

        return HoldResponse.of(holdId, showId, expiresAt, states);
    }

    public HoldResponse holdBestAvailable(Long showId, int quantity, String seatType, String customerId) {
        String type = SeatType.valueOf(seatType).name();

        for (int attempt = 0; attempt < BEST_AVAILABLE_ATTEMPTS; attempt++) {
            Set<Long> held = holdStore.heldSeats(showId);
            List<CandidateSeat> candidates = showSeatRepository.findAvailable(showId, type).stream()
                    .filter(seat -> !held.contains(seat.id())).toList();

            List<Long> block = bestBlock(candidates, quantity);
            if (block == null)
                break;

            try {
                return hold(showId, block, customerId);
            } catch (ConflictException lostRace) {
                // someone else got a seat of the block first
            }
        }

        if (!showSeatRepository.existsByShowId(showId))
            throw new NotFoundException("Show", showId);

        throw new ConflictException("No " + quantity + " adjacent " + type + " seats available");
    }

    // Someone else's hold is not found either, so ids can't be probed (BOLA)
    public HoldResponse get(String holdId, String customerId) {
        HoldRecord hold = holdStore.find(holdId).filter(found -> found.customerId().equals(customerId))
                .orElseThrow(() -> new NotFoundException("Hold", holdId));
        return HoldResponse.of(hold.id(), hold.showId(), hold.expiresAt(),
                showSeatRepository.findStates(hold.seatIds()));
    }

    public void release(String holdId, String customerId) {
        if (!holdStore.release(holdId, customerId)) {
            throw new NotFoundException("Hold", holdId);
        }
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
                if (i > 0 && row.get(i).seatNumber() != row.get(i - 1).seatNumber() + 1)
                    runStart = i;
                if (i - runStart + 1 < quantity)
                    continue;

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
