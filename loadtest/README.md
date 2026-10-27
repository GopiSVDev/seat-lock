# Load testing

Load tests for the backend, used to find how many requests per second it sustains on a small, fixed
resource budget, to find what breaks under concurrency, and to optimize until the hardware, not the
software, is the limit. Every run is recorded, so the effect of each change is visible in
[results/RESULTS.md](results/RESULTS.md).

## Contents

- [Test environment](#test-environment)
- [Pass criteria](#pass-criteria)
- [Scenarios](#scenarios)
- [Dataset](#dataset)
- [Prerequisites](#prerequisites)
- [Running a test](#running-a-test)
- [Profiling](#profiling)
- [Diagnosing where the time goes](#diagnosing-where-the-time-goes)
- [Reading the results](#reading-the-results)
- [Files](#files)

## Test environment

The app runs in an LXD virtual machine, `seat-lock` (Debian 13, IP `10.24.21.250`), with 1 CPU,
2 GB of RAM and a 10 GB disk. Only `postgres` and `backend` run in it, under Docker Compose without
container limits, so they share the VM like they would on a small cloud server. The backend heap is 25%
of the machine's RAM (`-XX:MaxRAMPercentage=25` in `docker-compose.yml`, 512 MB on the VM) so the JVM
leaves room for Postgres. The DB pool size and Tomcat thread count come from `.env` (`DB_POOL_SIZE`,
`TOMCAT_MAX_THREADS`); the run file's saturation table shows the values each run used.

Everything else runs on the laptop, outside the budget:

| Tool                 | Where                         | Measures                                                 |
| -------------------- | ----------------------------- | -------------------------------------------------------- |
| k6                   | laptop                        | Sends the load to `http://10.24.21.250:8080`             |
| Prometheus           | laptop (Docker, host network) | Scrapes the backend in the VM, postgres-exporter and LXD |
| postgres-exporter    | laptop (Docker, host network) | `pg_stat_statements` of the VM's Postgres                |
| LXD metrics endpoint | laptop (LXD itself)           | VM CPU and memory, from outside the VM                   |

The VM's vCPU is pinned to host CPU 10 and k6 runs on CPUs 0-9 with `taskset`, so the load generator
never shares a core with the app. Keep the laptop otherwise idle and plugged in during runs.

Backend CPU comes from the JVM (`process_cpu_usage`). Postgres has no separate CPU number: the run file
shows VM CPU minus backend CPU, which is Postgres plus Docker and the OS.

## Pass criteria

A rate passes only if all of the following hold during the step:

| Metric             | Limit                                                                      |
| ------------------ | -------------------------------------------------------------------------- |
| p90 latency        | ≤ 150 ms                                                                   |
| p95 latency        | ≤ 200 ms                                                                   |
| p99 latency        | ≤ 500 ms                                                                   |
| Error rate         | < 1%                                                                       |
| Dropped iterations | < 1% (k6 could not start requests on time because the backend fell behind) |

Latency limits apply to all requests together and, for p95/p99, to each request type on its own
(`book`, `seatmap`, ...), so slow bookings can't hide behind fast reads in `mixed`. Only the scenario's
own requests count: `setup()`/`teardown()` requests (tag `name:setup`) are left out.

`flash-sale` is an event test, not a capacity search: it passes when no seat is oversold (seats in 201
responses = seats marked BOOKED afterwards), errors < 1% and dropped < 1%. Its latency is reported, not
judged.

The limits can be overridden with environment variables (`P90_MS`, `P95_MS`, `P99_MS`, ...), listed at
the top of `find-capacity.mjs`. A run with different limits is not comparable with earlier runs.

## Scenarios

| Test          | Kind     | Script           | Requests                                                                                                  | Success     |
| ------------- | -------- | ---------------- | --------------------------------------------------------------------------------------------------------- | ----------- |
| `seatmap`     | capacity | `seatmap.js`     | `GET /shows/{id}/seats` (250 seats), Zipf-popular shows                                                   | 200         |
| `book`        | capacity | `book.js`        | `POST /bookings` for 2 seats, every request on different seats (cost of a booking, no contention)         | 201         |
| `mixed`       | capacity | `mixed.js`       | Browsing and booking: 30% seat map, 20% show, 15% movie, 5% theatre, 5% screen, 10% booking, 15% book    | 200 / 201   |
| `show-create` | capacity | `show-create.js` | `POST /shows` on a 250-seat screen (creates 250 show seats in the same transaction)                       | 201         |
| `flash-sale`  | event    | `flash-sale.js`  | Users rush a new 1,000-seat show: seat map, think, book adjacent seats, retry on 409                      | 200 / 201 / 409 |

- **Zipf** (s = 1): a few shows get most of the traffic, like new releases on a Friday evening. Popular
  ranks are spread over all show ids, so hot rows are not clustered at the start of the table.
- **book** and **mixed** never collide: booking n gets its own seat pair. Consecutive bookings go to
  different shows of a window of 500 shows on sale at once (`SHOW_WINDOW`), so concurrent bookings
  touch rows all over the table like real traffic, not the same few pages. Each run continues after the
  seats earlier runs took (`setup()` binary-searches the first free one). A run aborts when the
  bookable seats run out: reseed.
- **flash-sale** opens a fresh show in `setup()`. New users arrive from 0 to `RATE` users/s over `RAMP`
  (default 10s), then at `RATE` until `DURATION`. Each user reads the seat map (leaves if sold out),
  thinks 0-`THINK_MAX_S` s (default 3), books 1-4 adjacent free seats near the centre, and starts again
  on a 409, up to `MAX_ATTEMPTS` (3) bookings. Stale seat maps cause 409s, as in a real sale. It reports
  users who booked / saw sold out / gave up, sold-out time, latency of 201 and 409 bookings separately,
  and the oversell check. Through `find-capacity.mjs` it first warms up the JVM with `mixed`, then runs
  once at `PEAK_USERS` (default 200) users/s for `EVENT_DURATION` (default 1m).
- **show-create** runs at a few req/s (its own defaults in `find-capacity.mjs`) and grows the dataset:
  reseed afterwards.
- **mixed** picks the request type by iteration number, so every run sends exactly the same shares.

## Dataset

`seed.sh` loads a fixed dataset with `seed.sql` (bulk SQL, ~10 s) and writes `data/seed.json`, which the
scripts read:

- 50 movies, 20 theatres in 5 cities, 40 screens of 250 seats, and the 1,000-seat "Flash Arena" screen
- 6,000 shows (`SHOWS`), 1.5M show seats; the first 200 shows (`BOOKED_SHOWS`) are sold out by 25,000
  seeded bookings, so seat maps and `GET /bookings/{id}` read real data
- 725,000 bookable seat pairs for `book` and `mixed`

Ids are explicit and follow formulas the scripts rely on (documented at the top of `seed.sql`).

## Prerequisites

- The VM, with the app running in it:
  ```bash
  sudo lxc launch images:debian/13 seat-lock --vm -c limits.cpu=1 -c limits.memory=2GiB -d root,size=10GiB
  sudo lxc config device override seat-lock eth0 ipv4.address=10.24.21.250   # fixed IP
  sudo lxc stop seat-lock && sudo lxc config set seat-lock limits.cpu=10-10   # pin to CPU 10
  sudo lxc start seat-lock
  sudo lxc exec seat-lock -- sh -c 'apt-get update && apt-get install -y curl procps btop && curl -fsSL https://get.docker.com | sh'
  # no package updates in the background during runs
  sudo lxc exec seat-lock -- systemctl disable --now apt-daily.timer apt-daily-upgrade.timer
  ```
- In `.env`: `VM_IP=10.24.21.250` (postgres-exporter connects to the VM's Postgres with it),
  `DB_POOL_SIZE` and `TOMCAT_MAX_THREADS` (Spring's defaults are 10 and 200).
- LXD metrics for Prometheus (once):
  ```bash
  sudo lxc config set core.metrics_address 127.0.0.1:8444
  sudo lxc config set core.metrics_authentication false
  ```
- Monitoring on the laptop: `docker compose --profile monitoring up -d prometheus postgres-exporter`
  (stop any other project's Prometheus first; both use ports 9090 and 9187).
- [k6](https://k6.io) (tested with v2.3), Node.js 24 and `jq` on the laptop.
- `seed.sh`, `check.sh`, `profile.sh` and `diagnose.sh` reach into the VM with `sudo lxc exec`, so they
  ask for the sudo password. `VM=local` points `seed.sh` and `check.sh` at the local compose stack.

async-profiler is downloaded automatically by `profile.sh` into `loadtest/.tools/` on first use.

## Running a test

### 1. Prepare

1. Make one change per run. It is tested uncommitted and committed after its runs together with its
   results, so the run file records the previous commit with `+changes`.
2. Deploy the backend if its code or configuration changed. The image is built on the laptop (a build
   on the VM's 1 CPU is slow) and loaded into the VM, together with the project files:
   ```bash
   tar -c $(git ls-files -co --exclude-standard) .env | sudo lxc exec seat-lock -- sh -c 'mkdir -p /root/seat-lock && tar -x -C /root/seat-lock'
   docker compose build backend
   docker save seat-lock-backend | sudo lxc exec seat-lock -- docker load
   sudo lxc exec seat-lock -- sh -c 'cd /root/seat-lock && docker compose up -d postgres backend'
   ```
3. Seed the dataset: `loadtest/seed.sh`. Reseed when `book`/`mixed` run out of seats and after
   `show-create`. `seatmap` and `flash-sale` don't need a reseed.

### 2. Find the capacity

```bash
taskset -c 0-9 node loadtest/find-capacity.mjs <test> "what changed"
# event test: warm-up, then one sale
PEAK_USERS=200 taskset -c 0-9 node loadtest/find-capacity.mjs flash-sale "what changed"
```

The script runs k6 in steps of increasing rate and reports the highest rate that passes a sustained run:

1. **Warm-up** (not judged): 50, 100 and 200 req/s for 30 s each, then 1-minute steps at 300 req/s until
   the backend's CPU per request changes by less than 5% between two steps (the JIT has finished).
2. **Ramp**: starts at about 90% of the previous result for the same test and grows 1.1× per step (the
   very first run starts at 50 and doubles). Steps are 30 s.
3. **Refine**: binary search between the last passing and the first failing rate, on a 10 req/s grid
   (1 req/s for `show-create`). Steps get longer (1 minute) once within 5% of the limit.
4. **Confirm**: holds the best rate for 2 minutes. If it fails, the rate is lowered by 5% and confirmed
   again. Only the confirmed rate is reported.

After each step the script queries Prometheus and records where the time went per request, how saturated
each resource was, and how many SQL statements each request ran. The run is saved as
`results/runs/<id>.json`; `results.mjs` then regenerates its report (`runs/<id>.md`) and
`results/RESULTS.md` from all saved runs, so changing the report format and running
`node loadtest/results.mjs` updates every past run too. Rows of runs whose JSON is not on this machine
(`runs/*.json` is git-ignored) are kept as they are. Each failed step lists the criteria it missed;
RESULTS.md describes every test, its pass criteria, and what limited each run.
For a change that was reverted, set `"reverted": true` in its run JSON and note why: "vs previous" then
compares the next run with the last kept result.

After booking runs, `loadtest/check.sh` confirms that seats and bookings agree (every count 0).

### Single run at a fixed rate

```bash
RATE=200 DURATION=1m taskset -c 0-9 k6 run loadtest/book.js
```

All scripts read `RATE` (req/s), `DURATION` and `BASE_URL` (default `http://10.24.21.250:8080`, the VM).

## Profiling

`profile.sh` records a CPU profile of the backend with async-profiler while k6 runs at a fixed rate:

```bash
taskset -c 0-9 loadtest/profile.sh <test> <rate> <seconds> "what changed"
taskset -c 0-9 loadtest/profile.sh book 300 60 "baseline"
```

- Always profile a test at the same rate and duration so profiles can be compared.
- Profile below the capacity limit, so the profile shows the normal cost of a request rather than
  queueing.
- After a rebuild, warm up the JVM first: `RATE=300 DURATION=4m k6 run -q loadtest/<test>.js`
- If k6 misses the rate or drops requests, `top.txt` is marked `INVALID PROFILE`.

Each profile is saved in `results/profiles/<date>-<test>-<rate>rps/`:

| File              | Contents                                                                |
| ----------------- | ----------------------------------------------------------------------- |
| `flamegraph.html` | Interactive flame graph; open in a browser                              |
| `top.txt`         | Total samples, CPU by layer, socket I/O share, top methods by self time |
| `collapsed.txt`   | One line per stack with its sample count, for custom analysis           |
| `profile.jfr`     | Raw recording                                                           |

To compare two recordings as a differential flame graph:

```bash
loadtest/.tools/async-profiler-4.5-linux-x64/bin/jfrconv --cpu --diff a.jfr b.jfr diff.html
```

## Diagnosing where the time goes

`diagnose.sh` explains a time breakdown that the run file only shows as totals (for example why holding a
DB connection takes ms when the SQL takes µs). It runs k6 at a steady rate and, during the same 60 s
window, measures from three sides:

```bash
loadtest/diagnose.sh <test> <rate> <seconds> "note"
loadtest/diagnose.sh book 300 180 "where does DB hold time go"
```

| Side      | Source                                                                           | Shows                                                                                       |
| --------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| Postgres  | `pg_stat_activity` sampled every 10 ms, `pg_stat_wal` with `track_wal_io_timing` | What app connections do: run SQL, wait on WAL or locks (row locks in `flash-sale`), or idle   |
| VM kernel | `/proc/stat`, `/proc/pressure/{cpu,io}`, `vmstat`, `/proc/<pid>/stat`            | CPU split incl. iowait and steal; time tasks waited for the CPU or for I/O; CPU per process |
| JVM       | async-profiler wall-clock profile                                                | What the Tomcat request threads wait on (pool, Postgres reply, CPU)                         |

It runs k6 with `taskset` itself and saves `summary.md` plus the raw files in
`results/diagnose/<date>-<test>-<rate>rps/`. The sampler and the wall profiler use a little of the VM's
CPU, so run it below capacity, not for capacity numbers.

## Reading the results

**Capacity runs** vary by a few percent with no code change. Treat single-run differences under about
10% with care, and use capacity runs for large changes and final numbers.

**Profiles** are far more precise for small optimizations: fewer total samples in `top.txt` for the same
rate and duration means cheaper requests.

**Bottlenecks** show up in the run file:

- _Hardware bound_: the VM sits at its CPU limit (VM CPU near 1) while the time goes into real work.
- _Software bound_: latency breaks the target with CPU to spare. The time breakdown shows where requests
  wait (queue, connection pool) and the saturation table shows which resource is full.

## Files

| Path                     | Description                                                                         |
| ------------------------ | ----------------------------------------------------------------------------------- |
| `seatmap.js`, `book.js`, `mixed.js`, `show-create.js` | k6 capacity scenarios                                  |
| `flash-sale.js`          | k6 event scenario (flash sale)                                                      |
| `lib.js`                 | Shared k6 helpers: options and per-request metrics, Zipf, seat and booking id formulas |
| `seed.sh`, `seed.sql`    | Seed the dataset and write `data/seed.json`                                         |
| `check.sh`               | Seat and booking consistency check (all counts must be 0)                           |
| `find-capacity.mjs`      | Capacity search; saves `results/runs/<id>.json`                                     |
| `results.mjs`            | Generates the run reports and `results/RESULTS.md` from `results/runs/*.json`       |
| `profile.sh`             | CPU profiling with async-profiler; writes `results/profiles/`                       |
| `diagnose.sh`            | Time breakdown from Postgres, the VM kernel and the JVM; writes `results/diagnose/` |
| `results/RESULTS.md`     | History of all runs, one section per test with what it does and its pass criteria   |
| `results/runs/`          | Per run: `.md` report with every step, time breakdown, SQL and saturation (in git) and the `.json` record it is generated from (git-ignored) |
| `results/profiles/`      | Per profile: flame graph and summaries (git-ignored)                                |
| `data/`                  | `seed.json` written by `seed.sh` (git-ignored)                                      |
| `../monitoring/`         | Prometheus scrape config (VM backend, exporter, LXD) and `pg_stat_statements` setup |
