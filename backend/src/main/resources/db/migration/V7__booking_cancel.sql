ALTER TABLE booking.booking
    DROP CONSTRAINT ck_booking_status,
    ADD CONSTRAINT ck_booking_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    ADD COLUMN cancelled_at timestamptz(6),
    ADD CONSTRAINT ck_booking_cancelled_at CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL));

-- Cancelled seat rows drop out of uk_booking_seat_active_show_seat, so the seat can be sold again
ALTER TABLE booking.booking_seat
    DROP CONSTRAINT ck_booking_seat_status,
    ADD CONSTRAINT ck_booking_seat_status CHECK (status IN ('CONFIRMED', 'CANCELLED'));
