package com.seatlock.backend.catalogue.internal.repository;

import com.seatlock.backend.catalogue.internal.domain.Genre;
import com.seatlock.backend.catalogue.internal.domain.Language;
import com.seatlock.backend.catalogue.internal.domain.ShowFormat;

import java.util.List;

// Empty lists = any
public record MovieFilter(Long cityId, List<Language> languages, List<Genre> genres, List<ShowFormat> formats) {
}
