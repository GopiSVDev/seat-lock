// Flash sale event: advance booking opens for many 1,000-seat shows at once (like a blockbuster release
// across theatres) and users rush in. Not a capacity search: one run, judged on correctness (no seat
// sold twice), errors and how latency behaves during the rush.
//   node loadtest/find-capacity.mjs flash-sale "note"          (warms up the JVM first)
//
// Single run by hand:
//   RATE=200 RAMP=10s DURATION=1m k6 run loadtest/flash-sale.js
//
// setup() schedules FLASH_SHOWS shows on the Flash Arena screen through the API (find-capacity.mjs sizes
// them so seats outlast the event; default 1, the single show of the earlier runs). New users arrive from
// 0 up to RATE users/s over RAMP, then RATE users/s until DURATION. Each user, like a real buyer:
//   1. wants one show, picked by popularity (Zipf: a few prime-time shows get most users); reads its seat
//      map (layout from the CDN edge once per VU and show, availability from the app every time); if it
//      shows no free seats, looks at another show, up to SHOWS_TO_TRY shows, then leaves (sold out)
//   2. has a group size (1-4) and a preferred spot (preferredSpot: most want the middle-back rows near the
//      centre, some recliners, some an aisle, a few the front), thinks for 0-THINK_MAX_S seconds, then
//      SEAT_MODE=best (default, since 2026-11-03): asks POST /bookings/best-available for that many
//        seats of the type at their spot (RECLINER or REGULAR); the server picks the adjacent block
//      SEAT_MODE=pick (the runs before): picks the seats on the map, one of the TOP_CHOICES free blocks
//        closest to their spot, and books those exact seats with POST /bookings
//   3. on 409 starts again at 1 with the same group size and spot, up to MAX_ATTEMPTS bookings; in best
//      mode the attempts alternate seat types (preferred, other, preferred, ...), like a person told
//      "no 2 recliners together" who then tries regular seats
// In pick mode, the longer users think, the staler their seat map and the more 409s, as in a real sale.
// Before 2026-11-02 every user took the free block closest to the centre of a random row, and the group
// size was re-drawn on every attempt: far more alike than real buyers, so more 409s.
//
// Reported: users who booked / saw sold out / gave up, booking attempts, sold out after (last
// successful booking, from the sale opening; only if every seat sold), seats left unsold and how many of
// them are stranded singles (no free neighbour in the row: a group can't use them), and the oversell
// check: seats in 201 responses (seats_booked) must equal unavailable seats afterwards (seats_sold).
//
// Env: RATE (peak new users/s, default 50), RAMP (default 10s), DURATION (default 1m), FLASH_SHOWS
// (default 1), SHOWS_TO_TRY (default 3), SEAT_MODE (best | pick, default best), THINK_MAX_S (default 3), MAX_ATTEMPTS
// (default 3), TOP_CHOICES (default 3, pick mode), BASE_URL (default http://10.24.21.250:8080, the LXD VM)

import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { availability, baseOptions, layout, BASE_URL, JSON_HEADERS, RATE } from './lib.js';

const seed = JSON.parse(open('./data/seed.json'));
const THINK_MAX_S = Number(__ENV.THINK_MAX_S ?? 3);
const MAX_ATTEMPTS = Number(__ENV.MAX_ATTEMPTS || 3);
const TOP_CHOICES = Number(__ENV.TOP_CHOICES || 3);
const RAMP = __ENV.RAMP || '10s';
const DURATION = __ENV.DURATION || '1m';
const FLASH_SHOWS = Number(__ENV.FLASH_SHOWS || 1);
const SHOWS_TO_TRY = Number(__ENV.SHOWS_TO_TRY || 3);
const SEAT_MODE = __ENV.SEAT_MODE || 'best';
if (!['best', 'pick'].includes(SEAT_MODE)) throw new Error(`SEAT_MODE must be best or pick, got ${SEAT_MODE}`);

const usersBooked = new Counter('users_booked');
const usersSoldOut = new Counter('users_sold_out');
const usersGaveUp = new Counter('users_gave_up');
const attempts = new Counter('booking_attempts');
const seatsBooked = new Counter('seats_booked');
const seatsSold = new Counter('seats_sold');
const seatsTotal = new Counter('seats_total');
const seatsStranded = new Counter('seats_stranded');
const soldAfter = new Trend('booked_after_open', true);

const toSeconds = (d) => (d.endsWith('m') ? Number(d.slice(0, -1)) * 60 : Number(d.replace(/s$/, '')));

export const options = baseOptions('flash_sale', ['layout', 'availability', 'book:201:409']);

// Per VU: layout rows by show id, each fetched once (immutable)
const layoutRows = {};
Object.assign(options.scenarios.flash_sale, {
  executor: 'ramping-arrival-rate',
  startRate: 0,
  timeUnit: '1s',
  stages: [
    { target: RATE, duration: RAMP },
    { target: RATE, duration: `${Math.max(toSeconds(DURATION) - toSeconds(RAMP), 1)}s` },
  ],
  // A user session lasts a few seconds (think time per attempt + requests), so many users are active at
  // once. Sized for the average session with retries (up to MAX_ATTEMPTS thinks of THINK_MAX_S / 2): VUs
  // created mid-test start late and count as dropped iterations although the app kept up
  preAllocatedVUs: Math.ceil(RATE * ((THINK_MAX_S / 2) * MAX_ATTEMPTS + 1)) + 10,
  maxVUs: Math.ceil(RATE * (THINK_MAX_S + 1) * 4) + 50,
});

// Scheduling a show inserts its 1,000 show seats one by one (~0.3 s), so many shows take a while
options.setupTimeout = '5m';

export function setup() {
  const showIds = [];
  // 10 at a time: the VM has one core, more in parallel only queues
  for (let first = 0; first < FLASH_SHOWS; first += 10) {
    const batch = [];
    for (let i = first; i < Math.min(first + 10, FLASH_SHOWS); i++) {
      const startTime = new Date(Date.now() + (60 + Math.random() * 300) * 86_400_000).toISOString();
      const body = { movieId: 1, screenId: seed.flashScreenId, startTime, language: 'English', format: 'IMAX_2D' };
      batch.push(['POST', `${BASE_URL}/shows`, JSON.stringify(body),
        { headers: JSON_HEADERS, responseType: 'text', tags: { name: 'setup' } }]);
    }
    for (const show of http.batch(batch)) {
      if (show.status !== 201) exec.test.abort(`Could not create a flash sale show: ${show.status} ${show.body}`);
      showIds.push(show.json('id'));
    }
  }
  return { showIds, openedAt: Date.now() };
}

// The shows this user would go to, most wanted first: Zipf by position in showIds, no repeats.
// (n + 1)^u so the last show can be picked too; lib.js zipfIndex never picks it, which only matters for few items
function showsToTry(showIds) {
  const n = showIds.length;
  const wanted = [];
  while (wanted.length < Math.min(SHOWS_TO_TRY, n)) {
    const showId = showIds[Math.min(Math.floor(Math.pow(n + 1, Math.random())), n) - 1];
    if (!wanted.includes(showId)) wanted.push(showId);
  }
  return wanted;
}

// 1 seat 10%, 2 seats 50%, 3 seats 20%, 4 seats 20%
function groupSize() {
  const roll = Math.random();
  return roll < 0.1 ? 1 : roll < 0.6 ? 2 : roll < 0.8 ? 3 : 4;
}

const gaussian = () => Math.sqrt(-2 * Math.log(1 - Math.random())) * Math.cos(2 * Math.PI * Math.random());
const clamp = (x, lo, hi) => Math.min(hi, Math.max(lo, x));

// Where this buyer would like to sit, as fractions of the hall (0 = front / left, 1 = back / right).
// People differ: most want the middle-back rows near the centre line, some the recliners at the back,
// some an aisle seat, a few sit anywhere cheap at the front.
function preferredSpot(rows) {
  const roll = Math.random();
  const recliners = rows.filter((row) => row.seatType === 'RECLINER').length / rows.length;
  if (roll < 0.2 && recliners > 0) return { row: 1 - Math.random() * recliners, col: clamp(0.5 + 0.2 * gaussian(), 0, 1) };
  if (roll < 0.35) return { row: clamp(0.55 + 0.15 * gaussian(), 0, 1), col: Math.random() < 0.5 ? 0 : 1 };
  if (roll < 0.45) return { row: Math.random() * 0.3, col: Math.random() };
  return { row: clamp(0.6 + 0.12 * gaussian(), 0, 1), col: clamp(0.5 + 0.15 * gaussian(), 0, 1) };
}

// Like a person scanning the seat map: of the free blocks of `size` adjacent seats, takes one of the few
// closest to their preferred spot (not always the single closest). One row away counts like 2 seats along
// the row. If no row has enough adjacent seats, any free seats. taken = ids from the availability.
function pickSeats(rows, taken, size, spot) {
  const candidates = [];
  rows.forEach((row, r) => {
    const wantRow = spot.row * (rows.length - 1);
    const wantCol = spot.col * (row.seats.length - 1);
    for (let from = 0, run = 0; from < row.seats.length; from++) {
      run = taken.has(row.seats[from].id) ? 0 : run + 1;
      if (run < size) continue;
      const start = from - size + 1;
      candidates.push({ r, start, distance: 2 * Math.abs(r - wantRow) + Math.abs(start + (size - 1) / 2 - wantCol) });
    }
  });
  if (candidates.length > 0) {
    candidates.sort((a, b) => a.distance - b.distance);
    const { r, start } = candidates[Math.floor(Math.random() * Math.min(TOP_CHOICES, candidates.length))];
    return rows[r].seats.slice(start, start + size).map((seat) => seat.id);
  }
  const free = rows.flatMap((row) => row.seats).filter((seat) => !taken.has(seat.id));
  return free.slice(0, size).map((seat) => seat.id);
}

// Seat types to try in best mode: the one at the buyer's spot first, then the others of the hall in turn
function seatTypes(rows, spot) {
  const preferred = rows[Math.round(spot.row * (rows.length - 1))].seatType;
  return [preferred, ...new Set(rows.map((row) => row.seatType).filter((type) => type !== preferred))];
}

// Free seats whose left and right neighbours in the row are both taken (or the row's end)
function countStranded(rows, taken) {
  let stranded = 0;
  for (const { seats } of rows) {
    seats.forEach((seat, i) => {
      const free = (j) => j >= 0 && j < seats.length && !taken.has(seats[j].id);
      if (free(i) && !free(i - 1) && !free(i + 1)) stranded++;
    });
  }
  return stranded;
}

export default function (data) {
  // One buyer (or group): same size and preferred spot on every attempt
  const size = groupSize();
  const customerName = `k6-${exec.scenario.iterationInTest}`;
  const shows = showsToTry(data.showIds);
  let showIndex = 0;
  let spot;
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; ) {
    const showId = shows[showIndex];
    layoutRows[showId] ??= layout(showId, 'layout')?.rows;
    const rows = layoutRows[showId];
    const free = availability(showId, 'availability');
    if (!rows || !free) return;
    if (free.availableSeats === 0) {
      // Sold out: looks at the next show they would like (not a booking attempt)
      if (++showIndex < shows.length) continue;
      usersSoldOut.add(1);
      return;
    }
    spot ??= preferredSpot(rows);

    let url;
    let body;
    if (SEAT_MODE === 'best') {
      const types = seatTypes(rows, spot);
      url = `${BASE_URL}/bookings/best-available`;
      body = { showId, customerName, quantity: size, seatType: types[(attempt - 1) % types.length] };
    } else {
      url = `${BASE_URL}/bookings`;
      body = { showId, customerName, showSeatIds: pickSeats(rows, new Set(free.unavailable), size, spot) };
    }
    sleep(Math.random() * THINK_MAX_S);
    attempt++;

    const res = http.post(url, JSON.stringify(body), {
      headers: JSON_HEADERS,
      responseType: 'text', // best mode learns its seats from the response
      tags: { name: 'book' },
      responseCallback: http.expectedStatuses(201, 409),
    });
    attempts.add(1);
    check(res, { 'booking 201 or 409': (r) => r.status === 201 || r.status === 409 });
    if (res.status === 201) {
      usersBooked.add(1);
      seatsBooked.add(res.json('seats').length);
      soldAfter.add(Date.now() - data.openedAt);
      return;
    }
    if (res.status !== 409) return;
  }
  usersGaveUp.add(1);
}

export function teardown(data) {
  sleep(2); // availability is cached for 1 s
  for (const showId of data.showIds) {
    const free = availability(showId, 'setup');
    const rows = layout(showId, 'setup').rows;
    seatsSold.add(free.unavailable.length);
    seatsTotal.add(free.availableSeats + free.unavailable.length);
    seatsStranded.add(countStranded(rows, new Set(free.unavailable)));
  }
}
