package com.seatlock.backend.shared.exception;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
