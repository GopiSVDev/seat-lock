package com.seatlock.backend.catalogue.internal.web.dto;

import com.seatlock.backend.catalogue.internal.domain.CityAlias;

public record CityAliasResponse(
        Long id,
        Long cityId,
        String alias
) {

    public static CityAliasResponse from(CityAlias alias) {
        return new CityAliasResponse(alias.getId(), alias.getCity().getId(), alias.getAlias());
    }
}
