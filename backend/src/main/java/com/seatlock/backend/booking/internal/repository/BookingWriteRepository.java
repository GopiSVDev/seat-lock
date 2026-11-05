package com.seatlock.backend.booking.internal.repository;

import com.seatlock.backend.booking.internal.domain.BookingStatus;
import com.seatlock.backend.booking.internal.domain.ShowSnapshot;
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

    public Long insert(Long showId, ShowSnapshot show, String customerId, String customerName, BookingStatus status,
                       Instant createdAt, List<Long> showSeatIds, List<String> seatLabels) {
        return jdbcClient.sql("""
                        with b as (
                            insert into booking.booking (show_id, customer_id, customer_name, status, created_at,
                                show_start_time, show_language, show_format, movie_id, movie_title,
                                movie_poster_url, movie_certification, theatre_id, theatre_name, screen_name)
                            values (:showId, :customerId, :customerName, :status, :createdAt,
                                :startTime, :language, :format, :movieId, :movieTitle,
                                :moviePosterUrl, :movieCertification, :theatreId, :theatreName, :screenName)
                            returning id
                        ), s as (
                            insert into booking.booking_seat (booking_id, show_seat_id, seat_label, status)
                            select b.id, seat.show_seat_id, seat.label, :status
                            from b, unnest(cast(:showSeatIds as bigint[]), cast(:seatLabels as text[]))
                                    with ordinality as seat(show_seat_id, label, position)
                            order by seat.position
                        )
                        select id from b
                        """)
                .param("showId", showId)
                .param("customerId", customerId)
                .param("customerName", customerName)
                .param("status", status.name())
                .param("createdAt", Timestamp.from(createdAt))
                .param("startTime", Timestamp.from(show.getStartTime()))
                .param("language", show.getLanguage())
                .param("format", show.getFormat())
                .param("movieId", show.getMovieId())
                .param("movieTitle", show.getMovieTitle())
                .param("moviePosterUrl", show.getMoviePosterUrl())
                .param("movieCertification", show.getMovieCertification())
                .param("theatreId", show.getTheatreId())
                .param("theatreName", show.getTheatreName())
                .param("screenName", show.getScreenName())
                .param("showSeatIds", showSeatIds.toArray(Long[]::new))
                .param("seatLabels", seatLabels.toArray(String[]::new))
                .query(Long.class)
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
                        """)
                .param("id", id)
                .param("customerId", customerId)
                .param("now", Timestamp.from(now))
                .update() == 1;
    }

    public void cancelSeats(Long bookingId) {
        jdbcClient.sql("update booking.booking_seat set status = 'CANCELLED' where booking_id = :bookingId")
                .param("bookingId", bookingId)
                .update();
    }
}
