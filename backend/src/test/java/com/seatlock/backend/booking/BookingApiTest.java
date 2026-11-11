package com.seatlock.backend.booking;

import com.seatlock.backend.support.Api.Response;
import com.seatlock.backend.support.IntegrationTest;
import com.seatlock.backend.support.TestShow;
import com.seatlock.backend.support.TestUser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BookingApiTest extends IntegrationTest {

    private final TestUser asha = TestUser.customer("Asha Kumar");
    private final TestUser ravi = TestUser.customer("Ravi Shankar");

    @Test
    void bookingBelongsToTheTokenUserAndKeepsTheShow() {
        TestShow show = newShow();

        Response response = book(asha, show, show.seats("A", 2));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("customerName").asString()).isEqualTo("Asha Kumar");
        assertThat(response.body().get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(response.body().get("show").get("id").asLong()).isEqualTo(show.showId());
        assertThat(response.body().get("show").get("movie").get("id").asLong()).isEqualTo(show.movieId());
        assertThat(response.body().get("show").get("theatre").get("id").asLong()).isEqualTo(show.theatreId());
        assertThat(ids(response.body().get("seats"), "showSeatId")).containsExactlyElementsOf(show.seats("A", 2));
        assertThat(jdbc.sql("select customer_id from booking.booking where id = :id").param("id", response.id())
                .query(String.class).single()).isEqualTo(asha.id());
        assertThat(seatStatuses(show.seats("A", 2))).containsOnly("BOOKED");
    }

    @Test
    void confirmingTurnsTheHoldIntoABooking() {
        TestShow show = newShow();
        String holdId = hold(asha, show, show.seats("A", 2)).text("id");

        Response booking = confirm(asha, holdId);

        assertThat(booking.status()).isEqualTo(200);
        assertThat(ids(booking.body().get("seats"), "showSeatId")).containsExactlyElementsOf(show.seats("A", 2));
        assertThat(api.get("/holds/" + holdId, asha).status()).isEqualTo(404);
        assertThat(redis.hasKey("hold:" + holdId)).isFalse();
        assertThat(redis.hasKey("seat:" + show.showId() + ":" + show.seats("A", 1).getFirst())).isFalse();
        // The hold is gone, but its booking is found again: no second booking
        Response again = confirm(asha, holdId);
        assertThat(again.status()).isEqualTo(200);
        assertThat(again.id()).isEqualTo(booking.id());
    }

    @Test
    void bookingKeepsWhatEachSeatCost() {
        TestShow show = newShow();
        List<Long> seats = new ArrayList<>(show.seats("A", 1));
        seats.add(show.seats("C", 1).getFirst());
        long bookingId = book(asha, show, seats).id();

        Response booking = api.get("/bookings/" + bookingId, asha);
        assertThat(booking.body().get("totalAmount").asLong()).isEqualTo(REGULAR_PRICE + RECLINER_PRICE);
        assertThat(ids(booking.body().get("seats"), "price")).containsExactlyInAnyOrder(REGULAR_PRICE, RECLINER_PRICE);

        Response cancelled = api.post("/bookings/" + bookingId + "/cancel", asha);
        assertThat(cancelled.body().get("totalAmount").asLong()).isEqualTo(REGULAR_PRICE + RECLINER_PRICE);
    }

    @Test
    void takenSeatsAreAConflict() {
        TestShow show = newShow();
        book(asha, show, show.seats("A", 2));

        Response response = book(ravi, show, show.seats("A", 3));

        assertThat(response.status()).isEqualTo(409);
        Long third = show.seats("A", 3).get(2);
        assertThat(seatStatuses(List.of(third))).containsOnly("AVAILABLE");
        assertThat(redis.hasKey("seat:" + show.showId() + ":" + third)).as("failed hold is released").isFalse();
    }

    @Test
    void onlyTheHolderCanConfirm() {
        TestShow show = newShow();
        String holdId = hold(asha, show, show.seats("A", 1)).text("id");

        assertThat(confirm(ravi, holdId).status()).isEqualTo(404);
        assertThat(confirm(asha, "no-such-hold").status()).isEqualTo(404);
        assertThat(api.post("/bookings", asha, Map.of()).status()).isEqualTo(400);
        assertThat(confirm(asha, holdId).status()).isEqualTo(200);
    }

    @Test
    void seatsOfAnotherShowAreNotFound() {
        TestShow show = newShow();
        TestShow other = newShow();

        assertThat(book(asha, show, other.seats("A", 1)).status()).isEqualTo(404);
    }

    @Test
    void anotherUsersBookingDoesNotExistForYou() {
        TestShow show = newShow();
        long bookingId = book(asha, show, show.seats("A", 1)).id();

        assertThat(api.get("/bookings/" + bookingId, asha).status()).isEqualTo(200);
        assertThat(api.get("/bookings/" + bookingId, ravi).status()).isEqualTo(404);
        assertThat(api.post("/bookings/" + bookingId + "/cancel", ravi).status()).isEqualTo(404);
        assertThat(seatStatuses(show.seats("A", 1))).containsOnly("BOOKED");
    }

    @Test
    void myBookingsSplitIntoUpcomingAndPastByShowTime() {
        TestShow first = newShow();
        TestShow second = newShow();
        long future = book(asha, first, first.seats("A", 1)).id();
        long past = book(asha, second, second.seats("A", 1)).id();
        moveShowSnapshotIntoThePast(past);

        assertThat(ids(api.get("/bookings?period=UPCOMING", asha).body().get("items"), "id")).containsExactly(future);
        assertThat(ids(api.get("/bookings", asha).body().get("items"), "id")).containsExactly(future);
        assertThat(ids(api.get("/bookings?period=PAST", asha).body().get("items"), "id")).containsExactly(past);
        assertThat(api.get("/bookings", ravi).body().get("totalItems").asLong()).isZero();
    }

    @Test
    void listParametersAreValidated() {
        assertThat(api.get("/bookings?size=51", asha).status()).isEqualTo(400);
        assertThat(api.get("/bookings?period=SOON", asha).status()).isEqualTo(400);
    }

    @Test
    void cancelPutsTheSeatsBackOnSale() {
        TestShow show = newShow();
        long bookingId = book(asha, show, show.seats("B", 2)).id();

        Response cancelled = api.post("/bookings/" + bookingId + "/cancel", asha);

        assertThat(cancelled.status()).isEqualTo(200);
        assertThat(cancelled.body().get("status").asString()).isEqualTo("CANCELLED");
        assertThat(cancelled.body().get("cancelledAt").isNull()).isFalse();
        assertThat(seatStatuses(show.seats("B", 2))).containsOnly("AVAILABLE");
        assertThat(book(ravi, show, show.seats("B", 2)).status()).isEqualTo(200);
    }

    @Test
    void cancellingTwiceChangesNothing() {
        TestShow show = newShow();
        long bookingId = book(asha, show, show.seats("A", 1)).id();

        Response first = api.post("/bookings/" + bookingId + "/cancel", asha);
        Response second = api.post("/bookings/" + bookingId + "/cancel", asha);

        assertThat(second.status()).isEqualTo(200);
        assertThat(second.body().get("cancelledAt")).isEqualTo(first.body().get("cancelledAt"));
    }

    @Test
    void cancellingAfterTheShowStartedIsAConflict() {
        TestShow show = newShow();
        long bookingId = book(asha, show, show.seats("A", 1)).id();
        moveShowSnapshotIntoThePast(bookingId);

        Response response = api.post("/bookings/" + bookingId + "/cancel", asha);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.body().get("detail").asString()).isEqualTo("Show has already started");
        assertThat(api.get("/bookings/" + bookingId, asha).body().get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(seatStatuses(show.seats("A", 1))).containsOnly("BOOKED");
    }

    // The API never creates past shows, so tests move the booking's copy of the show time
    private void moveShowSnapshotIntoThePast(long bookingId) {
        jdbc.sql("update booking.booking set show_start_time = now() - interval '1 hour' where id = :id")
                .param("id", bookingId).update();
    }
}
