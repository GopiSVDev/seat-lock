package com.seatlock.backend.shared.web;

import java.util.List;

// One page of a list endpoint. Our own shape instead of Spring Data's Page, whose JSON is not a stable contract.
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        long totalPages
) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        return new PageResponse<>(items, page, size, totalItems, (totalItems + size - 1) / size);
    }
}
