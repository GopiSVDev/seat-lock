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
  RAM; k6 and monitoring run on the host, on other cores.

## seatmap

`GET /shows/{id}/seats` on 250-seat shows; a few popular shows get most requests (Zipf). The read a user makes before booking.

Pass: p90 <= 150 ms, p95 <= 200 ms, p99 <= 500 ms (also p95/p99 per request type), errors < 1.00%, dropped < 1%.

| Date | Stable req/s | vs previous | p90 ms | p95 ms | p99 ms | Errors | Limited by | CPU backend | CPU db | Note | Run |
|---|---:|---:|---:|---:|---:|---:|---|---|---|---|---|
| 2026-10-23 | 710 | - | 4.3 | 10.8 | 29.7 | 0.00% | 750 req/s (confirm, 2m): p90 316 > 150 ms, p95 617.1 > 200 ms, p99 1308.9 > 500 ms | 0.49 (1) | 0.271 (1) | baseline | [report](runs/2026-10-23-02-12-seatmap.md) |
| 2026-10-27 | 850 | +19.7% | 24.4 | 40.7 | 98.2 | 0.00% | 860 req/s (ramp, 30s): p90 567.4 > 150 ms, p95 817.4 > 200 ms, p99 1480.8 > 500 ms | 0.528 (1) | 0.296 (1) | dto projection | [report](runs/2026-10-27-01-20-seatmap.md) |
| 2026-10-27 | 830 | -2.4% | 7.6 | 18 | 43 | 0.00% | 880 req/s (confirm, 2m): p90 772.4 > 150 ms, p95 1066.1 > 200 ms, p99 1701.8 > 500 ms | 0.475 (1) | 0.316 (1) | response buffer (reverted: no measurable gain) | [report](runs/2026-10-27-01-51-seatmap.md) |
| 2026-10-27 | 900 | +5.9% | 41.2 | 91.1 | 239.1 | 0.00% | 910 req/s (ramp, 30s): p90 262.3 > 150 ms, p95 612.3 > 200 ms, p99 1173.8 > 500 ms | 0.519 (1) | 0.308 (1) | leaner seat map json | [report](runs/2026-10-27-02-30-seatmap.md) |
| 2026-10-27 | 990 | +10.0% | 23.2 | 45.8 | 169.1 | 0.00% | 1000 req/s (ramp, 30s): p90 227.1 > 150 ms, p95 459.1 > 200 ms, p99 982 > 500 ms | 0.503 (1) | 0.372 (1) | no transaction on seat map | [report](runs/2026-10-27-02-52-seatmap.md) |
| 2026-10-27 | 970 | -2.0% | 5.2 | 12.6 | 34.3 | 0.00% | 1030 req/s (confirm, 2m): p90 152.9 > 150 ms, p95 207.1 > 200 ms | 0.446 (1) | 0.337 (1) | tomcat threads 30 (throughput same within noise, overload p99 ~halved) | [report](runs/2026-10-27-03-15-seatmap.md) |
| 2026-10-28 | 1230 | +26.8% | 55.6 | 93.6 | 157.7 | 0.00% | 1240 req/s (refine, 1m): p90 166.5 > 150 ms | 0.578 (1) | 0.252 (1) | seat order + no show-sql | [report](runs/2026-10-28-00-43-seatmap.md) |

## book

`POST /bookings` for 2 seats; every request takes different seats, so bookings never collide. The cost of one booking: conditional UPDATE of the seats plus INSERT of the booking and its seats, in one transaction.

Pass: p90 <= 150 ms, p95 <= 200 ms, p99 <= 500 ms (also p95/p99 per request type), errors < 1.00%, dropped < 1%.

| Date | Stable req/s | vs previous | p90 ms | p95 ms | p99 ms | Errors | Limited by | CPU backend | CPU db | Note | Run |
|---|---:|---:|---:|---:|---:|---:|---|---|---|---|---|
| 2026-10-23 | 470 | - | 57.1 | 91.7 | 196.5 | 0.00% | 480 req/s (refine, 30s): p90 457.8 > 150 ms, p95 597.3 > 200 ms, p99 1178.2 > 500 ms | 0.369 (1) | 0.584 (1) | baseline | [report](runs/2026-10-23-02-30-book.md) |

## mixed

Browsing and booking together: 30% seat map, 20% show, 15% movie, 5% theatre, 5% screen, 10% get booking, 15% book (bookings never collide).

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

A new 1,000-seat show opens and new users arrive at a fixed rate (Users/s). Each user loads the seat map, thinks 0-3 s, books 1-4 adjacent free seats near the centre, and on a 409 starts again, up to 3 bookings. Runs once, after warming up the JVM with `mixed`.

Pass: no seat oversold, errors < 1.00%, dropped < 1%, over 1m at 200 users/s (latency, sell-out and 409s are reported, not judged).

| Date | Users/s | Result | Failed on | Users | Booked | Gave up | Sold out after s | Oversold | p95 seat map ms | p95 book 201 ms | p95 book 409 ms | Errors | Dropped | CPU backend | CPU db | Note | Run |
|---|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|
| 2026-10-23 | 200 | fail | dropped 47.72% >= 1% | 5749 | 281 | 5468 | not sold out | 0 | 7398.1 | 6080.7 | 7468.2 | 0.00% | 47.72% | 0.67 (1) | 0.239 (1) | baseline | [report](runs/2026-10-23-03-02-flash-sale.md) |
