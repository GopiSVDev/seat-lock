package com.seatlock.backend.catalogue.internal.web.dto;

import com.seatlock.backend.catalogue.internal.domain.City;
import com.seatlock.backend.catalogue.internal.repository.CityMatch;

// matchedAlias: the alias a search matched ("Madras" for Chennai), null when the name matched or no search ran
public record CityResponse(
        Long id,
        String name,
        String state,
        Integer popularRank,
        String matchedAlias
) {

    public static CityResponse from(CityMatch match) {
        return new CityResponse(match.id(), match.name(), match.state(), match.popularRank(), match.matchedAlias());
    }

    public static CityResponse from(City city) {
        return new CityResponse(city.getId(), city.getName(), city.getState(), city.getPopularRank(), null);
    }
}
