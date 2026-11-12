package com.seatlock.backend.catalogue.internal.theatre;

import com.seatlock.backend.catalogue.internal.city.CityService;
import com.seatlock.backend.catalogue.internal.theatre.dto.CreateTheatreRequest;
import com.seatlock.backend.catalogue.internal.theatre.dto.TheatreResponse;
import com.seatlock.backend.shared.exception.NotFoundException;
import com.seatlock.backend.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TheatreService {

    private final TheatreRepository theatreRepository;
    private final TheatreBrowseRepository theatreBrowseRepository;
    private final CityService cityService;

    @Transactional
    public TheatreResponse create(CreateTheatreRequest request) {
        Theatre theatre = new Theatre(request.name(), cityService.findCity(request.cityId()), request.address());
        Theatre saved = theatreRepository.save(theatre);
        log.info("Theatre {} created: {} (city {})", saved.getId(), saved.getName(), request.cityId());
        return TheatreResponse.from(saved);
    }

    public PageResponse<TheatreResponse> find(Long cityId, String query, int page, int size) {
        if (cityId != null) {
            cityService.findCity(cityId); // unknown city → 404, not an empty list
        }
        String name = query == null || query.isBlank() ? null : query.strip();
        long offset = (long) page * size;
        var items = theatreBrowseRepository.find(cityId, name, size, offset).stream().map(TheatreService::toResponse)
                .toList();
        return PageResponse.of(items, page, size, theatreBrowseRepository.count(cityId, name));
    }

    private static TheatreResponse toResponse(TheatreRow row) {
        return new TheatreResponse(row.id(), row.name(), new TheatreResponse.CityInfo(row.cityId(), row.cityName()),
                row.address());
    }

    @Transactional(readOnly = true)
    public TheatreResponse getById(Long id) {
        return theatreRepository.findById(id).map(TheatreResponse::from)
                .orElseThrow(() -> new NotFoundException("Theatre", id));
    }
}
