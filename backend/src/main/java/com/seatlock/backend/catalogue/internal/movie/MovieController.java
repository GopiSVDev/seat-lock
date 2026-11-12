package com.seatlock.backend.catalogue.internal.movie;

import com.seatlock.backend.catalogue.internal.movie.dto.CreateMovieRequest;
import com.seatlock.backend.catalogue.internal.movie.dto.MovieCardResponse;
import com.seatlock.backend.catalogue.internal.movie.dto.MovieResponse;
import com.seatlock.backend.catalogue.internal.show.Language;
import com.seatlock.backend.catalogue.internal.show.ShowFormat;
import com.seatlock.backend.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

import static com.seatlock.backend.shared.web.ApiVersionConfig.API_V1;

@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

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
        return ResponseEntity.created(URI.create(API_V1 + "/movies/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public MovieResponse getById(@PathVariable Long id) {
        return movieService.getById(id);
    }
}
