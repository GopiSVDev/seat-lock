#!/usr/bin/env bash
# Fires N concurrent bookings for the same seats, half in ascending and half in descending id order,
# and reports status counts, deadlocks in the backend logs and how many bookings own those seats.
# Usage: scripts/deadlock.sh [requests] [seats] [movieId] [screenId]
set -euo pipefail

cd "$(dirname "$0")/.."

REQUESTS=${1:-50}
SEATS=${2:-20}
MOVIE_ID=${3:-1}
SCREEN_ID=${4:-1}
BASE_URL=${BASE_URL:-http://localhost:8080}

START_TIME=$(date -u -d "+30 days" +%Y-%m-%dT%H:%M:%SZ)

SHOW_ID=$(curl -sf -X POST "$BASE_URL/shows" -H 'Content-Type: application/json' \
  -d "{\"movieId\":$MOVIE_ID,\"screenId\":$SCREEN_ID,\"startTime\":\"$START_TIME\",\"language\":\"English\",\"format\":\"TWO_D\"}" \
  | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

SEAT_IDS=$(curl -sf "$BASE_URL/shows/$SHOW_ID/seats" \
  | grep -o '"id":[0-9]*' | cut -d: -f2 | sort -n | head -"$SEATS")

ASC=$(echo "$SEAT_IDS" | paste -sd,)
DESC=$(echo "$SEAT_IDS" | sort -rn | paste -sd,)

echo "Show $SHOW_ID, seats [$ASC], firing $REQUESTS concurrent bookings (odd asc, even desc)..."
echo

SINCE=$(date -u +%Y-%m-%dT%H:%M:%SZ)

book() {
  local ids=$ASC
  (( $1 % 2 == 0 )) && ids=$DESC
  curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE_URL/bookings" -H 'Content-Type: application/json' \
    -d "{\"showId\":$SHOW_ID,\"customerName\":\"customer-$1\",\"showSeatIds\":[$ids]}"
}
export -f book
export ASC DESC SHOW_ID BASE_URL

echo "HTTP status counts:"
seq 1 "$REQUESTS" | xargs -P "$REQUESTS" -I{} bash -c 'book {}' | sort | uniq -c
echo

echo "Deadlocks in backend logs: $(docker compose logs --since "$SINCE" backend | grep -c 'deadlock detected' || true)"
echo

echo "Bookings that own these seats (should be exactly 1 booking with $SEATS seats):"
docker compose exec -T postgres sh -c "psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -c \"
  select b.id as booking_id, b.customer_name, count(*) as seats
  from booking.booking_seat bs join booking.booking b on b.id = bs.booking_id
  where bs.show_seat_id in ($ASC) group by b.id, b.customer_name order by b.id;\""
