package com.seatlock.backend.booking.internal.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Published to Kafka as JSON; the notification service keeps its own copy of this shape
public record BookingConfirmed(UUID eventId, Long bookingId, String customerId, String customerName, String movieTitle,
        String theatreName, String screenName, Instant showStartTime, List<String> seats, long totalAmount) {

    public static final String TOPIC = "booking.confirmed";
}
