package com.seatlock.backend.catalogue.internal.show;

// Unused ids are null
public record ShowScope(Long movieId, Long cityId, Long theatreId) {

    public static ShowScope movieInCity(Long movieId, Long cityId) {
        return new ShowScope(movieId, cityId, null);
    }

    public static ShowScope atTheatre(Long theatreId) {
        return new ShowScope(null, null, theatreId);
    }

    boolean groupsByTheatre() {
        return theatreId == null;
    }
}
