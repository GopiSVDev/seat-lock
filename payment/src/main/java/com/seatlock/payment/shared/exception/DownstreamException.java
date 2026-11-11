package com.seatlock.payment.shared.exception;

// Razorpay or the backend failed or was unreachable; answered with 502
public class DownstreamException extends RuntimeException {

    public DownstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
