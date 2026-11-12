package com.seatlock.backend.catalogue;

import com.seatlock.backend.shared.time.IndiaTime;
import com.seatlock.backend.support.Api.Response;
import com.seatlock.backend.support.IntegrationTest;
import com.seatlock.backend.support.TestShow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogueApiTest extends IntegrationTest {

    @Test
    void citySearchForgivesTyposAndOldNames() {
        assertThat(api.get("/api/v1/cities?query=chenai", null).body().get(0).get("name").asString())
                .isEqualTo("Chennai");
        assertThat(api.get("/api/v1/cities?query=madras", null).body().get(0).get("name").asString())
                .isEqualTo("Chennai");
    }

    @Test
    void sameCityTwiceIsConflict() {
        Map<String, String> city = Map.of("name", unique("Testpur"), "state", "Kerala");

        assertThat(api.post("/api/v1/cities", admin, city).status()).isEqualTo(201);
        assertThat(api.post("/api/v1/cities", admin, city).status()).isEqualTo(409);
    }

    @Test
    void invalidMovieReportsEveryBadField() {
        Response response = api.post("/api/v1/movies", admin,
                Map.of("title", "", "durationMinutes", 0, "genres", List.of()));

        assertThat(response.status()).isEqualTo(400);
        for (String field : List.of("title", "durationMinutes", "certification", "releaseDate", "genres")) {
            assertThat(response.body().get("errors").has(field)).as(field).isTrue();
        }
    }

    @Test
    void scheduledShowIsBrowsableByCityDayAndTheatre() {
        TestShow show = newShow();
        LocalDate day = LocalDate.ofInstant(show.startTime(), IndiaTime.ZONE);

        Response nowShowing = api.get("/api/v1/movies?cityId=" + show.cityId(), null);
        assertThat(ids(nowShowing.body().get("items"), "id")).containsExactly(show.movieId());

        Response showtimes = api
                .get("/api/v1/shows?movieId=%d&cityId=%d&date=%s".formatted(show.movieId(), show.cityId(), day), null);
        assertThat(ids(showtimes.body().get("theatres"), "id")).containsExactly(show.theatreId());
        assertThat(ids(showtimes.body().get("theatres").get(0).get("shows"), "id")).containsExactly(show.showId());

        Response theatrePage = api.get("/api/v1/theatres/%d/shows?date=%s".formatted(show.theatreId(), day), null);
        assertThat(ids(theatrePage.body().get("movies"), "id")).containsExactly(show.movieId());

        Response theatres = api.get("/api/v1/theatres?cityId=" + show.cityId(), null);
        assertThat(ids(theatres.body().get("items"), "id")).containsExactly(show.theatreId());
    }

    @Test
    void showKeepsItsPricesPerSeatType() {
        TestShow show = newShow();

        Response response = api.get("/api/v1/shows/" + show.showId(), null);

        assertThat(response.body().get("prices").get("REGULAR").asLong()).isEqualTo(REGULAR_PRICE);
        assertThat(response.body().get("prices").get("RECLINER").asLong()).isEqualTo(RECLINER_PRICE);
        assertThat(response.body().get("prices").has("PREMIUM")).isFalse();
    }

    @Test
    void pricesMustMatchTheScreensSeatTypes() {
        TestShow show = newShow();

        // The screen has REGULAR and RECLINER seats
        assertThat(createShow(show, Map.of("REGULAR", 15000)).status()).as("missing type").isEqualTo(400);
        assertThat(createShow(show, Map.of("REGULAR", 15000, "RECLINER", 40000, "PREMIUM", 25000)).status())
                .as("extra type").isEqualTo(400);
        assertThat(createShow(show, Map.of("REGULAR", 0, "RECLINER", 40000)).status()).as("zero price").isEqualTo(400);
        assertThat(createShow(show, Map.of()).status()).as("no prices").isEqualTo(400);
        assertThat(createShow(show, Map.of("REGULAR", 15000, "RECLINER", 40000)).status()).isEqualTo(201);
    }

    @Test
    void showtimesForAPastDayAreRejected() {
        TestShow show = newShow();

        Response response = api.get(
                "/api/v1/shows?movieId=%d&cityId=%d&date=2020-01-01".formatted(show.movieId(), show.cityId()), null);

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void languageFilterNeedsACity() {
        assertThat(api.get("/api/v1/movies?language=ENGLISH", null).status()).isEqualTo(400);
    }

    @Test
    void unknownCityIsNotFound() {
        assertThat(api.get("/api/v1/movies?cityId=999999999", null).status()).isEqualTo(404);
    }

    private Response createShow(TestShow show, Map<String, Integer> prices) {
        return api.post("/api/v1/shows", admin,
                Map.of("movieId", show.movieId(), "screenId", show.screenId(), "startTime",
                        show.startTime().plusSeconds(86400).toString(), "language", "ENGLISH", "format", "TWO_D",
                        "prices", prices));
    }

    @Test
    void chennaiIsReferenceData() {
        assertThat(api.get("/api/v1/cities/" + CHENNAI, null).body().get("name").asString()).isEqualTo("Chennai");
    }
}
