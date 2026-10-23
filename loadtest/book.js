// Booking test: POST /bookings for 2 seats, every request on different seats (no contention).
// Measures the cost of a booking itself. Each run continues after the seats earlier runs took.
// Normally driven by find-capacity.mjs:
//   node loadtest/find-capacity.mjs book
//
// Single run by hand:
//   RATE=200 DURATION=1m k6 run loadtest/book.js
//
// Env: RATE (req/s, default 50), DURATION (default 1m), BASE_URL (default http://10.24.21.250:8080, the LXD VM)

import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { arrivalOptions, bookingRequest, firstFreeBooking, BASE_URL, JSON_HEADERS } from './lib.js';

const seed = JSON.parse(open('./data/seed.json'));

export const options = arrivalOptions('book');

export function setup() {
  return { start: firstFreeBooking(seed) };
}

export default function (data) {
  const n = data.start + exec.scenario.iterationInTest;
  const res = http.post(`${BASE_URL}/bookings`, bookingRequest(seed, n), {
    headers: JSON_HEADERS,
    tags: { name: 'book' },
  });
  check(res, { 'status is 201': (r) => r.status === 201 });
}
