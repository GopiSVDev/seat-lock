#!/usr/bin/env bash
# Checks that seats and bookings agree after a run. Every count must be 0.
#
#   loadtest/check.sh
#   VM=local loadtest/check.sh
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
vm="${VM:-seat-lock}"
psql_cmd='exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"'
if [ "$vm" = local ]; then
  psql=(docker compose -f "$here/../docker-compose.yml" exec -T postgres sh -c "$psql_cmd" psql)
else
  psql=(sudo lxc exec "$vm" -- docker exec -i seat-lock-db sh -c "$psql_cmd" psql)
fi

"${psql[@]}" -c "
SELECT 'seats in more than one confirmed booking' AS check, count(*) FROM (
  SELECT show_seat_id FROM booking.booking_seat WHERE status = 'CONFIRMED'
  GROUP BY show_seat_id HAVING count(*) > 1) d
UNION ALL
SELECT 'BOOKED seats without a confirmed booking', count(*) FROM inventory.show_seat ss
WHERE ss.status = 'BOOKED' AND NOT EXISTS (
  SELECT 1 FROM booking.booking_seat bs WHERE bs.show_seat_id = ss.id AND bs.status = 'CONFIRMED')
UNION ALL
SELECT 'confirmed booking seats on AVAILABLE seats', count(*) FROM booking.booking_seat bs
JOIN inventory.show_seat ss ON ss.id = bs.show_seat_id
WHERE bs.status = 'CONFIRMED' AND ss.status = 'AVAILABLE'"
