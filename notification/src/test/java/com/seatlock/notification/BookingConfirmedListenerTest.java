package com.seatlock.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

// The backend's side is played by sending its JSON straight to the topic
@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
@ExtendWith(OutputCaptureExtension.class)
class BookingConfirmedListenerTest {

    private static final String TOPIC = "booking.confirmed";

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void confirmedBookingSendsAnEmail(CapturedOutput output) throws Exception {
        BookingConfirmed event = event(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));

        send(event);
        awaitHandled(event.eventId());

        awaitLogged(output, "Email to customer customer-1: Booking " + event.bookingId()
                + " confirmed: Dune at PVR Grand (Screen 1), 1 Jan 2030, 7:00 PM IST, seats A1, A2, paid INR 300.00");
    }

    @Test
    void sameEventTwiceSendsOneEmail(CapturedOutput output) throws Exception {
        long bookingId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        BookingConfirmed event = event(bookingId);
        // Same key = same partition, so the marker is handled after both copies
        BookingConfirmed marker = event(bookingId);

        send(event);
        send(event);
        send(marker);
        awaitHandled(marker.eventId());

        assertThat(jdbc.sql("select count(*) from notification.notification where event_id = :id")
                .param("id", event.eventId()).query(Long.class).single()).isEqualTo(1);
        awaitLogged(output, "Event " + event.eventId() + " (booking " + bookingId + ") already handled, skipped");
        // Two emails (the event and the marker), not three
        assertThat(output.getAll().split("Email to customer customer-1: Booking " + bookingId + " ", -1)).hasSize(3);
    }

    // The line is written just after the row
    private static void awaitLogged(CapturedOutput output, String line) throws InterruptedException {
        for (int i = 0; i < 40 && !output.getAll().contains(line); i++) {
            Thread.sleep(50);
        }
        assertThat(output.getAll()).contains(line);
    }

    private static BookingConfirmed event(long bookingId) {
        return new BookingConfirmed(UUID.randomUUID(), bookingId, "customer-1", "Asha Kumar", "Dune", "PVR Grand",
                "Screen 1", Instant.parse("2030-01-01T13:30:00Z"), List.of("A1", "A2"), 30000);
    }

    private void send(BookingConfirmed event) throws Exception {
        kafka.send(TOPIC, String.valueOf(event.bookingId()), jsonMapper.writeValueAsString(event)).get();
    }

    private void awaitHandled(UUID eventId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (jdbc.sql("select count(*) from notification.notification where event_id = :id").param("id", eventId)
                    .query(Long.class).single() == 1) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Event " + eventId + " was not handled within 30 s");
    }
}
