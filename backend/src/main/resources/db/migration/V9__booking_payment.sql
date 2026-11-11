-- A booking now starts unpaid and its seats are booked only after payment:
--   PENDING_PAYMENT -> CONFIRMED (paid while the hold was still ours)
--                   -> EXPIRED   (deadline passed unpaid; set by the sweep job)
--                   -> FAILED    (paid, but the seats were lost; the payment is refunded)
--   CONFIRMED -> CANCELLED
ALTER TABLE booking.booking
    DROP CONSTRAINT ck_booking_status,
    ADD CONSTRAINT ck_booking_status
        CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'FAILED', 'CANCELLED')),
    -- One booking per hold: a repeated checkout finds it instead of extending the hold again
    ADD COLUMN hold_id varchar(64),
    ADD CONSTRAINT uk_booking_hold UNIQUE (hold_id),
    -- The payment service's id; no foreign key, it lives in another database
    ADD COLUMN payment_id bigint,
    ADD COLUMN payment_expires_at timestamptz(6),
    ADD CONSTRAINT ck_booking_payment_expires_at
        CHECK (status <> 'PENDING_PAYMENT' OR payment_expires_at IS NOT NULL);

-- Seat rows follow their booking; only CONFIRMED ones count in uk_booking_seat_active_show_seat
ALTER TABLE booking.booking_seat
    DROP CONSTRAINT ck_booking_seat_status,
    ADD CONSTRAINT ck_booking_seat_status
        CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'FAILED', 'CANCELLED'));

-- The sweep job reads only unpaid bookings, never the confirmed history
CREATE INDEX ix_booking_payment_due ON booking.booking (payment_expires_at)
    WHERE status = 'PENDING_PAYMENT';
