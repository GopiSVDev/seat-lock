package com.seatlock.backend.inventory.internal.web;

import com.seatlock.backend.inventory.internal.service.ShowSeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// Caches the serialized JSON, so cache hits skip Jackson and the heap holds one byte[] per show.
// Cached 1 s (spring.cache.caffeine.spec), so the map can be that stale; booking re-checks every seat.
@Component
@RequiredArgsConstructor
public class SeatMapJsonCache {

    private final ShowSeatService showSeatService;
    private final JsonMapper jsonMapper;

    @Cacheable(cacheNames = "seatMaps", sync = true)
    public byte[] seatMapJson(Long showId) {
        return jsonMapper.writeValueAsBytes(showSeatService.getSeatMap(showId));
    }
}
