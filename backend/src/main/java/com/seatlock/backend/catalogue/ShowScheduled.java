package com.seatlock.backend.catalogue;

import java.util.List;

public record ShowScheduled(Long showId, List<ScheduledSeat> seats) {

    // price in paise
    public record ScheduledSeat(Long seatId, String rowLabel, int seatNumber, String seatType, int seatRank,
                                long price) {
    }
}
