package com.seatlock.backend.booking;

import com.seatlock.backend.support.Api.Response;
import com.seatlock.backend.support.Concurrently;
import com.seatlock.backend.support.IntegrationTest;
import com.seatlock.backend.support.TestShow;
import com.seatlock.backend.support.TestUser;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// The core promise: under concurrent requests a seat is never sold twice
class BookingConcurrencyTest extends IntegrationTest {

    @Test
    void manyBuyersRacingForTheSameSeatsOnlyOneWins() {
        TestShow show = newShow();
        List<Long> seats = show.seats("A", 2);

        List<Response> responses = Concurrently.run(20, n -> book(TestUser.customer("Buyer " + n), show, seats));

        assertThat(countByStatus(responses)).containsExactlyInAnyOrderEntriesOf(Map.of(200, 1L, 409, 19L));
        assertThat(confirmedBookingSeats(seats)).isEqualTo(2);
    }

    @Test
    void opposingSeatOrdersStillGiveOneWinner() {
        TestShow show = newShow();
        List<Long> ascending = show.seatIdsByRow().get("A");
        List<Long> descending = ascending.reversed();

        // A hold takes all its seats in one Redis script, so no two buyers can each get
        // part of the row
        List<Response> responses = Concurrently.run(20,
                n -> book(TestUser.customer("Buyer " + n), show, n % 2 == 0 ? ascending : descending));

        assertThat(countByStatus(responses)).containsExactlyInAnyOrderEntriesOf(Map.of(200, 1L, 409, 19L));
        assertThat(confirmedBookingSeats(ascending)).isEqualTo(10);
    }

    @Test
    void bestAvailableUnderLoadHoldsEachSeatOnce() {
        TestShow show = newShow();

        List<TestUser> buyers = IntStream.range(0, 30).mapToObj(n -> TestUser.customer("Buyer " + n)).toList();
        List<Response> holds = Concurrently.run(30, n -> api.post("/api/v1/holds/best-available", buyers.get(n),
                Map.of("showId", show.showId(), "quantity", 1, "seatType", "REGULAR")));

        // A buyer gives up after a few lost races, so the split can vary; what must
        // hold: at most 20 winners,
        // each with a different seat, and every winner can book
        Map<Integer, Long> statuses = countByStatus(holds);
        assertThat(statuses.keySet()).isSubsetOf(201, 409);
        long winners = statuses.getOrDefault(201, 0L);
        assertThat(winners).isBetween(1L, 20L);

        Set<Long> held = new HashSet<>();
        holds.stream().filter(r -> r.status() == 201)
                .forEach(r -> held.addAll(ids(r.body().get("seats"), "showSeatId")));
        assertThat(held).hasSize((int) winners);

        for (int n = 0; n < 30; n++) {
            if (holds.get(n).status() == 201) {
                assertThat(confirm(buyers.get(n), holds.get(n).text("id")).status()).isEqualTo(200);
            }
        }
        assertThat(jdbc.sql("select count(*) from inventory.show_seat where show_id = :id and status = 'BOOKED'")
                .param("id", show.showId()).query(Long.class).single()).isEqualTo(winners);
    }

    @Test
    void doubleTappedCheckoutAndPayBooksOnce() {
        TestShow show = newShow();
        TestUser asha = TestUser.customer("Asha Kumar");
        List<Long> seats = show.seats("B", 2);
        String holdId = hold(asha, show, seats).text("id");

        List<Response> responses = Concurrently.run(10, n -> confirm(asha, holdId));

        // Every tap ends at the hold's one booking (uk_booking_hold), confirmed once
        assertThat(countByStatus(responses)).containsExactly(Map.entry(200, 10L));
        assertThat(responses.stream().map(Response::id).distinct()).hasSize(1);
        assertThat(confirmedBookingSeats(seats)).isEqualTo(2);
        assertThat(seatStatuses(seats)).containsOnly("BOOKED");
    }

    @Test
    void doubleTappedCancelReleasesTheSeatsOnce() {
        TestShow show = newShow();
        TestUser asha = TestUser.customer("Asha Kumar");
        List<Long> seats = show.seats("A", 2);
        long bookingId = book(asha, show, seats).id();

        List<Response> responses = Concurrently.run(10,
                n -> api.post("/api/v1/bookings/" + bookingId + "/cancel", asha));

        assertThat(countByStatus(responses)).containsExactly(Map.entry(200, 10L));
        assertThat(seatStatuses(seats)).containsOnly("AVAILABLE");
        assertThat(book(TestUser.customer("Ravi Shankar"), show, seats).status()).isEqualTo(200);
        assertThat(confirmedBookingSeats(seats)).isEqualTo(2);
    }

    private static Map<Integer, Long> countByStatus(List<Response> responses) {
        return responses.stream().collect(Collectors.groupingBy(Response::status, Collectors.counting()));
    }

    private long confirmedBookingSeats(List<Long> showSeatIds) {
        return jdbc
                .sql("select count(*) from booking.booking_seat where status = 'CONFIRMED' and show_seat_id in (:ids)")
                .param("ids", showSeatIds).query(Long.class).single();
    }
}
