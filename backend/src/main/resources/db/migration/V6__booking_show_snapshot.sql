-- Show details copied at booking time: my bookings render and filter by show time without catalogue
ALTER TABLE booking.booking
    ADD COLUMN show_start_time     timestamptz(6),
    ADD COLUMN show_language       varchar(255),
    ADD COLUMN show_format         varchar(255),
    ADD COLUMN movie_id            bigint,
    ADD COLUMN movie_title         varchar(255),
    ADD COLUMN movie_poster_url    varchar(500),
    ADD COLUMN movie_certification varchar(255),
    ADD COLUMN theatre_id          bigint,
    ADD COLUMN theatre_name        varchar(255),
    ADD COLUMN screen_name         varchar(255);

-- One-off backfill across schemas; application code never joins them
UPDATE booking.booking b
SET show_start_time     = s.start_time,
    show_language       = s.language,
    show_format         = s.format,
    movie_id            = m.id,
    movie_title         = m.title,
    movie_poster_url    = m.poster_url,
    movie_certification = m.certification,
    theatre_id          = t.id,
    theatre_name        = t.name,
    screen_name         = sc.name
FROM catalogue.shows s
JOIN catalogue.movie m ON m.id = s.movie_id
JOIN catalogue.screen sc ON sc.id = s.screen_id
JOIN catalogue.theatre t ON t.id = sc.theatre_id
WHERE s.id = b.show_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM booking.booking WHERE show_start_time IS NULL) THEN
        RAISE EXCEPTION 'Bookings whose show does not exist; fix them before migrating';
    END IF;
END $$;

ALTER TABLE booking.booking
    ALTER COLUMN show_start_time SET NOT NULL,
    ALTER COLUMN show_language SET NOT NULL,
    ALTER COLUMN show_format SET NOT NULL,
    ALTER COLUMN movie_id SET NOT NULL,
    ALTER COLUMN movie_title SET NOT NULL,
    ALTER COLUMN movie_certification SET NOT NULL,
    ALTER COLUMN theatre_id SET NOT NULL,
    ALTER COLUMN theatre_name SET NOT NULL,
    ALTER COLUMN screen_name SET NOT NULL;

-- Upcoming (forward scan) and past (backward scan) tabs; replaces the booked-at order
DROP INDEX booking.ix_booking_customer_created;
CREATE INDEX ix_booking_customer_show_start ON booking.booking (customer_id, show_start_time, id);
