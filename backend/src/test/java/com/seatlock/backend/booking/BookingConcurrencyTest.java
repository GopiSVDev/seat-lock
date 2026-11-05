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

import static org.assertj.core.api.Assertions.assertThat;

// The core promise: under concurrent requests a seat is never sold twice
class BookingConcurrencyTest extends IntegrationTest {

    @Test
    void manyBuyersRacingForTheSameSeatsOnlyOneWins() {
        TestShow show = newShow();
        List<Long> seats = show.seats("A", 2);

        List<Response> responses = Concurrently.run(20,
                n -> book(TestUser.customer("Buyer " + n), show, seats));

        assertThat(countByStatus(responses)).containsExactlyInAnyOrderEntriesOf(Map.of(201, 1L, 409, 19L));
        assertThat(confirmedBookingSeats(seats)).isEqualTo(2);
    }

    @Test
    void bestAvailableUnderLoadSellsEachSeatOnce() {
        TestShow show = newShow();

        List<Response> responses = Concurrently.run(30, n -> api.post("/bookings/best-available",
                TestUser.customer("Buyer " + n), Map.of("showId", show.showId(), "quantity", 1, "seatType", "REGULAR")));

        // SKIP LOCKED may answer 409 while the last seats are locked by others, so the exact split can vary;
        // what must hold: only 201/409, at most 20 winners, each with a different seat, and the DB agrees
        Map<Integer, Long> statuses = countByStatus(responses);
        assertThat(statuses.keySet()).isSubsetOf(201, 409);
        long winners = statuses.getOrDefault(201, 0L);
        assertThat(winners).isBetween(1L, 20L);

        Set<Long> sold = new HashSet<>();
        responses.stream().filter(r -> r.status() == 201)
                .forEach(r -> sold.addAll(ids(r.body().get("seats"), "showSeatId")));
        assertThat(sold).hasSize((int) winners);
        assertThat(jdbc.sql("select count(*) from inventory.show_seat where show_id = :id and status = 'BOOKED'")
                .param("id", show.showId()).query(Long.class).single()).isEqualTo(winners);
    }

    @Test
    void doubleTappedCancelReleasesTheSeatsOnce() {
        TestShow show = newShow();
        TestUser asha = TestUser.customer("Asha Kumar");
        List<Long> seats = show.seats("A", 2);
        long bookingId = book(asha, show, seats).id();

        List<Response> responses = Concurrently.run(10, n -> api.post("/bookings/" + bookingId + "/cancel", asha));

        assertThat(countByStatus(responses)).containsExactly(Map.entry(200, 10L));
        assertThat(seatStatuses(seats)).containsOnly("AVAILABLE");
        assertThat(book(TestUser.customer("Ravi Shankar"), show, seats).status()).isEqualTo(201);
        assertThat(confirmedBookingSeats(seats)).isEqualTo(2);
    }

    private static Map<Integer, Long> countByStatus(List<Response> responses) {
        return responses.stream().collect(Collectors.groupingBy(Response::status, Collectors.counting()));
    }

    private long confirmedBookingSeats(List<Long> showSeatIds) {
        return jdbc.sql("select count(*) from booking.booking_seat where status = 'CONFIRMED' and show_seat_id in (:ids)")
                .param("ids", showSeatIds)
                .query(Long.class)
                .single();
    }
}
