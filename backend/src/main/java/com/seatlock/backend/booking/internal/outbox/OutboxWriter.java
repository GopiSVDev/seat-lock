package com.seatlock.backend.booking.internal.outbox;

import com.seatlock.backend.booking.internal.domain.Booking;
import com.seatlock.backend.booking.internal.domain.BookingSeat;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    // MANDATORY: an event written outside the booking's own transaction could outlive a rollback
    @Transactional(propagation = Propagation.MANDATORY)
    public void bookingConfirmed(Booking booking, List<BookingSeat> seats) {
        BookingConfirmed event = new BookingConfirmed(UUID.randomUUID(), booking.getId(), booking.getCustomerId(),
                booking.getCustomerName(), booking.getShow().getMovieTitle(), booking.getShow().getTheatreName(),
                booking.getShow().getScreenName(), booking.getShow().getStartTime(),
                seats.stream().map(BookingSeat::getSeatLabel).toList(), booking.getTotalAmount());
        jdbcClient.sql("""
                insert into booking.outbox (event_id, topic, event_key, payload, created_at)
                values (:eventId, :topic, :key, cast(:payload as jsonb), :createdAt)
                """).param("eventId", event.eventId()).param("topic", BookingConfirmed.TOPIC)
                .param("key", String.valueOf(booking.getId())).param("payload", jsonMapper.writeValueAsString(event))
                .param("createdAt", Timestamp.from(Instant.now())).update();
    }
}
