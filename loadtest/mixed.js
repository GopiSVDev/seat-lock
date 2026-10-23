// Mixed test: the traffic of people browsing and booking, one request per iteration.
// The mix is fixed by iteration number, so every run sends exactly the same shares:
//   30% GET /shows/{id}/seats   seat map, Zipf-popular shows
//   20% GET /shows/{id}         show details, Zipf-popular shows
//   15% GET /movies/{id}        Zipf-popular movies
//    5% GET /theatres/{id}
//    5% GET /screens/{id}       screen layout (250 seats)
//   10% GET /bookings/{id}      seeded bookings
//   15% POST /bookings          2 seats, no contention (same seat order as book.js)
// Latency is reported per request type, so slow bookings can't hide behind fast reads.
//   node loadtest/find-capacity.mjs mixed
//
// Single run by hand:
//   RATE=200 DURATION=1m k6 run loadtest/mixed.js
//
// Env: RATE (req/s, default 50), DURATION (default 1m), BASE_URL (default http://10.24.21.250:8080, the LXD VM)

import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import {
  arrivalOptions, bookingRequest, firstFreeBooking, popularShow, randomInt, zipfIndex, BASE_URL, JSON_HEADERS,
} from './lib.js';

const seed = JSON.parse(open('./data/seed.json'));
const BOOK_FROM = 85; // buckets 85-99 book

export const options = arrivalOptions('mixed', ['seatmap', 'show', 'movie', 'theatre', 'screen', 'booking', 'book']);

export function setup() {
  return { start: firstFreeBooking(seed) };
}

function get(path, name) {
  const res = http.get(`${BASE_URL}${path}`, { tags: { name } });
  check(res, { 'status is 200': (r) => r.status === 200 });
}

export default function (data) {
  const i = exec.scenario.iterationInTest;
  const bucket = i % 100;
  if (bucket < 30) get(`/shows/${popularShow(seed)}/seats`, 'seatmap');
  else if (bucket < 50) get(`/shows/${popularShow(seed)}`, 'show');
  else if (bucket < 65) get(`/movies/${zipfIndex(seed.movies) + 1}`, 'movie');
  else if (bucket < 70) get(`/theatres/${randomInt(1, seed.theatres)}`, 'theatre');
  else if (bucket < 75) get(`/screens/${randomInt(1, seed.screens)}`, 'screen');
  else if (bucket < BOOK_FROM) get(`/bookings/${randomInt(1, seed.seededBookings)}`, 'booking');
  else {
    const n = data.start + Math.floor(i / 100) * (100 - BOOK_FROM) + (bucket - BOOK_FROM);
    const res = http.post(`${BASE_URL}/bookings`, bookingRequest(seed, n), {
      headers: JSON_HEADERS,
      tags: { name: 'book' },
    });
    check(res, { 'status is 201': (r) => r.status === 201 });
  }
}
