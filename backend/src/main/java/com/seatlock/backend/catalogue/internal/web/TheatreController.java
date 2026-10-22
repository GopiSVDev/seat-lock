package com.seatlock.backend.catalogue.internal.web;

import com.seatlock.backend.catalogue.internal.service.TheatreService;
import com.seatlock.backend.catalogue.internal.web.dto.CreateTheatreRequest;
import com.seatlock.backend.catalogue.internal.web.dto.TheatreResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/theatres")
@RequiredArgsConstructor
public class TheatreController {

    private final TheatreService theatreService;

    @PostMapping
    public ResponseEntity<TheatreResponse> create(@Valid @RequestBody CreateTheatreRequest request) {
        TheatreResponse response = theatreService.create(request);
        return ResponseEntity.created(URI.create("/theatres/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public TheatreResponse getById(@PathVariable Long id) {
        return theatreService.getById(id);
    }
}
