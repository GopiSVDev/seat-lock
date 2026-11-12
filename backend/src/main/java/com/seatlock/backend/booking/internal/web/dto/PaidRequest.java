package com.seatlock.backend.booking.internal.web.dto;

import jakarta.validation.constraints.NotNull;

// From the payment service after Razorpay's payment.captured webhook
public record PaidRequest(@NotNull Long paymentId) {
}
