package com.seatlock.backend.catalogue.internal.repository;

// A city found by search or listing; matchedAlias is set when the city was found through one of its aliases
public record CityMatch(Long id, String name, String state, Integer popularRank, String matchedAlias) {
}
