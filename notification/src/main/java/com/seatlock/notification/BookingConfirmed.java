package com.seatlock.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Our copy of the backend's event (topic booking.confirmed); services share no code, only this JSON shape
public record BookingConfirmed(UUID eventId, Long bookingId, String customerId, String customerName, String movieTitle,
        String theatreName, String screenName, Instant showStartTime, List<String> seats, long totalAmount) {
}
