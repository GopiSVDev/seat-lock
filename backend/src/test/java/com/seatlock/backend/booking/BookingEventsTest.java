package com.seatlock.backend.booking;

import com.github.dockerjava.api.DockerClient;
import com.seatlock.backend.support.Api.Response;
import com.seatlock.backend.support.IntegrationTest;
import com.seatlock.backend.support.TestShow;
import com.seatlock.backend.support.TestUser;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

// BookingConfirmed goes out through the outbox: written with the booking, sent to Kafka by the relay
class BookingEventsTest extends IntegrationTest {

    private static final String TOPIC = "booking.confirmed";

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void confirmedBookingPublishesOneEvent() {
        TestShow show = newShow();
        TestUser buyer = TestUser.customer("Asha Kumar");
        long bookingId = confirm(buyer, hold(buyer, show, show.seats("A", 2)).text("id")).id();
        // Browser and webhook both report the payment: only the first confirms
        assertThat(pay(buyer, bookingId).status()).isEqualTo(200);

        JsonNode event = awaitEvent(bookingId);

        assertThat(event.get("eventId").asString()).isNotBlank();
        assertThat(event.get("customerName").asString()).isEqualTo("Asha Kumar");
        assertThat(event.get("seats").size()).isEqualTo(2);
        assertThat(event.get("totalAmount").asLong()).isEqualTo(2 * REGULAR_PRICE);
        assertThat(outboxRows(bookingId)).isEqualTo(1);
    }

    @Test
    void kafkaDownDoesNotStopConfirmingAndTheEventFollowsLater() throws Exception {
        TestShow show = newShow();
        TestUser buyer = TestUser.customer("Ravi Shankar");
        String holdId = hold(buyer, show, show.seats("A", 1)).text("id");
        DockerClient docker = kafka.getDockerClient();

        docker.pauseContainerCmd(kafka.getContainerId()).exec();
        long bookingId;
        try {
            long start = System.nanoTime();
            Response confirmed = confirm(buyer, holdId);

            assertThat(confirmed.status()).isEqualTo(200);
            assertThat(confirmed.text("status")).isEqualTo("CONFIRMED");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
            bookingId = confirmed.id();
            // A few relay runs come and go meanwhile
            Thread.sleep(1500);
            assertThat(published(bookingId)).isFalse();
        } finally {
            docker.unpauseContainerCmd(kafka.getContainerId()).exec();
        }

        assertThat(awaitEvent(bookingId).get("bookingId").asLong()).isEqualTo(bookingId);
    }

    private JsonNode awaitEvent(long bookingId) {
        Map<String, Object> config = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(), ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest", ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.key().equals(String.valueOf(bookingId))) {
                        return jsonMapper.readTree(record.value());
                    }
                }
            }
        }
        return fail("No BookingConfirmed event for booking " + bookingId + " within 30 s");
    }

    private long outboxRows(long bookingId) {
        return jdbc.sql("select count(*) from booking.outbox where event_key = :key")
                .param("key", String.valueOf(bookingId)).query(Long.class).single();
    }

    private boolean published(long bookingId) {
        return jdbc.sql("select published_at is not null from booking.outbox where event_key = :key")
                .param("key", String.valueOf(bookingId)).query(Boolean.class).single();
    }
}
