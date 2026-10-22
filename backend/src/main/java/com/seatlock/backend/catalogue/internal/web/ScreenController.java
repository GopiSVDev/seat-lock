package com.seatlock.backend.catalogue.internal.web;

import com.seatlock.backend.catalogue.internal.service.ScreenService;
import com.seatlock.backend.catalogue.internal.web.dto.CreateScreenRequest;
import com.seatlock.backend.catalogue.internal.web.dto.ScreenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class ScreenController {

    private final ScreenService screenService;

    @PostMapping("/theatres/{theatreId}/screens")
    public ResponseEntity<ScreenResponse> create(@PathVariable Long theatreId, @Valid @RequestBody CreateScreenRequest request) {
        ScreenResponse response = screenService.create(theatreId, request);
        return ResponseEntity.created(URI.create("/screens/" + response.id())).body(response);
    }

    @GetMapping("/screens/{id}")
    public ScreenResponse getById(@PathVariable Long id) {
        return screenService.getById(id);
    }
}
