package com.seatlock.payment.web.dto;

import com.seatlock.payment.domain.RefundReason;
import jakarta.validation.constraints.NotNull;

public record RefundRequest(@NotNull RefundReason reason) {
}
