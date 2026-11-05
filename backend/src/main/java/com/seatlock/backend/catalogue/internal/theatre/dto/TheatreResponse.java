package com.seatlock.backend.catalogue.internal.theatre.dto;

import com.seatlock.backend.catalogue.internal.theatre.Theatre;


public record TheatreResponse(
        Long id,
        String name,
        CityInfo city,
        String address
) {

    public record CityInfo(Long id, String name) {
    }

    public static TheatreResponse from(Theatre theatre) {
        return new TheatreResponse(
                theatre.getId(),
                theatre.getName(),
                new CityInfo(theatre.getCity().getId(), theatre.getCity().getName()),
                theatre.getAddress()
        );
    }
}
