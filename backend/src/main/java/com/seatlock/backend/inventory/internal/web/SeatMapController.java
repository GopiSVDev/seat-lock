package com.seatlock.backend.inventory.internal.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SeatMapController {

    private final SeatMapJsonCache seatMapJsonCache;

    @GetMapping(value = "/shows/{showId}/seats", produces = MediaType.APPLICATION_JSON_VALUE)
    public byte[] getSeatMap(@PathVariable Long showId) {
        return seatMapJsonCache.seatMapJson(showId);
    }
}
