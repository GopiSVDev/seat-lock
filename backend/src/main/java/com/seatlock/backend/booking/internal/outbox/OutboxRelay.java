package com.seatlock.backend.booking.internal.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

// Sends unsent outbox rows to Kafka, oldest first. At least once: a crash after sending but before the
// commit sends the row again next time, so consumers must ignore repeats (by eventId).
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH = 100;

    private final JdbcClient jdbcClient;
    private final KafkaTemplate<String, String> kafka;

    private record Row(long id, String topic, String eventKey, String payload) {
    }

    // SKIP LOCKED: with several backend instances each row is sent by one of them.
    // Kafka down: nothing is marked sent, so the rows wait for the next run.
    @Scheduled(fixedDelayString = "${outbox.relay-interval}")
    @Transactional
    public void publish() {
        List<Row> rows = jdbcClient.sql("""
                select id, topic, event_key, payload::text as payload from booking.outbox
                where published_at is null
                order by id
                limit :batch
                for update skip locked
                """).param("batch", BATCH).query(Row.class).list();
        if (rows.isEmpty()) {
            return;
        }
        try {
            for (Row row : rows) {
                kafka.send(row.topic(), row.eventKey(), row.payload()).get(15, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Outbox: Kafka send failed, {} events wait for the next run: {}", rows.size(), e.toString());
            return;
        }
        jdbcClient.sql("update booking.outbox set published_at = :now where id in (:ids)")
                .param("now", Timestamp.from(Instant.now())).param("ids", rows.stream().map(Row::id).toList()).update();
        log.info("Outbox: published {} events (ids {}..{})", rows.size(), rows.getFirst().id(), rows.getLast().id());
    }
}
