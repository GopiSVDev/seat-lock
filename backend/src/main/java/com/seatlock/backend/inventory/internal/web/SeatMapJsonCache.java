package com.seatlock.backend.inventory.internal.web;

import com.seatlock.backend.inventory.internal.service.ShowSeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// Caches the serialized JSON, so cache hits skip Jackson and the heap holds one byte[] per show.
// Cache settings live in SeatMapCacheConfig.
@Component
@RequiredArgsConstructor
public class SeatMapJsonCache {

    private final ShowSeatService showSeatService;
    private final JsonMapper jsonMapper;

    @Cacheable(cacheNames = SeatMapCacheConfig.LAYOUTS, sync = true)
    public byte[] layoutJson(Long showId) {
        return jsonMapper.writeValueAsBytes(showSeatService.getLayout(showId));
    }

    // Can be up to 1 s stale; booking re-checks every seat.
    @Cacheable(cacheNames = SeatMapCacheConfig.AVAILABILITY, sync = true)
    public byte[] availabilityJson(Long showId) {
        return jsonMapper.writeValueAsBytes(showSeatService.getAvailability(showId));
    }
}
