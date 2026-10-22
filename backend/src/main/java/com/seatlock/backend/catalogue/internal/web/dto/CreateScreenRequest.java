package com.seatlock.backend.catalogue.internal.web.dto;

import com.seatlock.backend.catalogue.internal.domain.SeatType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateScreenRequest(
        @NotBlank String name,
        @NotEmpty List<@Valid Row> rows
) {

    // One row of the layout; expanded into seats 1..seatCount
    public record Row(
            @NotBlank String label,
            @Positive int seatCount,
            @NotNull SeatType seatType
    ) {
    }
}
