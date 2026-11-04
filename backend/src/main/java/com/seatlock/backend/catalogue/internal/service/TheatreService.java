package com.seatlock.backend.catalogue.internal.service;

import com.seatlock.backend.catalogue.internal.domain.Theatre;
import com.seatlock.backend.catalogue.internal.repository.TheatreBrowseRepository;
import com.seatlock.backend.catalogue.internal.repository.TheatreRepository;
import com.seatlock.backend.catalogue.internal.repository.TheatreRow;
import com.seatlock.backend.catalogue.internal.web.dto.CreateTheatreRequest;
import com.seatlock.backend.catalogue.internal.web.dto.TheatreResponse;
import com.seatlock.backend.shared.exception.NotFoundException;
import com.seatlock.backend.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TheatreService {

    private final TheatreRepository theatreRepository;
    private final TheatreBrowseRepository theatreBrowseRepository;
    private final CityService cityService;

    @Transactional
    public TheatreResponse create(CreateTheatreRequest request) {
        Theatre theatre = new Theatre(request.name(), cityService.findCity(request.cityId()), request.address());
        return TheatreResponse.from(theatreRepository.save(theatre));
    }

    // Theatres by name; cityId and name filters optional (blank name = no filter)
    public PageResponse<TheatreResponse> find(Long cityId, String query, int page, int size) {
        if (cityId != null) {
            cityService.findCity(cityId);   // unknown city → 404, not an empty list
        }
        String name = query == null || query.isBlank() ? null : query.strip();
        long offset = (long) page * size;
        var items = theatreBrowseRepository.find(cityId, name, size, offset).stream()
                .map(TheatreService::toResponse)
                .toList();
        return PageResponse.of(items, page, size, theatreBrowseRepository.count(cityId, name));
    }

    private static TheatreResponse toResponse(TheatreRow row) {
        return new TheatreResponse(row.id(), row.name(),
                new TheatreResponse.CityInfo(row.cityId(), row.cityName()), row.address());
    }

    @Transactional(readOnly = true)
    public TheatreResponse getById(Long id) {
        return theatreRepository.findById(id)
                .map(TheatreResponse::from)
                .orElseThrow(() -> new NotFoundException("Theatre", id));
    }
}
