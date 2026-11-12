package com.seatlock.backend.booking.internal.repository;

import com.seatlock.backend.booking.internal.domain.BookingStatus;
import com.seatlock.backend.booking.internal.domain.ShowSnapshot;
import com.seatlock.backend.inventory.BookedSeat;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

// Booking and all its seats in one statement; reads stay on JPA.
@Repository
@RequiredArgsConstructor
public class BookingWriteRepository {

    private final JdbcClient jdbcClient;

    // Starts unpaid; the seats are booked in inventory only once the payment is confirmed
    public Long insertPending(Long showId, ShowSnapshot show, String customerId, String customerName, String holdId,
            Instant createdAt, Instant paymentExpiresAt, long totalAmount, List<BookedSeat> seats) {
        return jdbcClient.sql("""
                with b as (
                    insert into booking.booking (show_id, customer_id, customer_name, status, created_at, total_amount,
                        hold_id, payment_expires_at,
                        show_start_time, show_language, show_format, movie_id, movie_title,
                        movie_poster_url, movie_certification, theatre_id, theatre_name, screen_name)
                    values (:showId, :customerId, :customerName, 'PENDING_PAYMENT', :createdAt, :totalAmount,
                        :holdId, :paymentExpiresAt,
                        :startTime, :language, :format, :movieId, :movieTitle,
                        :moviePosterUrl, :movieCertification, :theatreId, :theatreName, :screenName)
                    returning id
                ), s as (
                    insert into booking.booking_seat (booking_id, show_seat_id, seat_label, price, status)
                    select b.id, seat.show_seat_id, seat.label, seat.price, 'PENDING_PAYMENT'
                    from b, unnest(cast(:showSeatIds as bigint[]), cast(:seatLabels as text[]),
                            cast(:seatPrices as bigint[]))
                            with ordinality as seat(show_seat_id, label, price, position)
                    order by seat.position
                )
                select id from b
                """).param("showId", showId).param("customerId", customerId).param("customerName", customerName)
                .param("createdAt", Timestamp.from(createdAt)).param("holdId", holdId)
                .param("paymentExpiresAt", Timestamp.from(paymentExpiresAt)).param("totalAmount", totalAmount)
                .param("startTime", Timestamp.from(show.getStartTime())).param("language", show.getLanguage())
                .param("format", show.getFormat()).param("movieId", show.getMovieId())
                .param("movieTitle", show.getMovieTitle()).param("moviePosterUrl", show.getMoviePosterUrl())
                .param("movieCertification", show.getMovieCertification()).param("theatreId", show.getTheatreId())
                .param("theatreName", show.getTheatreName()).param("screenName", show.getScreenName())
                .param("showSeatIds", seats.stream().map(BookedSeat::showSeatId).toArray(Long[]::new))
                .param("seatLabels", seats.stream().map(BookedSeat::label).toArray(String[]::new))
                .param("seatPrices", seats.stream().map(BookedSeat::price).toArray(Long[]::new)).query(Long.class)
                .single();
    }

    // Guarded like booking: of two concurrent cancels only one matches, so seats are released once
    public boolean cancel(Long id, String customerId, Instant now) {
        return jdbcClient.sql("""
                update booking.booking
                set status = 'CANCELLED', cancelled_at = :now
                where id = :id
                  and customer_id = :customerId
                  and status = 'CONFIRMED'
                  and show_start_time > :now
                """).param("id", id).param("customerId", customerId).param("now", Timestamp.from(now)).update() == 1;
    }

    public void cancelSeats(Long bookingId) {
        setSeatStatus(bookingId, BookingStatus.CANCELLED);
    }

    // Only the first payment id sticks; a later different one is a mismatch for the
    // caller to handle
    public void setPayment(Long id, Long paymentId) {
        jdbcClient.sql("update booking.booking set payment_id = :paymentId where id = :id and payment_id is null")
                .param("id", id).param("paymentId", paymentId).update();
    }

    // Guarded like cancel: of the browser and the webhook confirming together, only
    // one gets the row.
    // EXPIRED too: a payment that arrives late still gets its seats if the hold happens to be alive.
    public boolean settle(Long id, BookingStatus to) {
        boolean changed = jdbcClient.sql("""
                update booking.booking set status = :to
                where id = :id and status in ('PENDING_PAYMENT', 'EXPIRED')
                """).param("id", id).param("to", to.name()).update() == 1;
        if (changed) {
            setSeatStatus(id, to);
        }
        return changed;
    }

    // The sweep job: unpaid past the deadline. Seats were never booked, so nothing
    // is released.
    public int expireOverdue(Instant now) {
        return jdbcClient.sql("""
                with expired as (
                    update booking.booking set status = 'EXPIRED'
                    where status = 'PENDING_PAYMENT' and payment_expires_at < :now
                    returning id
                ), seats as (
                    update booking.booking_seat set status = 'EXPIRED'
                    where booking_id in (select id from expired)
                )
                select count(*) from expired
                """).param("now", Timestamp.from(now)).query(Integer.class).single();
    }

    private void setSeatStatus(Long bookingId, BookingStatus status) {
        jdbcClient.sql("update booking.booking_seat set status = :status where booking_id = :bookingId")
                .param("bookingId", bookingId).param("status", status.name()).update();
    }
}
