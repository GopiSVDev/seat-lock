// Shared helpers for the k6 scenarios. Ids follow the formulas in seed.sql.

import http from 'k6/http';
import exec from 'k6/execution';

export const BASE_URL = __ENV.BASE_URL || 'http://10.24.21.250:8080';
export const RATE = Number(__ENV.RATE || 50);
export const JSON_HEADERS = { 'Content-Type': 'application/json' };
// Shows on sale at the same time; bookings spread over them (SHOW_WINDOW)
const SHOW_WINDOW = Number(__ENV.SHOW_WINDOW || 500);

// Options shared by all scenarios. Requests of the scenario itself carry the tag load:yes, so setup()
// and teardown() requests stay out of the judged numbers. The always-true thresholds only make k6
// export one latency/count submetric per request name (and per status where given) for find-capacity.
export function baseOptions(scenario, requests) {
  const thresholds = {
    'http_req_duration{load:yes}': ['max>=0'],
    'http_req_failed{load:yes}': ['rate>=0'],
    'http_reqs{load:yes}': ['count>=0'],
  };
  for (const request of requests) {
    const [name, ...statuses] = request.split(':');
    const tags = statuses.length ? statuses.map((s) => `name:${name},status:${s}`) : [`name:${name}`];
    for (const tag of tags) {
      thresholds[`http_req_duration{${tag}}`] = ['max>=0'];
      thresholds[`http_reqs{${tag}}`] = ['count>=0'];
    }
  }
  return {
    discardResponseBodies: true,
    // No 'url' tag: one series per id would flood Prometheus. Requests are grouped by 'name'.
    systemTags: ['status', 'method', 'name', 'group', 'check', 'error', 'error_code', 'scenario', 'expected_response'],
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds,
    scenarios: { [scenario]: { tags: { load: 'yes' } } },
  };
}

// One constant-arrival-rate scenario at RATE req/s for DURATION.
// requests: request names ('seatmap'), optionally with statuses to split by ('book:201:409').
export function arrivalOptions(scenario, requests) {
  const options = baseOptions(scenario, requests);
  Object.assign(options.scenarios[scenario], {
    executor: 'constant-arrival-rate',
    rate: RATE,
    timeUnit: '1s',
    duration: __ENV.DURATION || '1m',
    // VUs needed = rate x latency. Enough for ~2s latency; beyond that k6 drops
    // iterations, which find-capacity counts as a failed step.
    preAllocatedVUs: Math.ceil(RATE * 0.5) + 10,
    maxVUs: RATE * 2 + 50,
  });
  return options;
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

// Booking n -> one seat pair. Consecutive bookings go to different shows of a window of SHOW_WINDOW
// shows (like many shows selling at once), and no two bookings ever want the same seats.
// Only complete windows of the bookable shows (after the seeded sold-out ones) are used.
function seatPair(seed, n) {
  const pairs = seed.seatsPerShow / 2;
  const perWindow = SHOW_WINDOW * pairs;
  const total = Math.floor((seed.shows - seed.bookedShows) / SHOW_WINDOW) * perWindow;
  if (n >= total) exec.test.abort('Out of bookable seats: reseed with loadtest/seed.sh');
  const rest = n % perWindow;
  const showId = seed.bookedShows + 1 + Math.floor(n / perWindow) * SHOW_WINDOW + (rest % SHOW_WINDOW);
  const first = (showId - 1) * seed.seatsPerShow + Math.floor(rest / SHOW_WINDOW) * 2 + 1;
  return { showId, first, total };
}

export function bookingRequest(seed, n) {
  const { showId, first } = seatPair(seed, n);
  return JSON.stringify({ showId, customerName: `k6-${n}`, showSeatIds: [first, first + 1] });
}

// First unused booking index. Earlier runs booked every index below some N, so binary search for
// the first index whose seat is still available.
export function firstFreeBooking(seed) {
  const booked = (n) => {
    const { showId, first } = seatPair(seed, n);
    const rows = http.get(`${BASE_URL}/shows/${showId}/seats`, { responseType: 'text', tags: { name: 'setup' } })
      .json('rows');
    return rows.some((row) => row.seats.some((seat) => seat.id === first && seat.status === 'BOOKED'));
  };
  let lo = 0;
  let hi = seatPair(seed, 0).total;
  while (lo < hi) {
    const mid = Math.floor((lo + hi) / 2);
    if (booked(mid)) lo = mid + 1;
    else hi = mid;
  }
  return lo;
}
