package com.seatlock.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

// Idempotent consumer: the event id is stored first, so a repeated delivery sends nothing.
// A failing message is retried 10 times by Spring Kafka, then logged and skipped (no dead-letter topic).
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingConfirmedListener {

    private static final DateTimeFormatter SHOW_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
            .withZone(ZoneId.of("Asia/Kolkata"));

    private final JsonMapper jsonMapper;
    private final NotificationRepository notifications;

    @KafkaListener(topics = "booking.confirmed")
    public void on(String payload) {
        BookingConfirmed event = jsonMapper.readValue(payload, BookingConfirmed.class);
        String message = message(event);
        if (!notifications.saveIfNew(event.eventId(), event.bookingId(), event.customerId(), message)) {
            log.info("Event {} (booking {}) already handled, skipped", event.eventId(), event.bookingId());
            return;
        }
        // Stands in for an email or SMS provider
        log.info("Email to customer {}: {}", event.customerId(), message);
    }

    private static String message(BookingConfirmed event) {
        return "Booking %d confirmed: %s at %s (%s), %s IST, seats %s, paid INR %.2f".formatted(event.bookingId(),
                event.movieTitle(), event.theatreName(), event.screenName(), SHOW_TIME.format(event.showStartTime()),
                String.join(", ", event.seats()), event.totalAmount() / 100.0);
    }
}
