package com.seatlock.backend.catalogue.internal.repository;

import com.seatlock.backend.catalogue.internal.domain.Genre;
import com.seatlock.backend.catalogue.internal.domain.Language;
import com.seatlock.backend.catalogue.internal.domain.ShowFormat;

import java.util.List;

// Filters for movie lists; empty lists mean "any". cityId null = whole catalogue (languages/formats then unused).
public record MovieFilter(Long cityId, List<Language> languages, List<Genre> genres, List<ShowFormat> formats) {
}
