package com.seatlock.payment.domain;

public enum RefundReason {
    // Paid, but the seats were lost (hold expired) before the booking could be confirmed
    BOOKING_FAILED, BOOKING_CANCELLED
}
