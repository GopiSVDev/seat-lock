-- Transactional outbox: events are written in the same transaction as the change they announce,
-- then a relay job sends them to Kafka and sets published_at. Either both commit or neither does.
CREATE TABLE booking.outbox (
    id           bigint GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_outbox PRIMARY KEY,
    event_id     uuid         NOT NULL CONSTRAINT uk_outbox_event UNIQUE,
    topic        varchar(100) NOT NULL,
    event_key    varchar(100) NOT NULL,
    payload      jsonb        NOT NULL,
    created_at   timestamptz(6) NOT NULL,
    published_at timestamptz(6)
);

-- The relay only ever reads unsent rows, oldest first
CREATE INDEX ix_outbox_unpublished ON booking.outbox (id) WHERE published_at IS NULL;
