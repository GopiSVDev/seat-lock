package com.seatlock.backend.catalogue.internal.city;

import com.seatlock.backend.catalogue.internal.city.dto.CityAliasResponse;
import com.seatlock.backend.catalogue.internal.city.dto.CityResponse;
import com.seatlock.backend.catalogue.internal.city.dto.CreateCityAliasRequest;
import com.seatlock.backend.catalogue.internal.city.dto.CreateCityRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/cities")
@RequiredArgsConstructor
public class CityController {

    private final CityService cityService;

    @GetMapping
    public List<CityResponse> find(@RequestParam(required = false) @Size(max = 50) String query,
                                   @RequestParam(defaultValue = "false") boolean popular,
                                   @RequestParam(defaultValue = "false") boolean withTheatres) {
        return cityService.find(query, popular, withTheatres);
    }

    @GetMapping("/{id}")
    public CityResponse getById(@PathVariable Long id) {
        return cityService.getById(id);
    }

    @PostMapping
    public ResponseEntity<CityResponse> create(@Valid @RequestBody CreateCityRequest request) {
        CityResponse response = cityService.create(request);
        return ResponseEntity.created(URI.create("/cities/" + response.id())).body(response);
    }

    @PostMapping("/{id}/aliases")
    public ResponseEntity<CityAliasResponse> addAlias(@PathVariable Long id,
                                                      @Valid @RequestBody CreateCityAliasRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cityService.addAlias(id, request));
    }
}
