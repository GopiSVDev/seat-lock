-- Fixed dataset for the load tests. Same rows every time, so runs are comparable.
-- Run through loadtest/seed.sh, which also writes data/seed.json for the k6 scripts.
-- The app must have started once so Hibernate has created the tables.
--
-- Ids are explicit and follow formulas the k6 scripts rely on (see lib.js):
--   50 movies, 20 theatres in 5 cities, 2 screens each (40 screens of 250 seats: rows A-J x 25,
--   I-J premium), plus screen 41 "Flash Arena" with 1,000 seats (rows A-T x 50) for hot-show.js
--   :shows shows, round-robin over screens 1-40 and movies, 4 hours apart per screen
--   show_seat id = (show - 1) * 250 + k + 1 for seat k (0-249) of the show's screen
--   the first :booked_shows shows are sold out by seeded bookings of 2 seats
--   (booking id = (show - 1) * 125 + pair + 1), so seat maps and GET /bookings read real data

TRUNCATE booking.booking_seat, booking.booking, inventory.show_seat, catalogue.shows, catalogue.seat,
         catalogue.screen, catalogue.theatre, catalogue.movie RESTART IDENTITY CASCADE;

INSERT INTO catalogue.movie (id, title, description, duration_minutes, certification, release_date)
SELECT m, 'Movie ' || m, 'Seeded movie for load tests', 90 + (m * 7) % 91,
       (ARRAY['U', 'UA', 'A'])[1 + m % 3], DATE '2026-01-01' + (m * 5) % 300
FROM generate_series(1, 50) m;

INSERT INTO catalogue.theatre (id, name, city, address)
SELECT t, 'Theatre ' || t, (ARRAY['Chennai', 'Bengaluru', 'Mumbai', 'Delhi', 'Hyderabad'])[1 + t % 5],
       t || ' Main Road'
FROM generate_series(1, 20) t;

INSERT INTO catalogue.screen (id, theatre_id, name)
SELECT s, (s - 1) / 2 + 1, 'Screen ' || ((s - 1) % 2 + 1)
FROM generate_series(1, 40) s
UNION ALL
SELECT 41, 1, 'Flash Arena';

INSERT INTO catalogue.seat (id, screen_id, row_label, seat_number, seat_type)
SELECT (s - 1) * 250 + k + 1, s, chr(65 + k / 25), k % 25 + 1,
       CASE WHEN k / 25 >= 8 THEN 'PREMIUM' ELSE 'REGULAR' END
FROM generate_series(1, 40) s, generate_series(0, 249) k
UNION ALL
SELECT 40 * 250 + k + 1, 41, chr(65 + k / 50), k % 50 + 1,
       CASE WHEN k / 50 >= 16 THEN 'RECLINER' ELSE 'REGULAR' END
FROM generate_series(0, 999) k;

INSERT INTO catalogue.shows (id, movie_id, screen_id, start_time, end_time, language, format)
SELECT j, m.id, (j - 1) % 40 + 1, st, st + m.duration_minutes * interval '1 minute',
       (ARRAY['English', 'Tamil', 'Hindi', 'Telugu'])[1 + j % 4], 'TWO_D'
FROM generate_series(1, :shows) j
JOIN catalogue.movie m ON m.id = (j - 1) % 50 + 1,
LATERAL (SELECT date_trunc('hour', now()) + interval '1 day' + ((j - 1) / 40) * interval '4 hours' AS st) t;

INSERT INTO inventory.show_seat (id, show_id, seat_id, row_label, seat_number, seat_type, status)
SELECT (j - 1) * 250 + k + 1, j, ((j - 1) % 40) * 250 + k + 1, chr(65 + k / 25), k % 25 + 1,
       CASE WHEN k / 25 >= 8 THEN 'PREMIUM' ELSE 'REGULAR' END,
       CASE WHEN j <= :booked_shows THEN 'BOOKED' ELSE 'AVAILABLE' END
FROM generate_series(1, :shows) j, generate_series(0, 249) k
ORDER BY j, k;

INSERT INTO booking.booking (id, show_id, customer_name, status, created_at)
SELECT (j - 1) * 125 + p + 1, j, 'seed-customer-' || ((j - 1) * 125 + p + 1), 'CONFIRMED',
       now() - interval '1 day'
FROM generate_series(1, :booked_shows) j, generate_series(0, 124) p
ORDER BY j, p;

INSERT INTO booking.booking_seat (id, booking_id, show_seat_id, seat_label, status)
SELECT ((j - 1) * 125 + p) * 2 + q + 1, (j - 1) * 125 + p + 1, (j - 1) * 250 + 2 * p + q + 1,
       chr(65 + (2 * p + q) / 25) || ((2 * p + q) % 25 + 1), 'CONFIRMED'
FROM generate_series(1, :booked_shows) j, generate_series(0, 124) p, generate_series(0, 1) q
ORDER BY j, p, q;

-- Explicit ids don't advance the identity sequences; move them past the seeded rows
SELECT setval(pg_get_serial_sequence('catalogue.movie', 'id'), (SELECT max(id) FROM catalogue.movie)),
       setval(pg_get_serial_sequence('catalogue.theatre', 'id'), (SELECT max(id) FROM catalogue.theatre)),
       setval(pg_get_serial_sequence('catalogue.screen', 'id'), (SELECT max(id) FROM catalogue.screen)),
       setval(pg_get_serial_sequence('catalogue.seat', 'id'), (SELECT max(id) FROM catalogue.seat)),
       setval(pg_get_serial_sequence('catalogue.shows', 'id'), (SELECT max(id) FROM catalogue.shows)),
       setval(pg_get_serial_sequence('inventory.show_seat', 'id'), (SELECT max(id) FROM inventory.show_seat)),
       setval(pg_get_serial_sequence('booking.booking', 'id'), (SELECT coalesce(max(id), 1) FROM booking.booking)),
       setval(pg_get_serial_sequence('booking.booking_seat', 'id'), (SELECT coalesce(max(id), 1) FROM booking.booking_seat));

VACUUM ANALYZE catalogue.movie, catalogue.theatre, catalogue.screen, catalogue.seat, catalogue.shows,
               inventory.show_seat, booking.booking, booking.booking_seat;
