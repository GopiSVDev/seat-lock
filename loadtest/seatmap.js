// Seat map test: GET /shows/{id}/seats (250 seats per show) on the seeded shows.
// Shows follow a Zipf distribution (s = 1): a few shows get most of the traffic, like
// new releases on a Friday evening.
//   node loadtest/find-capacity.mjs seatmap
//
// Single run by hand:
//   RATE=200 DURATION=1m k6 run loadtest/seatmap.js
//
// Env: RATE (req/s, default 50), DURATION (default 1m), BASE_URL (default http://10.24.21.250:8080, the LXD VM)

import http from 'k6/http';
import { check } from 'k6';
import { arrivalOptions, popularShow, BASE_URL } from './lib.js';

const seed = JSON.parse(open('./data/seed.json'));

export const options = arrivalOptions('seatmap', ['seatmap']);

export default function () {
  const res = http.get(`${BASE_URL}/shows/${popularShow(seed)}/seats`, { tags: { name: 'seatmap' } });
  check(res, { 'status is 200': (r) => r.status === 200 });
}
