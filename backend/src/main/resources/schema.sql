CREATE UNIQUE INDEX IF NOT EXISTS uk_booking_seat_active_show_seat
    ON booking.booking_seat (show_seat_id) WHERE status = 'CONFIRMED';
