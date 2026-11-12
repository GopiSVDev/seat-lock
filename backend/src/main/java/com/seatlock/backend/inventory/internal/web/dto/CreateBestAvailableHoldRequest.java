package com.seatlock.backend.inventory.internal.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreateBestAvailableHoldRequest(@NotNull Long showId, @Positive @Max(10) int quantity,
        @NotNull @Pattern(regexp = "REGULAR|PREMIUM|RECLINER") String seatType) {
}
