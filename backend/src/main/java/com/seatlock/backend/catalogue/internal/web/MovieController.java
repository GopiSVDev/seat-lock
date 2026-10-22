package com.seatlock.backend.catalogue.internal.web;

import com.seatlock.backend.catalogue.internal.service.MovieService;
import com.seatlock.backend.catalogue.internal.web.dto.CreateMovieRequest;
import com.seatlock.backend.catalogue.internal.web.dto.MovieResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @PostMapping
    public ResponseEntity<MovieResponse> create(@Valid @RequestBody CreateMovieRequest request) {
        MovieResponse response = movieService.create(request);
        return ResponseEntity.created(URI.create("/movies/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public MovieResponse getById(@PathVariable Long id) {
        return movieService.getById(id);
    }
}
