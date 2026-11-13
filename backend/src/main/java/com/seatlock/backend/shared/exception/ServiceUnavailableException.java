package com.seatlock.backend.shared.exception;

import lombok.Getter;

import java.time.Duration;

// A dependency is overloaded or down and we refused without waiting for it
@Getter
public class ServiceUnavailableException extends RuntimeException {

    private final long retryAfterSeconds;

    public ServiceUnavailableException(String what, Duration wait) {
        this(what, Math.max(1, (wait.toMillis() + 999) / 1000));
    }

    private ServiceUnavailableException(String what, long retryAfterSeconds) {
        super(what + ", try again in " + retryAfterSeconds + (retryAfterSeconds == 1 ? " second" : " seconds"));
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
