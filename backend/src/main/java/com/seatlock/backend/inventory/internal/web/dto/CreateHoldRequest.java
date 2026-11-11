package com.seatlock.backend.inventory.internal.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateHoldRequest(@NotNull Long showId, @NotEmpty @Size(max = 10) List<@NotNull Long> showSeatIds) {
}
