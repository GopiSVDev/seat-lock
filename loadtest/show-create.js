// Show scheduling test: POST /shows on the 250-seat screens. Each show also creates its 250
// show seats (ShowScheduled event, same transaction), so this is the heaviest write per request.
// Expect a low rate. Adds shows and seats to the dataset: reseed afterwards.
//   node loadtest/find-capacity.mjs show-create
//
// Single run by hand:
//   RATE=5 DURATION=1m k6 run loadtest/show-create.js
//
// Env: RATE (req/s, default 50), DURATION (default 1m), BASE_URL (default http://10.24.21.250:8080, the LXD VM)

import http from 'k6/http';
import { check } from 'k6';
import { arrivalOptions, randomInt, BASE_URL, JSON_HEADERS } from './lib.js';

const seed = JSON.parse(open('./data/seed.json'));

export const options = arrivalOptions('show_create', ['create-show']);

export default function () {
  // Far beyond the seeded shows; overlapping shows are not checked yet
  const startTime = new Date(Date.now() + randomInt(400, 3000) * 86_400_000).toISOString();
  const res = http.post(
    `${BASE_URL}/shows`,
    JSON.stringify({
      movieId: randomInt(1, seed.movies),
      screenId: randomInt(1, seed.screens),
      startTime,
      language: 'English',
      format: 'TWO_D',
    }),
    { headers: JSON_HEADERS, tags: { name: 'create-show' } },
  );
  check(res, { 'status is 201': (r) => r.status === 201 });
}
