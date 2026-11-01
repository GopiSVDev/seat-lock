package com.seatlock.backend.booking.internal.repository;

import com.seatlock.backend.booking.internal.domain.BookingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

// Plain JDBC for the booking hot path: the booking and all its seats in one statement (one round trip).
// Ids come from the identity columns; reads stay on JPA (BookingRepository, BookingSeatRepository).
@Repository
@RequiredArgsConstructor
public class BookingWriteRepository {

    private final JdbcClient jdbcClient;

    // Returns the new booking's id. Seats are inserted in the given order.
    public Long insert(Long showId, String customerName, BookingStatus status, Instant createdAt,
                       List<Long> showSeatIds, List<String> seatLabels) {
        return jdbcClient.sql("""
                        with b as (
                            insert into booking.booking (show_id, customer_name, status, created_at)
                            values (:showId, :customerName, :status, :createdAt)
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
                .param("customerName", customerName)
                .param("status", status.name())
                .param("createdAt", Timestamp.from(createdAt))
                .param("showSeatIds", showSeatIds.toArray(Long[]::new))
                .param("seatLabels", seatLabels.toArray(String[]::new))
                .query(Long.class)
                .single();
    }
}
