package com.seatlock.backend.catalogue.internal.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTheatreRequest(
        @NotBlank String name,
        @NotBlank String city,
        @NotBlank String address
) {
}
