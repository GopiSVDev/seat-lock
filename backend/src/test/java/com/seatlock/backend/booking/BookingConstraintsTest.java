package com.seatlock.backend.booking;

import com.seatlock.backend.support.IntegrationTest;
import com.seatlock.backend.support.TestShow;
import com.seatlock.backend.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The database refuses bad states even if application code has a bug
class BookingConstraintsTest extends IntegrationTest {

    private final TestUser asha = TestUser.customer("Asha Kumar");

    @Test
    void cancelledBookingNeedsACancelTime() {
        TestShow show = newShow();
        long bookingId = book(asha, show, show.seats("A", 1)).id();

        assertThatThrownBy(() -> jdbc.sql("update booking.booking set status = 'CANCELLED' where id = :id")
                .param("id", bookingId).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_booking_cancelled_at");
    }

    @Test
    void unknownStatusIsRejected() {
        TestShow show = newShow();
        long bookingId = book(asha, show, show.seats("A", 1)).id();

        assertThatThrownBy(() -> jdbc.sql("update booking.booking set status = 'LOST' where id = :id")
                .param("id", bookingId).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_booking_status");
    }

    @Test
    void aSeatIsInAtMostOneConfirmedBooking() {
        TestShow show = newShow();
        long first = book(asha, show, show.seats("A", 1)).id();
        long second = book(asha, show, show.seats("B", 1)).id();

        // Point the second booking's seat at the first booking's seat
        assertThatThrownBy(() -> jdbc.sql("""
                        update booking.booking_seat
                        set show_seat_id = (select show_seat_id from booking.booking_seat where booking_id = :first)
                        where booking_id = :second
                        """)
                .param("first", first).param("second", second).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_booking_seat_active_show_seat");
    }
}
