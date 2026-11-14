package com.seatlock.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationRepository {

    private final JdbcClient jdbcClient;

    // false: this event was handled before (Kafka delivers at least once)
    public boolean saveIfNew(UUID eventId, Long bookingId, String customerId, String message) {
        return jdbcClient.sql("""
                insert into notification.notification (event_id, booking_id, customer_id, message, sent_at)
                values (:eventId, :bookingId, :customerId, :message, :sentAt)
                on conflict (event_id) do nothing
                """).param("eventId", eventId).param("bookingId", bookingId).param("customerId", customerId)
                .param("message", message).param("sentAt", Timestamp.from(Instant.now())).update() == 1;
    }
}
