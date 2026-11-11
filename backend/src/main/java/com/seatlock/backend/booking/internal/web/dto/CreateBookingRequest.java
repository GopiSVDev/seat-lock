package com.seatlock.backend.booking.internal.web.dto;

import jakarta.validation.constraints.NotBlank;

// Seats are held first (POST /holds); booking confirms the hold
public record CreateBookingRequest(@NotBlank String holdId) {
}
