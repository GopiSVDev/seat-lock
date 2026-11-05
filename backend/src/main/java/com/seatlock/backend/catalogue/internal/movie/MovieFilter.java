package com.seatlock.backend.catalogue.internal.movie;

import com.seatlock.backend.catalogue.internal.show.Language;
import com.seatlock.backend.catalogue.internal.show.ShowFormat;
import java.util.List;

// Empty lists = any
public record MovieFilter(Long cityId, List<Language> languages, List<Genre> genres, List<ShowFormat> formats) {
}
