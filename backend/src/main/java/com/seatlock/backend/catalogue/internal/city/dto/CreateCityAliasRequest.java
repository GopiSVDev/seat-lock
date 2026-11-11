package com.seatlock.backend.catalogue.internal.city.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCityAliasRequest(@NotBlank @Size(max = 100) String alias) {
}
