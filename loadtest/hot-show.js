// Flash sale test: every request races for seats of one new 1,000-seat show.
// setup() schedules the show on the Flash Arena screen, so each run (and each find-capacity step)
// is a fresh sale. Requests want 1-4 adjacent seats in a random row, mostly near the centre.
// Early requests compete for free seats; once sold out, every request is a 409.
//   node loadtest/find-capacity.mjs hot-show
//
// Single run by hand:
//   RATE=200 DURATION=1m k6 run loadtest/hot-show.js
//
// 201 and 409 both count as success. Oversell check: seats_booked (seats in 201 responses) must
// equal seats_sold (BOOKED seats in the seat map after the run); find-capacity fails the step if not.
//
// Env: RATE (req/s, default 50), DURATION (default 1m), BASE_URL (default http://10.24.21.250:8080, the LXD VM)

import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { arrivalOptions, BASE_URL, JSON_HEADERS } from './lib.js';

const seed = JSON.parse(open('./data/seed.json'));
const seatsBooked = new Counter('seats_booked');
const seatsSold = new Counter('seats_sold');

export const options = arrivalOptions('hot_show');

export function setup() {
  const startTime = new Date(Date.now() + (60 + Math.random() * 300) * 86_400_000).toISOString();
  const show = http.post(
    `${BASE_URL}/shows`,
    JSON.stringify({ movieId: 1, screenId: seed.flashScreenId, startTime, language: 'English', format: 'IMAX_2D' }),
    { headers: JSON_HEADERS, responseType: 'text', tags: { name: 'setup' } },
  );
  if (show.status !== 201) exec.test.abort(`Could not create the flash sale show: ${show.status} ${show.body}`);
  const showId = show.json('id');
  const map = http.get(`${BASE_URL}/shows/${showId}/seats`, { responseType: 'text', tags: { name: 'setup' } });
  return { showId, rows: map.json('rows').map((row) => row.seats.map((seat) => seat.showSeatId)) };
}

// 1 seat 10%, 2 seats 50%, 3 seats 20%, 4 seats 20%
function groupSize() {
  const roll = Math.random();
  return roll < 0.1 ? 1 : roll < 0.6 ? 2 : roll < 0.8 ? 3 : 4;
}

export default function (data) {
  const size = groupSize();
  const row = data.rows[Math.floor(Math.random() * data.rows.length)];
  // Average of two uniforms peaks in the middle: centre seats are the most wanted
  const from = Math.floor(((Math.random() + Math.random()) / 2) * (row.length - size + 1));
  const res = http.post(
    `${BASE_URL}/bookings`,
    JSON.stringify({ showId: data.showId, customerName: `k6-${exec.scenario.iterationInTest}`, showSeatIds: row.slice(from, from + size) }),
    { headers: JSON_HEADERS, tags: { name: 'book' }, responseCallback: http.expectedStatuses(201, 409) },
  );
  check(res, { 'status is 201 or 409': (r) => r.status === 201 || r.status === 409 });
  if (res.status === 201) seatsBooked.add(size);
}

export function teardown(data) {
  const map = http.get(`${BASE_URL}/shows/${data.showId}/seats`, { responseType: 'text', tags: { name: 'setup' } });
  seatsSold.add(map.json('totalSeats') - map.json('availableSeats'));
}
