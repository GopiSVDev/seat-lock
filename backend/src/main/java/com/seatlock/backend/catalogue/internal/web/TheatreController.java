package com.seatlock.backend.catalogue.internal.web;

import com.seatlock.backend.catalogue.internal.domain.Language;
import com.seatlock.backend.catalogue.internal.domain.ShowFormat;
import com.seatlock.backend.catalogue.internal.service.ShowService;
import com.seatlock.backend.catalogue.internal.service.TheatreService;
import com.seatlock.backend.catalogue.internal.web.dto.CreateTheatreRequest;
import com.seatlock.backend.catalogue.internal.web.dto.TheatreResponse;
import com.seatlock.backend.catalogue.internal.web.dto.TheatreShowtimesResponse;
import com.seatlock.backend.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/theatres")
@RequiredArgsConstructor
public class TheatreController {

    private final TheatreService theatreService;
    private final ShowService showService;

    @GetMapping
    public PageResponse<TheatreResponse> find(@RequestParam(required = false) Long cityId,
                                              @RequestParam(required = false) @Size(max = 50) String query,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return theatreService.find(cityId, query, page, size);
    }

    @GetMapping("/{id}/shows")
    public TheatreShowtimesResponse findShows(@PathVariable Long id,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                              @RequestParam(defaultValue = "") List<Language> language,
                                              @RequestParam(defaultValue = "") List<ShowFormat> format) {
        return showService.findTheatreShowtimes(id, date, language, format);
    }

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
