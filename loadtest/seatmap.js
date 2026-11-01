// Seat map test: one iteration = a user opening the seat map of a show (250 seats), like a browser:
//   GET /shows/{id}/layout        from the CDN edge (nginx on the laptop, loadtest/cdn); only misses reach the app
//   GET /shows/{id}/availability  from the app, every view
// Shows follow a Zipf distribution (s = 1): a few shows get most of the traffic, like
// new releases on a Friday evening. RATE counts views; the judged req/s are the app's requests (one per
// view, so it compares with runs from before the split, which sent one GET /shows/{id}/seats per view).
//   docker compose --profile cdn up -d cdn
//   taskset -c 0-7 node loadtest/find-capacity.mjs seatmap
//
// Single run by hand:
//   RATE=200 DURATION=1m taskset -c 0-7 k6 run loadtest/seatmap.js
//
// Env: RATE (views/s, default 50), DURATION (default 1m), BASE_URL (default http://10.24.21.250:8080, the LXD VM),
// CDN_URL (default http://localhost:8090)

import { arrivalOptions, popularShow, viewSeatMap } from './lib.js';

const seed = JSON.parse(open('./data/seed.json'));

export const options = arrivalOptions('seatmap', ['layout', 'availability']);

export default function () {
  viewSeatMap(popularShow(seed));
}
