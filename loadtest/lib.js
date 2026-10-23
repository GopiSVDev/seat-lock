// Shared helpers for the k6 scenarios. Ids follow the formulas in seed.sql.

import http from 'k6/http';
import exec from 'k6/execution';

export const BASE_URL = __ENV.BASE_URL || 'http://10.24.21.250:8080';
export const RATE = Number(__ENV.RATE || 50);
export const JSON_HEADERS = { 'Content-Type': 'application/json' };

// One constant-arrival-rate scenario at RATE req/s for DURATION.
export function arrivalOptions(name) {
  return {
    discardResponseBodies: true,
    // No 'url' tag: one series per id would flood Prometheus. Requests are grouped by 'name'.
    systemTags: ['status', 'method', 'name', 'group', 'check', 'error', 'error_code', 'scenario', 'expected_response'],
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    scenarios: {
      [name]: {
        executor: 'constant-arrival-rate',
        rate: RATE,
        timeUnit: '1s',
        duration: __ENV.DURATION || '1m',
        // VUs needed = rate x latency. Enough for ~2s latency; beyond that k6 drops
        // iterations, which find-capacity counts as a failed step.
        preAllocatedVUs: Math.ceil(RATE * 0.5) + 10,
        maxVUs: RATE * 2 + 50,
      },
    },
  };
}

// Zipf with s = 1 over n items: P(rank <= r) ~ ln r / ln n, so rank = n^u for uniform u.
export function zipfIndex(n) {
  return Math.min(Math.floor(Math.pow(n, Math.random())), n) - 1;
}

// Popular shows spread over all ids (7919 is prime, so this is a permutation of 0..n-1).
export function popularShow(seed) {
  return ((zipfIndex(seed.shows) * 7919) % seed.shows) + 1;
}

export function randomInt(min, max) {
  return min + Math.floor(Math.random() * (max - min + 1));
}

// Booking n takes seat pair n % 125 of show bookedShows + 1 + floor(n / 125), so concurrent
// bookings never want the same seats (no contention by design).
export function bookingRequest(seed, n) {
  const pairs = seed.seatsPerShow / 2;
  const showId = seed.bookedShows + 1 + Math.floor(n / pairs);
  if (showId > seed.shows) exec.test.abort('Out of bookable seats: reseed with loadtest/seed.sh');
  const first = (showId - 1) * seed.seatsPerShow + (n % pairs) * 2 + 1;
  return JSON.stringify({ showId, customerName: `k6-${n}`, showSeatIds: [first, first + 1] });
}

// First unused booking index: earlier runs filled shows in order, so binary search for the first
// bookable show whose first seat is still available.
export function firstFreeBooking(seed) {
  const untouched = (showId) =>
    http.get(`${BASE_URL}/shows/${showId}/seats`, { responseType: 'text', tags: { name: 'setup' } })
      .json('rows.0.seats.0.status') === 'AVAILABLE';
  let lo = seed.bookedShows + 1;
  let hi = seed.shows + 1;
  while (lo < hi) {
    const mid = Math.floor((lo + hi) / 2);
    if (untouched(mid)) hi = mid;
    else lo = mid + 1;
  }
  return (lo - seed.bookedShows - 1) * (seed.seatsPerShow / 2);
}
