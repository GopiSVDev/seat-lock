package com.seatlock.backend.catalogue.internal.theatre.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTheatreRequest(@NotBlank String name, @NotNull Long cityId, @NotBlank String address) {
}
