// Flash sale event: a new 1,000-seat show opens and users rush in. Not a capacity search: one run,
// judged on correctness (no seat sold twice), errors and how latency behaves during the rush.
//   node loadtest/find-capacity.mjs flash-sale "note"          (warms up the JVM first)
//
// Single run by hand:
//   RATE=200 RAMP=10s DURATION=1m k6 run loadtest/flash-sale.js
//
// setup() schedules the show on the Flash Arena screen. New users arrive from 0 up to RATE users/s
// over RAMP, then RATE users/s until DURATION. Each user, like a real buyer:
//   1. reads the seat map (layout from the CDN edge once per VU, availability from the app every time);
//      if it shows no free seats, leaves (sold out)
//   2. thinks for 0-THINK_MAX_S seconds, then books 1-4 adjacent free seats, centre seats first
//   3. on 409 (someone was faster) starts again at 1, up to MAX_ATTEMPTS bookings
// The longer users think, the staler their seat map and the more 409s, as in a real sale.
//
// Reported: users who booked / saw sold out / gave up, booking attempts, sold out after (last
// successful booking, from the sale opening; only if every seat sold), and the oversell check: seats in 201 responses
// (seats_booked) must equal unavailable seats in the availability afterwards (seats_sold).
//
// Env: RATE (peak new users/s, default 50), RAMP (default 10s), DURATION (default 1m),
// THINK_MAX_S (default 3), MAX_ATTEMPTS (default 3), BASE_URL (default http://10.24.21.250:8080, the LXD VM)

import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { availability, baseOptions, layout, BASE_URL, JSON_HEADERS, RATE } from './lib.js';

const seed = JSON.parse(open('./data/seed.json'));
const THINK_MAX_S = Number(__ENV.THINK_MAX_S ?? 3);
const MAX_ATTEMPTS = Number(__ENV.MAX_ATTEMPTS || 3);
const RAMP = __ENV.RAMP || '10s';
const DURATION = __ENV.DURATION || '1m';

const usersBooked = new Counter('users_booked');
const usersSoldOut = new Counter('users_sold_out');
const usersGaveUp = new Counter('users_gave_up');
const attempts = new Counter('booking_attempts');
const seatsBooked = new Counter('seats_booked');
const seatsSold = new Counter('seats_sold');
const seatsTotal = new Counter('seats_total');
const soldAfter = new Trend('booked_after_open', true);

const toSeconds = (d) => (d.endsWith('m') ? Number(d.slice(0, -1)) * 60 : Number(d.replace(/s$/, '')));

export const options = baseOptions('flash_sale', ['layout', 'availability', 'book:201:409']);

// Per VU: the show's layout rows, fetched once (immutable)
let layoutRows;
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

export function setup() {
  const startTime = new Date(Date.now() + (60 + Math.random() * 300) * 86_400_000).toISOString();
  const show = http.post(
    `${BASE_URL}/shows`,
    JSON.stringify({ movieId: 1, screenId: seed.flashScreenId, startTime, language: 'English', format: 'IMAX_2D' }),
    { headers: JSON_HEADERS, responseType: 'text', tags: { name: 'setup' } },
  );
  if (show.status !== 201) exec.test.abort(`Could not create the flash sale show: ${show.status} ${show.body}`);
  return { showId: show.json('id'), openedAt: Date.now() };
}

// 1 seat 10%, 2 seats 50%, 3 seats 20%, 4 seats 20%
function groupSize() {
  const roll = Math.random();
  return roll < 0.1 ? 1 : roll < 0.6 ? 2 : roll < 0.8 ? 3 : 4;
}

// Adjacent free seats closest to the centre of a row, trying rows in random order; if no row has
// enough adjacent seats, any free seats. taken = ids from the availability.
function pickSeats(rows, taken, size) {
  for (const row of [...rows].sort(() => Math.random() - 0.5)) {
    const centre = (row.seats.length - size) / 2;
    let best = -1;
    for (let from = 0, run = 0; from < row.seats.length; from++) {
      run = taken.has(row.seats[from].id) ? 0 : run + 1;
      const start = from - size + 1;
      if (run >= size && (best < 0 || Math.abs(start - centre) < Math.abs(best - centre))) best = start;
    }
    if (best >= 0) return row.seats.slice(best, best + size).map((seat) => seat.id);
  }
  const free = rows.flatMap((row) => row.seats).filter((seat) => !taken.has(seat.id));
  return free.slice(0, size).map((seat) => seat.id);
}

export default function (data) {
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    layoutRows ??= layout(data.showId, 'layout')?.rows;
    const free = availability(data.showId, 'availability');
    if (!layoutRows || !free) return;
    if (free.availableSeats === 0) {
      usersSoldOut.add(1);
      return;
    }
    const seats = pickSeats(layoutRows, new Set(free.unavailable), groupSize());
    sleep(Math.random() * THINK_MAX_S);

    const res = http.post(
      `${BASE_URL}/bookings`,
      JSON.stringify({ showId: data.showId, customerName: `k6-${exec.scenario.iterationInTest}`, showSeatIds: seats }),
      { headers: JSON_HEADERS, tags: { name: 'book' }, responseCallback: http.expectedStatuses(201, 409) },
    );
    attempts.add(1);
    check(res, { 'booking 201 or 409': (r) => r.status === 201 || r.status === 409 });
    if (res.status === 201) {
      usersBooked.add(1);
      seatsBooked.add(seats.length);
      soldAfter.add(Date.now() - data.openedAt);
      return;
    }
    if (res.status !== 409) return;
  }
  usersGaveUp.add(1);
}

export function teardown(data) {
  sleep(2); // availability is cached for 1 s
  const free = availability(data.showId, 'setup');
  seatsSold.add(free.unavailable.length);
  seatsTotal.add(free.availableSeats + free.unavailable.length);
}
