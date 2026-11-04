package com.seatlock.backend.shared.exception;

// A request that is well-formed field by field but asks for something that doesn't make sense together
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
