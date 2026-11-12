package com.seatlock.backend.booking.internal.service;

import com.seatlock.backend.booking.internal.repository.BookingWriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

// Marks unpaid bookings EXPIRED once their deadline passes, so the stored status is true for everyone
// reading it. Runs on every instance; the guarded update expires each booking once.
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryJob {

    private final BookingWriteRepository bookingWriteRepository;

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void expireOverdue() {
        int expired = bookingWriteRepository.expireOverdue(Instant.now());
        if (expired > 0) {
            log.info("Expired {} unpaid bookings", expired);
        }
    }
}
