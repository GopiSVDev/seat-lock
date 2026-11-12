package com.seatlock.backend.booking.internal.domain;

public enum BookingStatus {
    // Seats held, waiting for payment (until paymentExpiresAt)
    PENDING_PAYMENT, CONFIRMED,
    // Not paid in time
    EXPIRED,
    // Paid, but the seats were lost before confirming; the payment is refunded
    FAILED, CANCELLED
}
