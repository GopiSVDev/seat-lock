package com.seatlock.backend.inventory;

import com.seatlock.backend.support.Api.Response;
import com.seatlock.backend.support.IntegrationTest;
import com.seatlock.backend.support.TestShow;
import com.seatlock.backend.support.TestUser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class HoldApiTest extends IntegrationTest {

    private final TestUser asha = TestUser.customer("Asha Kumar");
    private final TestUser ravi = TestUser.customer("Ravi Shankar");

    @Test
    void holdReservesTheSeatsForFiveMinutes() {
        TestShow show = newShow();
        Instant before = Instant.now();

        Response hold = hold(asha, show, show.seats("A", 2));

        assertThat(hold.status()).isEqualTo(201);
        String holdId = hold.text("id");
        assertThat(hold.header("Location")).isEqualTo("/api/v1/holds/" + holdId);
        assertThat(hold.body().get("showId").asLong()).isEqualTo(show.showId());
        assertThat(ids(hold.body().get("seats"), "showSeatId")).containsExactlyElementsOf(show.seats("A", 2));
        assertThat(hold.body().get("seats").get(0).get("label").asString()).isEqualTo("A1");
        assertThat(Instant.parse(hold.text("expiresAt"))).isBetween(before.plus(Duration.ofMinutes(5)).minusSeconds(5),
                before.plus(Duration.ofMinutes(5)).plusSeconds(5));
        // Redis drops the keys by itself
        assertThat(redis.getExpire("hold:" + holdId)).isBetween(1L, 300L);
        assertThat(redis.getExpire("seat:" + show.showId() + ":" + show.seats("A", 1).getFirst())).isBetween(1L, 300L);
        // Postgres only learns about seats once they are booked
        assertThat(seatStatuses(show.seats("A", 2))).containsOnly("AVAILABLE");

        Response again = api.get("/api/v1/holds/" + holdId, asha);
        assertThat(again.status()).isEqualTo(200);
        assertThat(again.text("expiresAt")).isEqualTo(hold.text("expiresAt"));
    }

    @Test
    void holdShowsEachSeatsPriceAndTheTotal() {
        TestShow show = newShow();
        List<Long> seats = new ArrayList<>(show.seats("A", 2));
        seats.add(show.seats("C", 1).getFirst());

        Response hold = hold(asha, show, seats);

        assertThat(hold.body().get("totalAmount").asLong()).isEqualTo(2 * REGULAR_PRICE + RECLINER_PRICE);
        assertThat(hold.body().get("seats").get(0).get("price").asLong()).isEqualTo(REGULAR_PRICE);
        assertThat(hold.body().get("seats").get(2).get("price").asLong()).isEqualTo(RECLINER_PRICE);
        assertThat(api.get("/api/v1/holds/" + hold.text("id"), asha).body().get("totalAmount").asLong())
                .isEqualTo(2 * REGULAR_PRICE + RECLINER_PRICE);
    }

    @Test
    void heldSeatsAreTakenForEveryoneElse() {
        TestShow show = newShow();
        hold(asha, show, show.seats("A", 2));

        Response response = hold(ravi, show, show.seats("A", 3));

        assertThat(response.status()).isEqualTo(409);
        assertThat(redis.hasKey("seat:" + show.showId() + ":" + show.seats("A", 3).get(2))).isFalse();
        Response availability = api.get("/api/v1/shows/" + show.showId() + "/availability", null);
        assertThat(ids(availability.body().get("unavailable"))).containsExactlyElementsOf(show.seats("A", 2));
        assertThat(availability.body().get("availableSeats").asInt()).isEqualTo(26);
    }

    @Test
    void releasingAHoldFreesTheSeats() {
        TestShow show = newShow();
        String holdId = hold(asha, show, show.seats("A", 2)).text("id");

        assertThat(api.send("DELETE", "/api/v1/holds/" + holdId, asha.token(), null).status()).isEqualTo(204);

        assertThat(api.get("/api/v1/holds/" + holdId, asha).status()).isEqualTo(404);
        assertThat(api.send("DELETE", "/api/v1/holds/" + holdId, asha.token(), null).status()).isEqualTo(404);
        assertThat(hold(ravi, show, show.seats("A", 2)).status()).isEqualTo(201);
    }

    @Test
    void expiredHoldFreesTheSeatsAndCannotBeBooked() {
        TestShow show = newShow();
        String holdId = hold(asha, show, show.seats("A", 2)).text("id");

        expire(holdId);

        assertThat(hold(ravi, show, show.seats("A", 2)).status()).isEqualTo(201);
        assertThat(confirm(asha, holdId).status()).isEqualTo(404);
    }

    @Test
    void aNewHoldReplacesYourPreviousOneForTheShow() {
        TestShow show = newShow();
        String first = hold(asha, show, show.seats("A", 2)).text("id");

        // A2 was in the previous hold: re-selecting it is fine
        Response second = hold(asha, show, show.seatIdsByRow().get("A").subList(1, 3));

        assertThat(second.status()).isEqualTo(201);
        assertThat(api.get("/api/v1/holds/" + first, asha).status()).isEqualTo(404);
        assertThat(hold(ravi, show, show.seats("A", 1)).status()).as("A1 was let go").isEqualTo(201);
    }

    @Test
    void holdsOnOtherShowsAreKept() {
        TestShow show = newShow();
        TestShow other = newShow();
        String first = hold(asha, show, show.seats("A", 1)).text("id");

        hold(asha, other, other.seats("A", 1));

        assertThat(api.get("/api/v1/holds/" + first, asha).status()).isEqualTo(200);
    }

    @Test
    void anotherUsersHoldDoesNotExistForYou() {
        TestShow show = newShow();
        String holdId = hold(asha, show, show.seats("A", 1)).text("id");

        assertThat(api.get("/api/v1/holds/" + holdId, ravi).status()).isEqualTo(404);
        assertThat(api.send("DELETE", "/api/v1/holds/" + holdId, ravi.token(), null).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/holds/" + holdId, asha).status()).isEqualTo(200);
    }

    @Test
    void bookedSeatsCannotBeHeld() {
        TestShow show = newShow();
        book(asha, show, show.seats("A", 1));

        assertThat(hold(ravi, show, show.seats("A", 1)).status()).isEqualTo(409);
        assertThat(redis.hasKey("seat:" + show.showId() + ":" + show.seats("A", 1).getFirst())).isFalse();
    }

    @Test
    void seatsOfAnotherShowAreNotFound() {
        TestShow show = newShow();
        TestShow other = newShow();

        assertThat(hold(asha, show, other.seats("A", 1)).status()).isEqualTo(404);
        assertThat(redis.hasKey("seat:" + show.showId() + ":" + other.seats("A", 1).getFirst())).isFalse();
    }

    @Test
    void bestAvailableHoldsAnAdjacentBlockAndSkipsHeldSeats() {
        TestShow show = newShow();

        Response first = bestAvailable(asha, show, 2, "RECLINER");
        Response second = bestAvailable(ravi, show, 2, "RECLINER");

        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        List<Long> firstSeats = ids(first.body().get("seats"), "showSeatId");
        List<Long> secondSeats = ids(second.body().get("seats"), "showSeatId");
        assertThat(show.seatIdsByRow().get("C")).containsSequence(firstSeats).containsSequence(secondSeats);
        assertThat(Collections.disjoint(firstSeats, secondSeats)).isTrue();
    }

    @Test
    void bestAvailableWithoutABlockBigEnoughIsAConflict() {
        TestShow show = newShow();

        Response response = bestAvailable(asha, show, 9, "RECLINER");

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.text("detail")).isEqualTo("No 9 adjacent RECLINER seats available");
    }

    @Test
    void bestAvailableForAnUnknownShowIsNotFound() {
        Response response = api.post("/api/v1/holds/best-available", asha,
                Map.of("showId", 999999999, "quantity", 1, "seatType", "REGULAR"));

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.text("detail")).isEqualTo("Show not found: 999999999");
    }

    @Test
    void requestsAreValidated() {
        TestShow show = newShow();
        List<Long> eleven = new ArrayList<>(show.seatIdsByRow().get("A"));
        eleven.add(show.seats("B", 1).getFirst());

        assertThat(hold(asha, show, eleven).status()).isEqualTo(400);
        assertThat(hold(asha, show, List.of()).status()).isEqualTo(400);
        assertThat(bestAvailable(asha, show, 0, "REGULAR").status()).isEqualTo(400);
        assertThat(bestAvailable(asha, show, 11, "REGULAR").status()).isEqualTo(400);
        assertThat(bestAvailable(asha, show, 1, "BALCONY").status()).isEqualTo(400);
    }

    private Response bestAvailable(TestUser user, TestShow show, int quantity, String seatType) {
        return api.post("/api/v1/holds/best-available", user,
                Map.of("showId", show.showId(), "quantity", quantity, "seatType", seatType));
    }

    // What Redis does when the 5 minutes are up, done now. Mirrors HoldStore's key layout.
    private void expire(String holdId) {
        Map<Object, Object> hold = redis.opsForHash().entries("hold:" + holdId);
        String show = (String) hold.get("show");
        for (String seat : ((String) hold.get("seats")).split(",")) {
            redis.delete("seat:" + show + ":" + seat);
            redis.opsForZSet().add("held:" + show, seat, 0);
        }
        redis.delete(List.of("hold:" + holdId, "user-hold:" + show + ":" + hold.get("customer")));
    }

    private static List<Long> ids(JsonNode array) {
        return IntStream.range(0, array.size()).mapToObj(i -> array.get(i).asLong()).toList();
    }
}
