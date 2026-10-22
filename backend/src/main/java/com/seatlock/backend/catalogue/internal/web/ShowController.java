package com.seatlock.backend.catalogue.internal.web;

import com.seatlock.backend.catalogue.internal.service.ShowService;
import com.seatlock.backend.catalogue.internal.web.dto.CreateShowRequest;
import com.seatlock.backend.catalogue.internal.web.dto.ShowResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    @PostMapping
    public ResponseEntity<ShowResponse> create(@Valid @RequestBody CreateShowRequest request) {
        ShowResponse response = showService.create(request);
        return ResponseEntity.created(URI.create("/shows/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ShowResponse getById(@PathVariable Long id) {
        return showService.getById(id);
    }
}
