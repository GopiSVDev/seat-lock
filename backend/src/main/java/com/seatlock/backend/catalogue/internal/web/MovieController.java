package com.seatlock.backend.catalogue.internal.web;

import com.seatlock.backend.catalogue.internal.domain.Genre;
import com.seatlock.backend.catalogue.internal.domain.Language;
import com.seatlock.backend.catalogue.internal.domain.ShowFormat;
import com.seatlock.backend.catalogue.internal.repository.MovieFilter;
import com.seatlock.backend.catalogue.internal.service.MovieService;
import com.seatlock.backend.catalogue.internal.web.dto.CreateMovieRequest;
import com.seatlock.backend.catalogue.internal.web.dto.MovieCardResponse;
import com.seatlock.backend.catalogue.internal.web.dto.MovieResponse;
import com.seatlock.backend.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    // Repeat a parameter for several values: language=Tamil&language=English (either one matches)
    @GetMapping
    public PageResponse<MovieCardResponse> find(@RequestParam(required = false) Long cityId,
                                                @RequestParam(defaultValue = "") List<Language> language,
                                                @RequestParam(defaultValue = "") List<Genre> genre,
                                                @RequestParam(defaultValue = "") List<ShowFormat> format,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return movieService.find(new MovieFilter(cityId, language, genre, format), page, size);
    }

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
