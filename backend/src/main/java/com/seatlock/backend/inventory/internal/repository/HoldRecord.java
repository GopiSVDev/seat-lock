package com.seatlock.backend.inventory.internal.repository;

import java.time.Instant;
import java.util.List;

public record HoldRecord(String id, String customerId, Long showId, List<Long> seatIds, Instant expiresAt) {
}
