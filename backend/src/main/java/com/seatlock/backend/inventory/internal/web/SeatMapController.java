package com.seatlock.backend.inventory.internal.web;

import com.seatlock.backend.inventory.internal.service.ShowSeatService;
import com.seatlock.backend.inventory.internal.web.dto.SeatMapResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SeatMapController {

    private final ShowSeatService showSeatService;

    @GetMapping("/shows/{showId}/seats")
    public SeatMapResponse getSeatMap(@PathVariable Long showId) {
        return showSeatService.getSeatMap(showId);
    }
}
