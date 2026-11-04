# Performance results

Every change below was made only after a load test showed a bottleneck, and kept only if a new test proved
it helped.
Every individual run, with its pass criteria and a detailed report, is in [RESULTS.md](RESULTS.md).

**Setup:** the app (Spring Boot + PostgreSQL) runs in a VM with **1 vCPU and 2 GB RAM**. Load comes from
[k6](https://k6.io) on the host, metrics from Prometheus.
**Capacity** = the highest rate that holds for 2 minutes with p90 ≤ 150 ms, p95 ≤ 200 ms, p99 ≤ 500 ms,
< 1% errors and no requests left waiting.

## Summary

| Scenario | First version | Final | Gain |
|---|---:|---:|---:|
| Seat map views | 710 /s | **4,150 /s** | **5.8×** |
| Bookings | 470 /s | **960 /s** | **2.0×** |
| Flash sale, 200 new users/s on a 1,000-seat show | failed: app overloaded, 48% of users never served | **sold out in 10.1 s, no failed bookings while seats remained** | |
| Seats sold twice, across all tests | 0 | **0** | |

## Seat map: 710 → 4,150 views/s

The most frequent request: a customer opening a show's seat map (250 seats).

| Step | Change | Views/s | Gain |
|---:|---|---:|---:|
| 0 | First version: JPA entities, one JSON response with every seat and its status | 710 | |
| 1 | Read straight into small DTO records instead of managed JPA entities | 850 | +20% |
| 2 | Leaner JSON: seat type once per row instead of per seat (−40% bytes) | 900 | +6% |
| 3 | No transaction around a single SELECT (saves the BEGIN/COMMIT round trips) | 990 | +10% |
| 4 | Order by the index column (no sort step) and stop logging every SQL statement | 1,230 | +27% |
| 5 | 10 Tomcat threads / 5 DB connections instead of 200 / 10 on one core (less context switching) | 1,320 | +7% |
| 6 | Cache each seat map for 1 second (Caffeine) | 1,950 | +48% |
| 7 | Cache the ready-made JSON bytes, not objects (skips serialization on every hit) | 2,130 | +9% |
| 8 | Split into a layout that never changes (served by a CDN) and a small availability list | 3,430 | +61% |
| 9 | Host networking on the VM (no Docker NAT on every packet) | **4,150** | +21% |

## Bookings: 470 → 960 bookings/s

`POST /bookings` for 2 seats, each request on different seats (measures the cost of one booking, not
contention).

| Step | Change | Bookings/s | Gain |
|---:|---|---:|---:|
| 0 | First version: 8 SQL statements per booking through JPA (update, re-read the seats, insert the booking, then each seat) | 470 | |
| 1 | Settings from the seat map work: host networking, 10 threads / 5 connections, no SQL logging | 570 | +21% |
| 2 | `UPDATE … RETURNING` books the seats and returns their labels in one statement | 760 | +33% |
| 3 | Insert the booking's seats in one batch | 800 | +5% |
| 4 | 10 DB connections (bookings were waiting for one while others waited on commit) | 870 | +9% |
| 5 | Booking and all its seats in **one** INSERT statement (JDBC, CTE + `unnest`) | **960** | +10% |

The final version is CPU-bound: about 1 ms of CPU per booking on the single core, split between the app and
PostgreSQL. A booking is now 3 round trips to the database.

## Flash sale

200 new users per second rush a new show; each wants 1-4 seats near their favourite spot, thinks 0-3 s,
books, and retries up to 3 times on "seat taken".

| Run | Change | Result |
|---|---|---|
| First version | — | **Failed**: the app couldn't keep up; 48% of users were never served, p95 ~7 s |
| After the seat map and booking work | app faster; test fixed to pre-allocate enough virtual users | Passed, sold out in 58.7 s, but **98.8% of booking attempts failed**: everyone picked the same centre seats |
| Realistic buyers | test change: buyers aim for different spots (centre, recliners, aisle, front) | Sold out in 17.5 s; 93% of attempts failed; **1,627 users gave up** |
| **Best available** | the server picks the best adjacent seats, `SELECT … FOR UPDATE SKIP LOCKED` | **Sold out in 10.1 s**; booking attempts 6,412 → 1,627 (**−75%**); users giving up 1,627 → 160 (**−90%**); no failed attempts while seats remained |
| Many shows at once | test change: 45 shows on sale together, so buying lasts the whole minute | **9,948 of 10,996 users booked**, 0 oversold, VM CPU 44%, p95 booking 10 ms |

`SKIP LOCKED` is the key: concurrent buyers lock *different* candidate seats instead of all waiting on the
same ones, so they stop colliding.

## Tried and rejected

Measured, then reverted. These matter as much as the wins.

| Experiment | Result | Why rejected |
|---|---|---|
| `synchronous_commit = off` in PostgreSQL | bookings 960 → 1,110 /s (+16%) | a crash would lose bookings already confirmed to customers, and their seats could be sold again |
| gzip for the seat map | −2%, p95 2-4× worse | on one core, compressing costs more CPU than sending fewer bytes saves |
| Virtual threads | worse tail latency, collapse under overload | the small fixed thread pool was acting as natural admission control |
| Group commit (`commit_delay`) | no measurable gain | |
| JVM heap and young-generation tuning | no gain | objects were promoted at the same rate per GB |
| One large response buffer (single socket write) | no gain | the cost was the bytes, not the number of writes |
