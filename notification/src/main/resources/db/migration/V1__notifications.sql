-- One row per handled BookingConfirmed event; the unique event_id makes repeated deliveries no-ops
CREATE TABLE notification.notification (
    id          bigint GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_notification PRIMARY KEY,
    event_id    uuid         NOT NULL CONSTRAINT uk_notification_event UNIQUE,
    booking_id  bigint       NOT NULL,
    customer_id varchar(255) NOT NULL,
    message     text         NOT NULL,
    sent_at     timestamptz(6) NOT NULL
);
