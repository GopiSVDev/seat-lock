package com.seatlock.backend.shared.exception;

import lombok.Getter;

import java.time.Duration;

@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String what, Duration wait) {
        this(what, Math.max(1, (wait.toMillis() + 999) / 1000));
    }

    private TooManyRequestsException(String what, long retryAfterSeconds) {
        super("Too many " + what + ", try again in " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
