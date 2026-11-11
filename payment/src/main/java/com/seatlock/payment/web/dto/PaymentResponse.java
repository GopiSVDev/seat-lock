package com.seatlock.payment.web.dto;

import com.seatlock.payment.domain.Payment;
import com.seatlock.payment.domain.PaymentStatus;

// keyId is public (Checkout needs it in the browser); the key secret never leaves this service
public record PaymentResponse(Long id, Long bookingId, long amount, String currency, PaymentStatus status,
        String razorpayOrderId, String razorpayPaymentId, String keyId) {

    public static PaymentResponse from(Payment payment, String keyId) {
        return new PaymentResponse(payment.getId(), payment.getBookingId(), payment.getAmount(), payment.getCurrency(),
                payment.getStatus(), payment.getRazorpayOrderId(), payment.getRazorpayPaymentId(), keyId);
    }
}
