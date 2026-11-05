package com.seatlock.backend.catalogue.internal.city;

import com.seatlock.backend.catalogue.internal.city.dto.CityAliasResponse;
import com.seatlock.backend.catalogue.internal.city.dto.CityResponse;
import com.seatlock.backend.catalogue.internal.city.dto.CreateCityAliasRequest;
import com.seatlock.backend.catalogue.internal.city.dto.CreateCityRequest;
import com.seatlock.backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CityService {

    private static final int SEARCH_LIMIT = 10;

    private final CityRepository cityRepository;
    private final CityAliasRepository cityAliasRepository;
    private final CitySearchRepository citySearchRepository;

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

    @Transactional
    public CityResponse create(CreateCityRequest request) {
        City city = new City(request.name().strip(), request.state().strip(), request.popularRank());
        return CityResponse.from(cityRepository.save(city));
    }

    @Transactional
    public CityAliasResponse addAlias(Long cityId, CreateCityAliasRequest request) {
        CityAlias alias = new CityAlias(findCity(cityId), request.alias().strip());
        return CityAliasResponse.from(cityAliasRepository.save(alias));
    }

    public City findCity(Long id) {
        return cityRepository.findById(id).orElseThrow(() -> new NotFoundException("City", id));
    }
}
