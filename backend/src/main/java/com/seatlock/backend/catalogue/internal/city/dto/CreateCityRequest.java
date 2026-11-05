package com.seatlock.backend.catalogue.internal.city.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCityRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String state,
        @Positive Integer popularRank
) {
}
