-- Owner = the token's sub: an opaque string of up to 255 chars (OIDC), so not uuid.
-- Bookings made before login have no real owner: a placeholder no user's sub can be.
ALTER TABLE booking.booking ADD COLUMN customer_id varchar(255);
UPDATE booking.booking SET customer_id = 'legacy';
ALTER TABLE booking.booking ALTER COLUMN customer_id SET NOT NULL;

-- My bookings, newest first
CREATE INDEX ix_booking_customer_created ON booking.booking (customer_id, created_at DESC, id DESC);
