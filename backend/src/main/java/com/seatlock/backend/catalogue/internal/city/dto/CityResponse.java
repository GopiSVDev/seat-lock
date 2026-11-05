package com.seatlock.backend.catalogue.internal.city.dto;

import com.seatlock.backend.catalogue.internal.city.City;
import com.seatlock.backend.catalogue.internal.city.CityMatch;


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
