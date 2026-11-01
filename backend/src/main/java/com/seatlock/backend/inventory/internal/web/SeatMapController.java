package com.seatlock.backend.inventory.internal.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

// The seat map is split: a layout that never changes (browsers and CDNs cache it) and a small
// availability payload that clients fetch on every view.
@RestController
@RequiredArgsConstructor
public class SeatMapController {

    private static final CacheControl LAYOUT_CACHE_CONTROL = CacheControl.maxAge(Duration.ofDays(1)).cachePublic().immutable();

    private final SeatMapJsonCache seatMapJsonCache;

    @GetMapping(value = "/shows/{showId}/layout", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> getLayout(@PathVariable Long showId) {
        return ResponseEntity.ok().cacheControl(LAYOUT_CACHE_CONTROL).body(seatMapJsonCache.layoutJson(showId));
    }

    @GetMapping(value = "/shows/{showId}/availability", produces = MediaType.APPLICATION_JSON_VALUE)
    public byte[] getAvailability(@PathVariable Long showId) {
        return seatMapJsonCache.availabilityJson(showId);
    }
}
