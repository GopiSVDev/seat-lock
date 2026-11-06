package com.seatlock.backend.inventory.internal.repository;

import java.util.List;

public record ClaimedHold(Long showId, List<Long> seatIds) {
}
