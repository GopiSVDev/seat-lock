package com.seatlock.backend.shared.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

// India only: a "day" is a day in IST. Times stay UTC.
public final class IndiaTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private IndiaTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    // 00:00 in India on that date, e.g. 2026-11-05 → 2026-11-04T18:30:00Z
    public static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }
}
