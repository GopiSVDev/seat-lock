# Load test results

Generated from `runs/*.json` by `node loadtest/results.mjs` (also after every `find-capacity.mjs` run).
A row's report link has every step of that run: latency, where the time went, SQL, saturation.

- **Capacity tests** search for the highest request rate the app sustains. Each step sends a fixed rate
  and passes when it meets the Pass criteria of its section. **Stable req/s** is the highest rate that also
  passed a 2-minute confirm run; **Limited by** is the failed step just above it and what it missed.
- **Event tests** (flash-sale) run once at a fixed arrival rate of users. **Result** is pass/fail against
  the Pass criteria; **Failed on** lists what missed.
- **Dropped**: iterations k6 could not start on time because all its virtual users were still waiting for
  responses, i.e. the app could not keep up with the offered rate.
- **CPU** columns: average cores used during the confirm run (capacity) or the event, VM limit in brackets.
  CPU db is VM CPU minus backend CPU: Postgres plus Docker and the OS.
- Setup: the app (PostgreSQL + backend) runs in an LXD VM with 1 vCPU (pinned to one host core) and 2 GB
  RAM, on host networking since 2026-11-01 (no Docker NAT); k6 (CPUs 0-7), the nginx CDN edge (CPUs 8-9)
  and monitoring run on the host. Tomcat threads / DB connections: 200 / 10 at first, 30 / 10 from
  2026-10-27-03-15, 10 / 5 from 2026-10-28-02-18, 10 / 10 (current) from 2026-11-01-08-37.

## seatmap

Seat map views of 250-seat shows; a few popular shows get most views (Zipf). The read a user makes before booking. Since 2026-11-01 a view is `GET /shows/{id}/availability` from the app plus `GET /shows/{id}/layout` through a CDN edge (nginx on the laptop; only misses reach the app, hit rate in the run report); before, one `GET /shows/{id}/seats`. Rate = views/s; judged req/s = requests to the app.

Pass: p90 <= 150 ms, p95 <= 200 ms, p99 <= 500 ms (also p95/p99 per request type), errors < 1.00%, dropped < 1%.

| Date | Stable req/s | vs previous | p90 ms | p95 ms | p99 ms | Errors | Limited by | CPU backend | CPU db | Note | Run |
|---|---:|---:|---:|---:|---:|---:|---|---|---|---|---|
| 2026-10-23 | 710 | - | 4.3 | 10.8 | 29.7 | 0.00% | 750 req/s (confirm, 2m): p90 316 > 150 ms, p95 617.1 > 200 ms, p99 1308.9 > 500 ms | 0.49 (1) | 0.271 (1) | baseline | [report](runs/2026-10-23-02-12-seatmap.md) |
| 2026-10-27 | 850 | +19.7% | 24.4 | 40.7 | 98.2 | 0.00% | 860 req/s (ramp, 30s): p90 567.4 > 150 ms, p95 817.4 > 200 ms, p99 1480.8 > 500 ms | 0.528 (1) | 0.296 (1) | dto projection | [report](runs/2026-10-27-01-20-seatmap.md) |
| 2026-10-27 | 900 | +5.9% | 41.2 | 91.1 | 239.1 | 0.00% | 910 req/s (ramp, 30s): p90 262.3 > 150 ms, p95 612.3 > 200 ms, p99 1173.8 > 500 ms | 0.519 (1) | 0.308 (1) | leaner seat map json | [report](runs/2026-10-27-02-30-seatmap.md) |
| 2026-10-27 | 990 | +10.0% | 23.2 | 45.8 | 169.1 | 0.00% | 1000 req/s (ramp, 30s): p90 227.1 > 150 ms, p95 459.1 > 200 ms, p99 982 > 500 ms | 0.503 (1) | 0.372 (1) | no transaction on seat map | [report](runs/2026-10-27-02-52-seatmap.md) |
| 2026-10-28 | 1230 | +26.8% | 55.6 | 93.6 | 157.7 | 0.00% | 1240 req/s (refine, 1m): p90 166.5 > 150 ms | 0.578 (1) | 0.252 (1) | seat order + no show-sql | [report](runs/2026-10-28-00-43-seatmap.md) |
| 2026-10-28 | 1320 | +6.5% | 34.6 | 55.7 | 95.2 | 0.00% | 1360 req/s (confirm, 2m): p90 261.8 > 150 ms, p95 332.6 > 200 ms | 0.566 (1) | 0.289 (1) | threads 10, pool 5 (vs true ~1300 before: +2-4%; conn hold at the knee 1.9 → 0.6 ms) | [report](runs/2026-10-28-02-18-seatmap.md) |
| 2026-10-28 | 1950 | +47.7% | 56.3 | 89.2 | 155.8 | 0.00% | 1960 req/s (refine, 1m): p90 228.8 > 150 ms, p95 276.5 > 200 ms | 0.679 (1) | 0.19 (1) | seat map cache 1s | [report](runs/2026-10-28-02-46-seatmap.md) |
| 2026-10-28 | 2130 | +9.2% | 44.2 | 78.7 | 152.6 | 0.00% | 2190 req/s (confirm, 2m): p90 249.4 > 150 ms, p95 489.1 > 200 ms, p99 673.5 > 500 ms | 0.673 (1) | 0.171 (1) | cache serialized seat map json | [report](runs/2026-10-28-03-17-seatmap.md) |
| 2026-11-01 | 3430 | +61.0% | 18.5 | 72.1 | 235.3 | 0.00% | 3440 req/s (ramp, 30s): p90 249 > 150 ms, p95 323 > 200 ms, availability p95 323 > 200 ms | 0.588 (1) | 0.241 (1) | layout/availability split + CDN edge | [report](runs/2026-11-01-01-51-seatmap.md) |
| 2026-11-01 | 4150 | +21.0% | 24.9 | 52.8 | 104.3 | 0.00% | 4260 req/s (confirm, 2m): p90 177 > 150 ms, p95 226.3 > 200 ms, availability p95 226.3 > 200 ms | 0.634 (1) | 0.246 (1) | host networking (no Docker NAT) | [report](runs/2026-11-01-02-42-seatmap.md) |

## book

`POST /bookings` for 2 seats; every request takes different seats, so bookings never collide. The cost of one booking: conditional UPDATE of the seats plus INSERT of the booking and its seats, in one transaction.

Pass: p90 <= 150 ms, p95 <= 200 ms, p99 <= 500 ms (also p95/p99 per request type), errors < 1.00%, dropped < 1%.

| Date | Stable req/s | vs previous | p90 ms | p95 ms | p99 ms | Errors | Limited by | CPU backend | CPU db | Note | Run |
|---|---:|---:|---:|---:|---:|---:|---|---|---|---|---|
| 2026-10-23 | 470 | - | 57.1 | 91.7 | 196.5 | 0.00% | 480 req/s (refine, 30s): p90 457.8 > 150 ms, p95 597.3 > 200 ms, p99 1178.2 > 500 ms | 0.369 (1) | 0.584 (1) | baseline | [report](runs/2026-10-23-02-30-book.md) |
| 2026-11-01 | 570 | +21.3% | 17.1 | 35.5 | 72.7 | 0.00% | 590 req/s (confirm, 2m): p95 208.2 > 200 ms, p99 554.7 > 500 ms | 0.362 (1) | 0.433 (1) | re-baseline: host net, threads 10/pool 5, no show-sql | [report](runs/2026-11-01-07-17-book.md) |
| 2026-11-01 | 760 | +33.3% | 83.2 | 114.9 | 333.6 | 0.00% | 770 req/s (refine, 1m): p90 368.4 > 150 ms, p95 493 > 200 ms, p99 526.4 > 500 ms | 0.41 (1) | 0.482 (1) | update returning instead of re-select | [report](runs/2026-11-01-07-56-book.md) |
| 2026-11-01 | 800 | +5.3% | 63.2 | 98.9 | 160.9 | 0.00% | 810 req/s (refine, 30s): p90 417.1 > 150 ms, p95 593.1 > 200 ms, p99 686.9 > 500 ms | 0.405 (1) | 0.491 (1) | batch booking seat inserts (sequence ids) | [report](runs/2026-11-01-08-19-book.md) |
| 2026-11-01 | 870 | +8.8% | 27.4 | 40.3 | 71.9 | 0.00% | 900 req/s (confirm, 2m): p95 215.9 > 200 ms | 0.428 (1) | 0.497 (1) | pool 5 → 10 (threads 10) | [report](runs/2026-11-01-08-37-book.md) |
| 2026-11-01 | 960 | +10.3% | 42.9 | 68.5 | 147.8 | 0.00% | 970 req/s (refine, 30s): p90 221.7 > 150 ms, p95 294 > 200 ms | 0.388 (1) | 0.547 (1) | booking + seats in one insert (jdbc) | [report](runs/2026-11-01-09-53-book.md) |

## mixed

Browsing and booking together: 30% seat map view (layout through the CDN edge + availability from the app), 20% show, 15% movie, 5% theatre, 5% screen, 10% get booking, 15% book (bookings never collide).

Pass: p90 <= 150 ms, p95 <= 200 ms, p99 <= 500 ms (also p95/p99 per request type), errors < 1.00%, dropped < 1%.

| Date | Stable req/s | vs previous | p90 ms | p95 ms | p99 ms | Errors | Limited by | CPU backend | CPU db | Note | Run |
|---|---:|---:|---:|---:|---:|---:|---|---|---|---|---|
| 2026-10-23 | 820 | - | 47 | 89.6 | 227.9 | 0.00% | 870 req/s (confirm, 2m): p90 150.6 > 150 ms, p95 283.3 > 200 ms, p99 553.2 > 500 ms +14 more | 0.44 (1) | 0.481 (1) | baseline | [report](runs/2026-10-23-02-45-mixed.md) |

## show-create

`POST /shows` on a 250-seat screen, which also creates the 250 show seats in the same transaction. Admin path.

Pass: p90 <= 150 ms, p95 <= 200 ms, p99 <= 500 ms (also p95/p99 per request type), errors < 1.00%, dropped < 1%.

| Date | Stable req/s | vs previous | p90 ms | p95 ms | p99 ms | Errors | Limited by | CPU backend | CPU db | Note | Run |
|---|---:|---:|---:|---:|---:|---:|---|---|---|---|---|
| 2026-10-27 | 13 | - | 80 | 143.6 | 310.9 | 0.00% | 14 req/s (confirm, 2m): dropped 1.78% >= 1%, p90 2482.5 > 150 ms, p95 2790.6 > 200 ms +1 more | 0.222 (1) | 0.49 (1) | baseline | [report](runs/2026-10-27-00-21-show-create.md) |

## flash-sale

A new 1,000-seat show opens and new users arrive at a fixed rate (Users/s). Each user loads the seat map (layout through the CDN edge, availability from the app on every attempt), thinks 0-3 s, books 1-4 adjacent free seats near the centre, and on a 409 starts again, up to 3 bookings. Runs once, after warming up the JVM with `mixed`. **p95 seat map** is the availability request since 2026-11-01 (the full seat map before).

Pass: no seat oversold, errors < 1.00%, dropped < 1%, over 1m at 200 users/s (latency, sell-out and 409s are reported, not judged).

| Date | Users/s | Result | Failed on | Users | Booked | Gave up | Sold out after s | Oversold | p95 seat map ms | p95 book 201 ms | p95 book 409 ms | Errors | Dropped | CPU backend | CPU db | Note | Run |
|---|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|
| 2026-10-23 | 200 | fail | dropped 47.72% >= 1% | 5749 | 281 | 5468 | not sold out | 0 | 7398.1 | 6080.7 | 7468.2 | 0.00% | 47.72% | 0.67 (1) | 0.239 (1) | baseline | [report](runs/2026-10-23-03-02-flash-sale.md) |

## Reverted or within ±5%

Runs left out of the tables above: experiments that were reverted, and kept changes within ±5%
(capacity search noise). **vs previous** compares with the kept run before it; kept runs listed here are
still the baseline for the next "vs previous" above.

| Date | Test | Result | vs previous | Status | Experiment | Why reverted | Run |
|---|---|---:|---:|---|---|---|---|
| 2026-10-27 | seatmap | 830 req/s | -2.4% | reverted | response buffer | no measurable gain | [report](runs/2026-10-27-01-51-seatmap.md) |
| 2026-10-27 | seatmap | 970 req/s | -2.0% | kept | tomcat threads 30 (throughput same within noise, overload p99 ~halved) | - | [report](runs/2026-10-27-03-15-seatmap.md) |
| 2026-10-28 | seatmap | 1240 req/s | +0.8% | kept | seat map via JdbcClient (true limit ~1300: 1310 passed 1m, failed 2m confirm narrowly) | - | [report](runs/2026-10-28-01-23-seatmap.md) |
| 2026-10-28 | seatmap | 1280 req/s | +3.2% | reverted | gzip seat map | deflate CPU > send savings on 1 core | [report](runs/2026-10-28-01-52-seatmap.md) |
| 2026-11-01 | seatmap | 3980 req/s | -4.1% | reverted | JVM heap fixed + young gen half (NewRatio=1) | no gain, promotion per GB unchanged | [report](runs/2026-11-01-03-35-seatmap.md) |
| 2026-11-01 | book | 900 req/s | +3.4% | reverted | group commit (commit_delay 100us, siblings 3) | capacity same as the true ~900 before; fewer fsyncs not attributable | [report](runs/2026-11-01-09-05-book.md) |
| 2026-11-02 | book | 1110 req/s | +15.6% | reverted | synchronous_commit off | +16% but a crash loses acknowledged bookings, seats resold | [report](runs/2026-11-02-00-34-book.md) |
