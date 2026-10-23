#!/usr/bin/env bash
# Seeds the fixed load-test dataset (seed.sql) and writes loadtest/data/seed.json for the k6 scripts.
# Empties all tables first. Re-run before a booking test once its seats run out, and after show-create.
#
#   loadtest/seed.sh                    # 6,000 shows (1.5M show seats), the first 200 sold out
#   SHOWS=2000 loadtest/seed.sh
#   VM=local loadtest/seed.sh           # the local docker compose stack instead of the LXD VM
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
shows="${SHOWS:-6000}"
booked_shows="${BOOKED_SHOWS:-200}"
vm="${VM:-seat-lock}"
# Credentials come from the container's own environment
psql_cmd='exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"'
if [ "$vm" = local ]; then
  psql=(docker compose -f "$here/../docker-compose.yml" exec -T postgres sh -c "$psql_cmd" psql)
else
  psql=(sudo lxc exec "$vm" -- docker exec -i seat-lock-db sh -c "$psql_cmd" psql)
fi

echo "Seeding $shows shows ($booked_shows sold out) ..."
start=$(date +%s)
"${psql[@]}" -v ON_ERROR_STOP=1 -q -v shows="$shows" -v booked_shows="$booked_shows" < "$here/seed.sql" >/dev/null
echo "Seeded in $(( $(date +%s) - start ))s"

mkdir -p "$here/data"
cat > "$here/data/seed.json" <<EOF
{
  "shows": $shows,
  "bookedShows": $booked_shows,
  "seatsPerShow": 250,
  "seededBookings": $((booked_shows * 125)),
  "movies": 50,
  "theatres": 20,
  "screens": 40,
  "flashScreenId": 41
}
EOF

"${psql[@]}" -At -c "SELECT 'Show seats: ' || count(*) || ', table + indexes: ' || pg_size_pretty(pg_total_relation_size('inventory.show_seat')) FROM inventory.show_seat"
echo "Bookable pairs: $(( (shows - booked_shows) * 125 ))"
