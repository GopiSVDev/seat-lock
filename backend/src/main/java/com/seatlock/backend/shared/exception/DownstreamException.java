package com.seatlock.backend.shared.exception;

// Another service (e.g. payment) failed or was unreachable; answered with 502
public class DownstreamException extends RuntimeException {

    public DownstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
