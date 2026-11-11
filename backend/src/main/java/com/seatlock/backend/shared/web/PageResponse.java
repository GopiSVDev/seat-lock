package com.seatlock.backend.shared.web;

import java.util.List;

// Own shape: Spring Data's Page JSON isn't a stable contract
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, long totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        return new PageResponse<>(items, page, size, totalItems, (totalItems + size - 1) / size);
    }
}
