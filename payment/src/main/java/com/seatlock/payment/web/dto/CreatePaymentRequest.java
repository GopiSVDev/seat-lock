package com.seatlock.payment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Amount in paise
public record CreatePaymentRequest(@NotNull @Positive Long bookingId, @NotBlank @Size(max = 255) String customerId,
        @NotNull @Positive Long amount) {
}
