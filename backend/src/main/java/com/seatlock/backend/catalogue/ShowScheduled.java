package com.seatlock.backend.catalogue;

import java.util.List;

public record ShowScheduled(Long showId, List<ScheduledSeat> seats) {

    public record ScheduledSeat(Long seatId, String rowLabel, int seatNumber, String seatType) {
    }
}
