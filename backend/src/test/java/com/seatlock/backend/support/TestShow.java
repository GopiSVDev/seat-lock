package com.seatlock.backend.support;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// A scheduled show in its own city and theatre: rows A, B (10 regular each) and C (8 recliners),
// priced IntegrationTest.REGULAR_PRICE and RECLINER_PRICE
public record TestShow(long showId, long movieId, long theatreId, long screenId, long cityId, Instant startTime,
        Map<String, List<Long>> seatIdsByRow) {

    public List<Long> seats(String row, int count) {
        return seatIdsByRow.get(row).subList(0, count);
    }
}
