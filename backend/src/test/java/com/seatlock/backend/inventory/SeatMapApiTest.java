package com.seatlock.backend.inventory;

import com.seatlock.backend.support.Api.Response;
import com.seatlock.backend.support.IntegrationTest;
import com.seatlock.backend.support.TestShow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeatMapApiTest extends IntegrationTest {

    @Test
    void layoutListsEveryRowAndSeat() {
        TestShow show = newShow();

        Response layout = api.get("/shows/" + show.showId() + "/layout", null);

        assertThat(layout.body().get("totalSeats").asInt()).isEqualTo(28);
        assertThat(layout.body().get("rows").get(2).get("label").asString()).isEqualTo("C");
        assertThat(layout.body().get("rows").get(2).get("seatType").asString()).isEqualTo("RECLINER");
        assertThat(layout.body().get("rows").get(0).get("price").asLong()).isEqualTo(REGULAR_PRICE);
        assertThat(layout.body().get("rows").get(2).get("price").asLong()).isEqualTo(RECLINER_PRICE);
        assertThat(layout.header("Cache-Control")).contains("max-age=86400");
    }

    @Test
    void newShowHasEverySeatAvailable() {
        TestShow show = newShow();

        Response availability = api.get("/shows/" + show.showId() + "/availability", null);

        assertThat(availability.body().get("availableSeats").asInt()).isEqualTo(28);
        assertThat(availability.body().get("unavailable").size()).isZero();
    }

    @Test
    void unknownShowIsNotFound() {
        assertThat(api.get("/shows/999999999/layout", null).status()).isEqualTo(404);
        assertThat(api.get("/shows/999999999/availability", null).status()).isEqualTo(404);
    }
}
