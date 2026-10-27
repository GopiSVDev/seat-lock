// Finds the highest request rate a k6 test can sustain within the latency/error target.
//
//   node loadtest/find-capacity.mjs <test> [note]
//   node loadtest/find-capacity.mjs book "baseline"
//
// <test> is a k6 script in this folder (book -> book.js) that reads RATE and DURATION.
// Tests with a very different cost per request start from their own defaults (TEST_DEFAULTS).
//
// Event tests (MODE=event, e.g. flash-sale) are not searched: after the warm-up (with WARMUP_TEST)
// they run once at PEAK_USERS users/s for EVENT_DURATION and pass if no seat was oversold and
// errors and dropped iterations stay under the limits. Latency is reported, not judged.
//
// How it searches:
//   1. warm-up   not judged. Gentle start (50, 100, 200 req/s for 30s each, so a cold JVM isn't
//                flooded), then 1-minute steps at WARMUP_RATE until the backend's CPU cost per
//                request changes less than WARMUP_TOLERANCE (5%) between two steps, i.e. the JIT
//                has finished optimizing. After a restart this takes several minutes;
//                on an already-warm backend it stops after two steps.
//   2. ramp      raise the rate step by step until a step misses the target. If this test has a
//                previous result, it starts at ~90% of it and grows 1.1x per step; otherwise it
//                starts at 50 and doubles. START_RATE / GROWTH override both.
//   3. refine    binary search between the last passing and the first failing rate, down to
//                RESOLUTION req/s (rates are multiples of it). Coarse steps are short; once the gap is within PRECISION (5%)
//                steps get longer, because a small overload needs time to build a visible queue.
//                (warm-up + ramp + refine stop after SEARCH_BUDGET_MINUTES)
//   4. confirm   hold the best rate for CONFIRM_DURATION (2 min); if it misses, lower it by BACKOFF (5%)
//                and try again
//
// A step passes when: p90 <= P90_MS, p95 <= P95_MS, p99 <= P99_MS (over all requests, and p95/p99 for each
// request type, so slow bookings can't hide behind fast reads), error rate < MAX_ERROR_RATE and
// k6 dropped < 1% of iterations. Only the scenario's own requests count (tag load:yes), not setup().
// A failed step records which of these it missed (results.mjs failures).
// After each step the numbers for that step are pulled from Prometheus and saved with it:
// where the time went per request (queue, DB connection wait, DB work, Java code) and saturation
// (threads, pool, GC, CPU, memory). The VM at its CPU limit = hardware bound;
// latency rising with CPU to spare = software bound.
// Results go to loadtest/results/: runs/<id>.json is the record; runs/<id>.md and RESULTS.md are generated
// from all saved runs by results.mjs (node loadtest/results.mjs rebuilds them).
//
// The app runs in the LXD VM (VM name, BASE_URL); k6 and Prometheus run on the laptop.
// Backend CPU comes from the JVM, VM CPU and memory from LXD's metrics endpoint.

import { spawnSync, execSync } from 'node:child_process';
import { mkdirSync, readFileSync, readdirSync, writeFileSync, existsSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { failures, pct, round, targetText, verdict, writeResultsPage, writeRunReport } from './results.mjs';

const [test, note = ''] = process.argv.slice(2);

// Show scheduling writes 250 rows per request, so it runs at a few req/s instead of hundreds
const TEST_DEFAULTS = {
  'show-create': { START_RATE: 2, RESOLUTION: 1, WARMUP_RATE: 2 },
  'flash-sale': { MODE: 'event', WARMUP_TEST: 'mixed' },
};
const env = (name, fallback) => process.env[name] ?? TEST_DEFAULTS[test]?.[name] ?? fallback;
const config = {
  mode: env('MODE', 'capacity'),
  peakUsers: Number(env('PEAK_USERS', 200)),
  eventDuration: env('EVENT_DURATION', '1m'),
  warmupTest: env('WARMUP_TEST', test),
  p90Ms: Number(env('P90_MS', 150)),
  p95Ms: Number(env('P95_MS', 200)),
  p99Ms: Number(env('P99_MS', 500)),
  maxErrorRate: Number(env('MAX_ERROR_RATE', 0.01)),
  startRate: Number(env('START_RATE', 50)),
  growth: Number(env('GROWTH', 2)),
  maxRate: Number(env('MAX_RATE', 20000)),
  precision: Number(env('PRECISION', 0.05)),
  resolution: Number(env('RESOLUTION', 10)),
  stepDuration: env('STEP_DURATION', '30s'),
  fineStepDuration: env('FINE_STEP_DURATION', '1m'),
  searchBudgetMinutes: Number(env('SEARCH_BUDGET_MINUTES', 20)),
  confirmDuration: env('CONFIRM_DURATION', '2m'),
  backoff: Number(env('BACKOFF', 0.95)),
  maxConfirmAttempts: Number(env('MAX_CONFIRM_ATTEMPTS', 5)),
  warmupRate: Number(env('WARMUP_RATE', 300)),
  warmupDuration: env('WARMUP_DURATION', '1m'),
  warmupTolerance: Number(env('WARMUP_TOLERANCE', 0.05)),
  warmupMaxSteps: Number(env('WARMUP_MAX_STEPS', 8)),
  cooldownSeconds: Number(env('COOLDOWN_SECONDS', 10)),
  baseUrl: env('BASE_URL', 'http://10.24.21.250:8080'),
  vm: env('VM', 'seat-lock'),
  prometheusUrl: env('PROMETHEUS_URL', 'http://localhost:9090/api/v1/write'),
  prometheusQueryUrl: env('PROMETHEUS_QUERY_URL', 'http://localhost:9090/api/v1/query'),
};

const here = dirname(fileURLToPath(import.meta.url));
const script = join(here, `${test}.js`);
const warmupScript = join(here, `${config.warmupTest}.js`);
const isEvent = config.mode === 'event';
if (!test || !existsSync(script) || !existsSync(warmupScript)) {
  console.error('usage: node loadtest/find-capacity.mjs <test> [note]   (e.g. book)');
  process.exit(1);
}

const startedAt = new Date();
// Previous stable rate for this test, from the newest saved run that found one.
function previousResult() {
  const runsDir = join(here, 'results', 'runs');
  if (!existsSync(runsDir)) return null;
  for (const file of readdirSync(runsDir).filter((f) => f.endsWith(`-${test}.json`)).sort().reverse()) {
    const run = JSON.parse(readFileSync(join(runsDir, file), 'utf8'));
    if (run.final) return { rate: run.final.rate, id: run.id };
  }
  return null;
}
const previous = previousResult();
if (previous && process.env.START_RATE == null) {
  config.startRate = Math.max(Math.floor((previous.rate * 0.9) / config.resolution) * config.resolution, config.resolution);
  if (process.env.GROWTH == null) config.growth = 1.1;
}

const runId = `${startedAt.toISOString().slice(0, 16).replace(/[:T]/g, '-')}-${test}`;
const steps = [];

function sleep(seconds) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, seconds * 1000);
}

function runStep(phase, rate, duration, stepScript = script) {
  const summaryFile = join(tmpdir(), `k6-${runId}-${steps.length}.json`);
  const args = ['run', '-q', `--summary-export=${summaryFile}`, '--tag', `testid=${runId}`];
  if (config.prometheusUrl) args.push('-o', 'experimental-prometheus-rw');
  const unit = phase === 'event' ? 'users/s' : 'req/s';
  process.stdout.write(`${phase.padEnd(8)} ${String(rate).padStart(6)} ${unit} for ${duration.padEnd(3)} ... `);

  const k6 = spawnSync('k6', [...args, stepScript], {
    stdio: ['ignore', 'ignore', 'inherit'],
    env: {
      ...process.env,
      RATE: String(rate),
      DURATION: duration,
      BASE_URL: config.baseUrl,
      K6_PROMETHEUS_RW_SERVER_URL: config.prometheusUrl,
      K6_PROMETHEUS_RW_TREND_STATS: 'avg,p(90),p(95),p(99),max',
    },
  });
  if (!existsSync(summaryFile)) {
    console.error(`\nk6 failed (exit ${k6.status}) and wrote no summary`);
    process.exit(1);
  }

  const m = JSON.parse(readFileSync(summaryFile, 'utf8')).metrics;
  rmSync(summaryFile);
  // The scenario's own requests (load:yes), without setup() and teardown()
  const d = m['http_req_duration{load:yes}'] ?? m.http_req_duration ?? {};
  const reqs = m['http_reqs{load:yes}'] ?? m.http_reqs ?? {};
  const iterations = m.iterations?.count ?? 0;
  const dropped = m.dropped_iterations?.count ?? 0;
  const step = {
    phase,
    rate,
    duration,
    achievedRate: round(reqs.rate ?? 0),
    requests: reqs.count ?? 0,
    avgMs: round(d.avg),
    p90Ms: round(d['p(90)']),
    p95Ms: round(d['p(95)']),
    p99Ms: round(d['p(99)']),
    maxMs: round(d.max),
    errorRate: (m['http_req_failed{load:yes}'] ?? m.http_req_failed)?.value ?? 0,
    droppedRate: iterations + dropped > 0 ? dropped / (iterations + dropped) : 0,
    byRequest: requestBreakdown(m),
  };
  // Seats confirmed to clients vs seats marked sold afterwards (flash-sale); a difference is an oversell
  if (m.seats_sold) {
    step.sale = {
      users: iterations,
      booked: m.users_booked?.count ?? 0,
      soldOut: m.users_sold_out?.count ?? 0,
      gaveUp: m.users_gave_up?.count ?? 0,
      attempts: m.booking_attempts?.count ?? 0,
      seatsBooked: m.seats_booked?.count ?? 0,
      seatsSold: m.seats_sold.count,
      seatsTotal: m.seats_total?.count ?? 0,
    };
    // Last successful booking counts as the sell-out only if every seat was sold
    const lastBookingS = m.booked_after_open?.max == null ? null : round(m.booked_after_open.max / 1000);
    step.sale.soldOutAfterS = step.sale.seatsSold === step.sale.seatsTotal ? lastBookingS : 'not sold out';
    step.sale.oversold = step.sale.seatsBooked - step.sale.seatsSold;
  }
  const missed = failures(step, config);
  step.pass = missed === null ? null : missed.length === 0;

  // Prometheus scrapes every 5s, so wait before reading the step's window.
  const endSeconds = Date.now() / 1000;
  sleep(Math.max(config.cooldownSeconds, 7));
  step.metrics = collectMetrics(duration, endSeconds, step.avgMs);
  steps.push(step);

  const t = step.metrics;
  console.log(
    `${verdict(step).toUpperCase().padEnd(4)}  p90 ${step.p90Ms}ms  p95 ${step.p95Ms}ms  p99 ${step.p99Ms}ms  ` +
      `errors ${pct(step.errorRate)}  dropped ${pct(step.droppedRate)}`,
  );
  if (step.pass === false) console.log(`${' '.repeat(37)}missed: ${missed.join(', ')}`);
  console.log(
    `${' '.repeat(37)}avg ${step.avgMs}ms = queue ${t.queueMs ?? '?'} + conn wait ${t.connWaitMs ?? '?'} + ` +
      `db ${t.connHoldMs ?? '?'} + java ${t.javaMs ?? '?'}  |  cpu backend ${t.cpuBackend ?? '?'} db ${t.cpuDb ?? '?'}  |  ` +
      `sql ${t.dbStatementsPerRequest ?? '?'}/req`,
  );
  if (step.byRequest.length > 1) {
    console.log(`${' '.repeat(37)}${step.byRequest.map((r) => `${r.label} p95 ${r.p95Ms}/p99 ${r.p99Ms}ms`).join('  ')}`);
  }
  const sale = step.sale;
  if (sale) {
    console.log(
      `${' '.repeat(37)}users ${sale.users}: booked ${sale.booked}, saw sold out ${sale.soldOut}, gave up ${sale.gaveUp}  |  ` +
        `sold out after ${sale.soldOutAfterS ?? '?'}s  |  seats booked ${sale.seatsBooked}, sold ${sale.seatsSold}` +
        (sale.oversold === 0 ? '' : `  OVERSOLD ${sale.oversold}`),
    );
  }
  return step;
}

// Latency per request type (and status where the script splits it), from the k6 submetrics
// exported by lib.js baseOptions: keys like 'http_req_duration{name:book,status:201}'.
function requestBreakdown(m) {
  return Object.keys(m)
    .map((key) => key.match(/^http_req_duration\{(name:[^}]+)\}$/)?.[1])
    .filter((tags) => tags && !tags.includes('name:setup'))
    .map((tags) => {
      const d = m[`http_req_duration{${tags}}`];
      const label = tags.replace('name:', '').replace(',status:', ' ');
      return {
        label,
        count: m[`http_reqs{${tags}}`]?.count ?? 0,
        avgMs: round(d.avg),
        p95Ms: round(d['p(95)']),
        p99Ms: round(d['p(99)']),
      };
    })
    .filter((r) => r.count > 0);
}

function sh(cmd) {
  try {
    return execSync(cmd, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch {
    return '';
  }
}

// All series of an instant query: [{ metric: {labels}, value: number }]
function promQueryAll(expr, timeSeconds) {
  const out = sh(
    `curl -s --get --data-urlencode 'query=${expr}' --data-urlencode 'time=${timeSeconds}' ${config.prometheusQueryUrl}`,
  );
  try {
    return JSON.parse(out)
      .data.result.map((r) => ({ metric: r.metric, value: Number(r.value[1]) }))
      .filter((r) => Number.isFinite(r.value));
  } catch {
    return []; // Prometheus not reachable or no data
  }
}

function promQuery(expr, timeSeconds) {
  return promQueryAll(expr, timeSeconds)[0]?.value ?? null;
}

// The dashboard's numbers for one step, averaged (or max) over the step's window.
function collectMetrics(duration, endSeconds, k6AvgMs) {
  const w = duration;
  const app = 'uri!~"/actuator.*"';
  const db = 'datname!~"postgres|template.*"';
  const requests = `sum(increase(http_server_requests_seconds_count{${app}}[${w}]))`;
  const perRequestMs = (sumMetric) => `1000 * sum(increase(${sumMetric}[${w}])) / ${requests}`;
  const vm = `name="${config.vm}"`;
  const mb = 1024 * 1024;
  const queries = {
    serverAvgMs: perRequestMs(`http_server_requests_seconds_sum{${app}}`),
    serverP95Ms: `1000 * histogram_quantile(0.95, sum by (le) (increase(http_server_requests_seconds_bucket{${app}}[${w}])))`,
    connWaitMs: perRequestMs('hikaricp_connections_acquire_seconds_sum'),
    connHoldMs: perRequestMs('hikaricp_connections_usage_seconds_sum'),
    inPostgresMs: perRequestMs(`pg_stat_statements_seconds_total{${db}}`),
    dbStatementsPerRequest: `sum(increase(pg_stat_statements_calls_total{${db}}[${w}])) / ${requests}`,
    tomcatBusyMax: `max_over_time(sum(tomcat_threads_busy_threads)[${w}:5s])`,
    tomcatThreads: 'max(tomcat_threads_config_max_threads)',
    poolActiveMax: `max_over_time(sum(hikaricp_connections_active)[${w}:5s])`,
    poolPendingMax: `max_over_time(sum(hikaricp_connections_pending)[${w}:5s])`,
    poolSize: 'max(hikaricp_connections_max)',
    gcPausePct: `100 * sum(rate(jvm_gc_pause_seconds_sum[${w}]))`,
    heapMaxMb: `max_over_time(sum(jvm_memory_used_bytes{area="heap"})[${w}:5s]) / ${mb}`,
    // process_cpu_usage is the JVM's share of all CPUs; times the CPU count = cores used
    cpuBackend: `avg_over_time(max(process_cpu_usage)[${w}:5s]) * max(system_cpu_count)`,
    cpuVm: `sum(rate(lxd_cpu_seconds_total{${vm},mode!~"idle|iowait"}[${w}]))`,
    memVmMb: `max_over_time((sum(lxd_memory_MemTotal_bytes{${vm}}) - sum(lxd_memory_MemAvailable_bytes{${vm}}))[${w}:5s]) / ${mb}`,
    dbCacheHitPct: `100 * sum(increase(pg_stat_database_blks_hit{${db}}[${w}])) / (sum(increase(pg_stat_database_blks_hit{${db}}[${w}])) + sum(increase(pg_stat_database_blks_read{${db}}[${w}])))`,
  };

  const m = {};
  for (const [key, expr] of Object.entries(queries)) {
    const value = promQuery(expr, endSeconds);
    m[key] = value == null ? null : key.startsWith('cpu') ? Math.round(value * 1000) / 1000 : round(value);
  }
  // Request time split; the four parts add up to k6's average.
  const diff = (a, ...rest) => (a == null || rest.some((x) => x == null) ? null : round(Math.max(a - rest.reduce((x, y) => x + y, 0), 0)));
  m.queueMs = diff(k6AvgMs, m.serverAvgMs);
  m.javaMs = diff(m.serverAvgMs, m.connWaitMs, m.connHoldMs);
  // Everything in the VM except the JVM: mostly Postgres, plus Docker and the OS
  m.cpuDb = m.cpuVm == null || m.cpuBackend == null ? null : Math.max(Math.round((m.cpuVm - m.cpuBackend) * 1000) / 1000, 0);

  // Each SQL statement: how often it runs per request and its average time inside Postgres.
  // Counters are per queryid; the SQL text comes from the exporter's pg_stat_statements_query_id
  // mapping (needs --collector.stat_statements.include_query).
  // Statements running less than once per 100 requests (exporter, health checks) are left out.
  const calls = `sum by (queryid) (increase(pg_stat_statements_calls_total{${db}}[${w}]))`;
  const perRequest = promQueryAll(`${calls} / scalar(${requests})`, endSeconds);
  const avgMs = promQueryAll(
    `1000 * sum by (queryid) (increase(pg_stat_statements_seconds_total{${db}}[${w}])) / ${calls}`,
    endSeconds,
  );
  const text = new Map(
    promQueryAll('max by (queryid, query) (pg_stat_statements_query_id)', endSeconds).map((r) => [
      r.metric.queryid,
      r.metric.query,
    ]),
  );
  m.statements = perRequest
    .filter((r) => r.value >= 0.01)
    .sort((a, b) => b.value - a.value)
    .map((r) => {
      const ms = avgMs.find((a) => a.metric.queryid === r.metric.queryid)?.value;
      return {
        queryid: r.metric.queryid,
        query: (text.get(r.metric.queryid) ?? `queryid ${r.metric.queryid}`).replace(/\s+/g, ' '),
        perRequest: Math.round(r.value * 100) / 100,
        avgMs: ms == null ? null : Math.round(ms * 100) / 100,
      };
    });
  return m;
}

// The VM's CPUs (as the JVM sees them) and memory (as LXD sees it)
function vmLimits() {
  const now = Date.now() / 1000;
  const cpus = promQuery('max(system_cpu_count)', now);
  const memory = promQuery(`sum(lxd_memory_MemTotal_bytes{name="${config.vm}"})`, now);
  if (cpus == null) return null;
  return { cpus, memoryMb: memory == null ? '?' : Math.round(memory / 1024 / 1024) };
}

// --- search ---

// Backend CPU time per request in ms; drops while the JIT is still optimizing, then levels off.
function cpuMsPerRequest(step) {
  const cpu = step.metrics.cpuBackend;
  return cpu == null || !step.achievedRate ? null : Math.round(((cpu * 1000) / step.achievedRate) * 100) / 100;
}

function warmUp() {
  for (const rate of [50, 100, 200].filter((r) => r < config.warmupRate)) runStep('warmup', rate, '30s', warmupScript);
  let previous = null;
  for (let i = 0; i < config.warmupMaxSteps; i++) {
    const cost = cpuMsPerRequest(runStep('warmup', config.warmupRate, config.warmupDuration, warmupScript));
    if (cost == null) {
      console.log('Warm-up: no CPU data from Prometheus, continuing after one step');
      return;
    }
    const change = previous ? Math.abs(cost - previous) / previous : null;
    console.log(
      `${' '.repeat(37)}warm-up: ${cost} ms CPU per request` +
        (change == null ? '' : ` (${(change * 100).toFixed(1)}% change)`),
    );
    if (change != null && change < config.warmupTolerance) return;
    previous = cost;
  }
  console.log(`Warm-up: CPU per request still changing after ${config.warmupMaxSteps} steps, continuing anyway`);
}

const target = targetText(config);
// Rates are kept on a grid of RESOLUTION req/s (e.g. 800, 810).
const gridDown = (r) => Math.floor(r / config.resolution) * config.resolution;
const gridUp = (r) => Math.ceil(r / config.resolution) * config.resolution;

console.log(`Target: ${target}`);
if (isEvent) console.log(`Event: ${config.peakUsers} users/s for ${config.eventDuration}, warm-up with ${config.warmupTest}\n`);
else {
  console.log(
    previous && process.env.START_RATE == null
      ? `Previous result ${previous.rate} req/s (${previous.id}): ramp starts at ${config.startRate}, x${config.growth} per step\n`
      : `Ramp starts at ${config.startRate}, x${config.growth} per step\n`,
  );
}
warmUp();

let final = null;
if (isEvent) {
  const step = runStep('event', config.peakUsers, config.eventDuration);
  if (step.pass) final = step;
} else {
  let lo = 0; // highest passing rate
  let hi = null; // lowest failing rate
  for (let rate = gridUp(config.startRate); hi === null && rate <= config.maxRate; rate = gridUp(rate * config.growth)) {
    if (runStep('ramp', rate, config.stepDuration).pass) lo = rate;
    else hi = rate;
  }
  if (hi === null) console.log(`Never failed up to MAX_RATE=${config.maxRate}; confirming ${lo} req/s`);

  const narrowEnough = () => hi === null || hi - lo <= config.resolution;
  const isFine = () => hi - lo <= Math.max(lo * config.precision, config.resolution);
  const budgetLeft = () => Date.now() - startedAt.getTime() < config.searchBudgetMinutes * 60_000;
  while (!narrowEnough() && budgetLeft()) {
    const mid = Math.max(gridDown((lo + hi) / 2), lo + config.resolution);
    const duration = isFine() ? config.fineStepDuration : config.stepDuration;
    if (runStep('refine', mid, duration).pass) lo = mid;
    else hi = mid;
  }

  if (!narrowEnough()) console.log(`Search budget used up; confirming best passing rate ${lo} req/s`);

  for (let attempt = 0; lo > 0 && attempt < config.maxConfirmAttempts; attempt++) {
    const step = runStep('confirm', lo, config.confirmDuration);
    if (step.pass) {
      final = step;
      break;
    }
    lo = gridDown(lo * config.backoff);
  }
}

// --- save results ---

const result = {
  id: runId,
  test,
  note,
  startedAt: startedAt.toISOString(),
  finishedAt: new Date().toISOString(),
  commit: sh('git rev-parse --short HEAD'),
  uncommittedChanges: sh('git status --porcelain -- backend docker-compose.yml') !== '',
  limits: { vm: vmLimits() },
  config,
  final,
  steps,
};

const resultsDir = join(here, 'results');
const runsDir = join(resultsDir, 'runs');
mkdirSync(runsDir, { recursive: true });
writeFileSync(join(runsDir, `${runId}.json`), JSON.stringify(result, null, 2) + '\n');

writeRunReport(result, runsDir);
writeResultsPage(resultsDir);

if (isEvent) console.log(final ? `\nEvent passed: ${target}` : `\nEvent failed on: ${steps.at(-1).pass === false ? failures(steps.at(-1), config).join(', ') : '?'}`);
else {
  console.log(
    final
      ? `\nStable: ${final.rate} req/s (p90 ${final.p90Ms} / p95 ${final.p95Ms} / p99 ${final.p99Ms} ms over ${config.confirmDuration})`
      : '\nNo stable rate found',
  );
}
console.log(`Saved: loadtest/results/runs/${runId}.md`);
