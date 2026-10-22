#!/usr/bin/env bash
# Fires N concurrent bookings for the same seat and reports how many succeeded.
# Usage: scripts/double-booking.sh [requests] [movieId] [screenId]
set -euo pipefail

cd "$(dirname "$0")/.."

REQUESTS=${1:-50}
MOVIE_ID=${2:-1}
SCREEN_ID=${3:-1}
BASE_URL=${BASE_URL:-http://localhost:8080}

START_TIME=$(date -u -d "+30 days" +%Y-%m-%dT%H:%M:%SZ)

SHOW_ID=$(curl -sf -X POST "$BASE_URL/shows" -H 'Content-Type: application/json' \
  -d "{\"movieId\":$MOVIE_ID,\"screenId\":$SCREEN_ID,\"startTime\":\"$START_TIME\",\"language\":\"English\",\"format\":\"TWO_D\"}" \
  | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

SEAT_ID=$(curl -sf "$BASE_URL/shows/$SHOW_ID/seats" \
  | grep -o '"showSeatId":[0-9]*' | head -1 | cut -d: -f2)

echo "Show $SHOW_ID, showSeatId $SEAT_ID, firing $REQUESTS concurrent bookings..."
echo

echo "HTTP status counts:"
seq 1 "$REQUESTS" | xargs -P "$REQUESTS" -I{} curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "$BASE_URL/bookings" -H 'Content-Type: application/json' \
  -d "{\"showId\":$SHOW_ID,\"customerName\":\"customer-{}\",\"showSeatIds\":[$SEAT_ID]}" \
  | sort | uniq -c
echo

echo "Bookings that own showSeatId $SEAT_ID (should be exactly 1):"
docker compose exec -T postgres sh -c "psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -c \"
  select b.id as booking_id, b.customer_name, bs.seat_label
  from booking.booking_seat bs join booking.booking b on b.id = bs.booking_id
  where bs.show_seat_id = $SEAT_ID order by b.id;\""
