package com.seatlock.backend.catalogue.internal.service;

import com.seatlock.backend.catalogue.internal.domain.City;
import com.seatlock.backend.catalogue.internal.domain.CityAlias;
import com.seatlock.backend.catalogue.internal.repository.CityAliasRepository;
import com.seatlock.backend.catalogue.internal.repository.CityRepository;
import com.seatlock.backend.catalogue.internal.repository.CitySearchRepository;
import com.seatlock.backend.catalogue.internal.web.dto.CityAliasResponse;
import com.seatlock.backend.catalogue.internal.web.dto.CityResponse;
import com.seatlock.backend.catalogue.internal.web.dto.CreateCityAliasRequest;
import com.seatlock.backend.catalogue.internal.web.dto.CreateCityRequest;
import com.seatlock.backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CityService {

    // Enough suggestions for a dropdown under a search box
    private static final int SEARCH_LIMIT = 10;

    private final CityRepository cityRepository;
    private final CityAliasRepository cityAliasRepository;
    private final CitySearchRepository citySearchRepository;

    // No query: list (popular ones in rank order, else alphabetical). With a query: best matches first.
    public List<CityResponse> find(String query, boolean popularOnly, boolean withTheatresOnly) {
        String text = query == null ? "" : query.strip();
        var matches = text.isEmpty()
                ? citySearchRepository.list(popularOnly, withTheatresOnly)
                : citySearchRepository.search(text, popularOnly, withTheatresOnly, SEARCH_LIMIT);
        return matches.stream().map(CityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CityResponse getById(Long id) {
        return CityResponse.from(findCity(id));
    }

    // Duplicate name in the same state or a taken popular rank → unique index → 409
    @Transactional
    public CityResponse create(CreateCityRequest request) {
        City city = new City(request.name().strip(), request.state().strip(), request.popularRank());
        return CityResponse.from(cityRepository.save(city));
    }

    // An alias already used by any city → unique index → 409
    @Transactional
    public CityAliasResponse addAlias(Long cityId, CreateCityAliasRequest request) {
        CityAlias alias = new CityAlias(findCity(cityId), request.alias().strip());
        return CityAliasResponse.from(cityAliasRepository.save(alias));
    }

    City findCity(Long id) {
        return cityRepository.findById(id).orElseThrow(() -> new NotFoundException("City", id));
    }
}
