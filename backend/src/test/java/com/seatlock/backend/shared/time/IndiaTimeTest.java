package com.seatlock.backend.shared.time;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class IndiaTimeTest {

    @Test
    void dayStartsAtMidnightIndiaTime() {
        assertThat(IndiaTime.startOf(LocalDate.of(2030, 1, 1))).isEqualTo(Instant.parse("2029-12-31T18:30:00Z"));
    }
}
