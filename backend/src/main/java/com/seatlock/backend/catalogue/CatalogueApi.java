package com.seatlock.backend.catalogue;

public interface CatalogueApi {

    // Throws NotFoundException for an unknown show
    ShowSummary findShowSummary(Long showId);
}
