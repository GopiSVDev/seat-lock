package com.seatlock.backend.booking.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The three values Razorpay Checkout hands the browser after a successful payment
public record PaymentConfirmationRequest(@NotBlank @Size(max = 64) String razorpayOrderId,
        @NotBlank @Size(max = 64) String razorpayPaymentId, @NotBlank @Size(max = 128) String razorpaySignature) {
}
