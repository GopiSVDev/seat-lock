package com.seatlock.backend.catalogue.internal.show;

import com.seatlock.backend.catalogue.internal.show.dto.CreateShowRequest;
import com.seatlock.backend.catalogue.internal.show.dto.ShowResponse;
import com.seatlock.backend.catalogue.internal.show.dto.ShowtimesResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    @GetMapping
    public ShowtimesResponse find(@RequestParam Long movieId, @RequestParam Long cityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "") List<Language> language,
            @RequestParam(defaultValue = "") List<ShowFormat> format) {
        return showService.findShowtimes(movieId, cityId, date, language, format);
    }

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
