// Turns saved runs (results/runs/<id>.json) into the readable reports: runs/<id>.md and RESULTS.md.
// find-capacity.mjs calls it after every run; run it directly to rebuild all reports from the JSON:
//
//   node loadtest/results.mjs

import { readFileSync, readdirSync, writeFileSync, existsSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// What each test sends, shown above its results (and in the order of RESULTS.md)
export const TESTS = {
  seatmap:
    '`GET /shows/{id}/seats` on 250-seat shows; a few popular shows get most requests (Zipf). ' +
    'The read a user makes before booking.',
  book:
    '`POST /bookings` for 2 seats; every request takes different seats, so bookings never collide. ' +
    'The cost of one booking: conditional UPDATE of the seats plus INSERT of the booking and its seats, in one transaction.',
  mixed:
    'Browsing and booking together: 30% seat map, 20% show, 15% movie, 5% theatre, 5% screen, ' +
    '10% get booking, 15% book (bookings never collide).',
  'show-create':
    '`POST /shows` on a 250-seat screen, which also creates the 250 show seats in the same transaction. Admin path.',
  'flash-sale':
    'A new 1,000-seat show opens and new users arrive at a fixed rate (Users/s). Each user loads the seat map, ' +
    'thinks 0-3 s, books 1-4 adjacent free seats near the centre, and on a 409 starts again, up to 3 bookings. ' +
    'Runs once, after warming up the JVM with `mixed`.',
};

export function targetText(config) {
  const limits = `errors < ${pct(config.maxErrorRate)}, dropped < 1%`;
  return config.mode === 'event'
    ? `no seat oversold, ${limits}, over ${config.eventDuration} at ${config.peakUsers} users/s ` +
        '(latency, sell-out and 409s are reported, not judged)'
    : `p90 <= ${config.p90Ms} ms, p95 <= ${config.p95Ms} ms, p99 <= ${config.p99Ms} ms ` +
        `(also p95/p99 per request type), ${limits}`;
}

// Criteria a judged step missed, e.g. "p95 597.3 > 200 ms"; null for warm-up steps (not judged)
export function failures(step, config) {
  if (step.phase === 'warmup') return null;
  const missed = [];
  if (step.errorRate >= config.maxErrorRate) missed.push(`errors ${pct(step.errorRate)} >= ${pct(config.maxErrorRate)}`);
  if (step.droppedRate >= 0.01) missed.push(`dropped ${pct(step.droppedRate)} >= 1%`);
  if ((step.sale?.oversold ?? 0) !== 0) missed.push(`oversold ${step.sale.oversold} seats`);
  if (step.phase === 'event') return missed;
  for (const [name, ms, max] of [
    ['p90', step.p90Ms, config.p90Ms],
    ['p95', step.p95Ms, config.p95Ms],
    ['p99', step.p99Ms, config.p99Ms],
  ]) {
    if (ms > max) missed.push(`${name} ${ms} > ${max} ms`);
  }
  for (const r of step.byRequest) {
    for (const [name, ms, max] of [
      ['p95', r.p95Ms, config.p95Ms],
      ['p99', r.p99Ms, config.p99Ms],
    ]) {
      // With a single request type its numbers repeat the overall ones
      const repeat = step.byRequest.length === 1 && missed.some((m) => m.startsWith(`${name} `));
      if (ms > max && !repeat) missed.push(`${r.label} ${name} ${ms} > ${max} ms`);
    }
  }
  return missed;
}

export function verdict(step) {
  return step.pass === null ? 'done' : step.pass ? 'pass' : 'fail';
}

export function round(n) {
  return n == null ? null : Math.round(n * 10) / 10;
}

export function pct(n) {
  return `${(n * 100).toFixed(2)}%`;
}

const cell = (v) => v ?? '-';
const ofLimit = (v, max) => `${cell(v)} / ${cell(max)}`;
const cpuCell = (used, vm) => `${used ?? '-'} (${vm ? vm.cpus : '?'})`;
const short = (missed, max = 3) =>
  missed.length > max ? `${missed.slice(0, max).join(', ')} +${missed.length - max} more` : missed.join(', ');
const resultCell = (step, config) => (step.pass === false ? `fail: ${failures(step, config).join(', ')}` : verdict(step));

// The failed step at the lowest rate above the stable one: what stopped the search going higher
function limitedBy(run) {
  const failed = run.steps
    .filter((s) => s.pass === false && (!run.final || s.rate > run.final.rate))
    .sort((a, b) => a.rate - b.rate)[0];
  return failed ? `${failed.rate} req/s (${failed.phase}, ${failed.duration}): ${short(failures(failed, run.config))}` : '-';
}

export function writeRunReport(run, runsDir) {
  const { test, config, steps, final } = run;
  const isEvent = config.mode === 'event';
  const vm = run.limits.vm;
  const eventStep = steps.find((s) => s.phase === 'event');
  // SQL breakdown is shown for the confirmed step, or the last judged step if nothing was confirmed.
  const sqlStep = final ?? [...steps].reverse().find((s) => s.pass !== null);
  const timeRows = steps.map((s) => {
    const t = s.metrics;
    return `| ${s.phase} | ${s.rate} | ${verdict(s)} | ${s.avgMs} | ${cell(t.queueMs)} | ${cell(t.connWaitMs)} | ${cell(t.connHoldMs)} | ${cell(t.inPostgresMs)} | ${cell(t.javaMs)} | ${cell(t.serverAvgMs)} | ${cell(t.serverP95Ms)} | ${cell(t.dbStatementsPerRequest)} |`;
  });
  const saturationRows = steps.map((s) => {
    const t = s.metrics;
    return `| ${s.phase} | ${s.rate} | ${verdict(s)} | ${ofLimit(t.tomcatBusyMax, t.tomcatThreads)} | ${ofLimit(t.poolActiveMax, t.poolSize)} | ${cell(t.poolPendingMax)} | ${cell(t.gcPausePct)} | ${cell(t.cpuVm)} | ${cell(t.cpuBackend)} | ${cell(t.cpuDb)} | ${cell(t.memVmMb)} | ${cell(t.heapMaxMb)} | ${cell(t.dbCacheHitPct)} |`;
  });
  const stepRows = steps.map(
    (s) =>
      `| ${s.phase} | ${s.rate} | ${s.duration} | ${s.achievedRate} | ${s.avgMs} | ${s.p90Ms} | ${s.p95Ms} | ${s.p99Ms} | ${pct(s.errorRate)} | ${pct(s.droppedRate)} | ${resultCell(s, config)} |`,
  );
  writeFileSync(
    join(runsDir, `${run.id}.md`),
    [
      isEvent
        ? `# ${test}: ${final ? 'passed' : 'failed'} at ${config.peakUsers} users/s`
        : `# ${test}: ${final ? `${final.rate} req/s` : 'no stable rate found'}`,
      '',
      run.note ? `> ${run.note}\n` : '',
      `- Date: ${run.startedAt}`,
      `- Commit: ${run.commit}${run.uncommittedChanges ? ' (+changes)' : ''}`,
      ...(TESTS[test] ? [`- Test: ${TESTS[test]}`] : []),
      `- Pass: ${targetText(config)}`,
      isEvent
        ? `- Failed on: ${eventStep && eventStep.pass === false ? failures(eventStep, config).join(', ') : '-'}`
        : `- Limited by: ${limitedBy(run)}`,
      `- VM: ${vm ? `${vm.cpus} CPU / ${vm.memoryMb} MB` : 'n/a'} (backend and Postgres share it)`,
      '',
      '## Latency (k6)',
      '',
      'Phases: warm-up (not judged), ramp (raise the rate until a step fails), refine (binary search between',
      'the last pass and the first fail), confirm (hold the best rate for longer), event (flash-sale only).',
      '',
      '| Phase | Target req/s | Duration | Achieved req/s | Avg ms | p90 ms | p95 ms | p99 ms | Errors | Dropped | Result |',
      '|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---|',
      ...stepRows,
      '',
      '## Where the time went (avg ms per request)',
      '',
      'Queued + network + Waiting for DB connection + Holding DB connection + Java code = k6 avg.',
      '"Inside Postgres" is the part of "Holding DB connection" spent executing SQL.',
      '',
      '| Phase | req/s | Result | k6 avg | Queued + network | Waiting for DB connection | Holding DB connection | Inside Postgres | Java code | Server avg | Server p95 | SQL statements / request |',
      '|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|',
      ...timeRows,
      '',
      `## SQL per request (${sqlStep ? `${sqlStep.phase} at ${sqlStep.rate} req/s` : 'no data'})`,
      '',
      'Every statement Postgres ran, per HTTP request. Each one is a round trip unless the driver batches it',
      '(pgjdbc sends BEGIN together with the first statement of a transaction).',
      '',
      '| Statement | Per request | Avg ms in Postgres |',
      '|---|---:|---:|',
      ...(sqlStep?.metrics.statements ?? []).map(
        (q) => `| \`${q.query.replace(/\|/g, '\\|')}\` | ${q.perRequest} | ${cell(q.avgMs)} |`,
      ),
      '',
      '## Latency by request type (k6)',
      '',
      '| Phase | req/s | Result | Request | Count | Avg ms | p95 ms | p99 ms |',
      '|---|---:|---|---|---:|---:|---:|---:|',
      ...steps.flatMap((s) =>
        s.byRequest.map((r) => `| ${s.phase} | ${s.rate} | ${verdict(s)} | ${r.label} | ${r.count} | ${r.avgMs} | ${r.p95Ms} | ${r.p99Ms} |`),
      ),
      '',
      ...(steps.some((s) => s.sale)
        ? [
            '## Sale',
            '',
            'Users = sessions started. "Sold out after" = last successful booking, from the sale opening.',
            'Seats booked = seats in 201 responses; seats sold = BOOKED seats in the seat map afterwards.',
            'Any difference is an oversell.',
            '',
            '| Phase | Users/s | Result | Users | Booked | Saw sold out | Gave up | Booking attempts | Sold out after s | Seats booked | Seats sold | Oversold |',
            '|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|',
            ...steps
              .filter((s) => s.sale)
              .map((s) => {
                const x = s.sale;
                return `| ${s.phase} | ${s.rate} | ${verdict(s)} | ${x.users} | ${x.booked} | ${x.soldOut} | ${x.gaveUp} | ${x.attempts} | ${cell(x.soldOutAfterS)} | ${x.seatsBooked} | ${x.seatsSold} | ${x.oversold} |`;
              }),
            '',
          ]
        : []),
      '## Saturation',
      '',
      'Max values over the step, except CPU (average cores), GC (% of time paused) and cache hit (%).',
      '"DB + OS CPU" is VM CPU minus backend CPU: Postgres plus Docker and the OS.',
      '',
      '| Phase | req/s | Result | Tomcat threads busy / max | DB pool active / size | Pool waiting | GC pause % | VM CPU | Backend CPU | DB + OS CPU | VM mem MB | Heap MB | DB cache hit % |',
      '|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|',
      ...saturationRows,
      '',
    ].join('\n'),
  );
}

function capacityRow(run, previousRate, latest) {
  const f = run.final;
  const change = f && previousRate ? (f.rate - previousRate) / previousRate : null;
  return [
    run.startedAt.slice(0, 10),
    f ? f.rate : 'none',
    change == null ? '-' : `${change >= 0 ? '+' : ''}${(change * 100).toFixed(1)}%`,
    f?.p90Ms ?? '-',
    f?.p95Ms ?? '-',
    f?.p99Ms ?? '-',
    f ? pct(f.errorRate) : '-',
    limitedBy(run),
    cpuCell(f?.metrics.cpuBackend, run.limits.vm),
    cpuCell(f?.metrics.cpuDb, run.limits.vm),
    noteCell(run, latest),
    `[report](runs/${run.id}.md)`,
  ];
}

function eventRow(run, latest) {
  const s = run.steps.find((x) => x.phase === 'event');
  if (!s) return null;
  const p95Of = (label) => s.byRequest.find((r) => r.label === label)?.p95Ms ?? '-';
  return [
    run.startedAt.slice(0, 10),
    s.rate,
    verdict(s),
    s.pass === false ? failures(s, run.config).join(', ') : '-',
    cell(s.sale?.users),
    cell(s.sale?.booked),
    cell(s.sale?.gaveUp),
    cell(s.sale?.soldOutAfterS),
    cell(s.sale?.oversold),
    p95Of('seatmap'),
    p95Of('book 201'),
    p95Of('book 409'),
    pct(s.errorRate),
    pct(s.droppedRate),
    cpuCell(s.metrics.cpuBackend, run.limits.vm),
    cpuCell(s.metrics.cpuDb, run.limits.vm),
    noteCell(run, latest),
    `[report](runs/${run.id}.md)`,
  ];
}

// A run judged against other limits than the latest one is not comparable: say so in its note
function noteCell(run, latest) {
  const target = targetText(run.config);
  return target === targetText(latest.config) ? run.note : `${run.note} (pass: ${target})`.trim();
}

const CAPACITY_HEADER = [
  '| Date | Stable req/s | vs previous | p90 ms | p95 ms | p99 ms | Errors | Limited by | CPU backend | CPU db | Note | Run |',
  '|---|---:|---:|---:|---:|---:|---:|---|---|---|---|---|',
];
const EVENT_HEADER = [
  '| Date | Users/s | Result | Failed on | Users | Booked | Gave up | Sold out after s | Oversold | p95 seat map ms | p95 book 201 ms | p95 book 409 ms | Errors | Dropped | CPU backend | CPU db | Note | Run |',
  '|---|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|',
];

export function writeResultsPage(resultsDir) {
  const runs = loadRuns(join(resultsDir, 'runs'));
  const { kept, passLines } = keptRows(join(resultsDir, 'RESULTS.md'), new Set(runs.map((r) => r.id)));
  const order = (t) => (t in TESTS ? Object.keys(TESTS).indexOf(t) : Infinity);
  const tests = [...new Set([...runs.map((r) => r.test), ...kept.map((k) => k.test)])].sort(
    (a, b) => order(a) - order(b) || a.localeCompare(b),
  );
  const lines = [
    '# Load test results',
    '',
    'Generated from `runs/*.json` by `node loadtest/results.mjs` (also after every `find-capacity.mjs` run).',
    "A row's report link has every step of that run: latency, where the time went, SQL, saturation.",
    '',
    '- **Capacity tests** search for the highest request rate the app sustains. Each step sends a fixed rate',
    '  and passes when it meets the Pass criteria of its section. **Stable req/s** is the highest rate that also',
    '  passed a 2-minute confirm run; **Limited by** is the failed step just above it and what it missed.',
    '- **Event tests** (flash-sale) run once at a fixed arrival rate of users. **Result** is pass/fail against',
    '  the Pass criteria; **Failed on** lists what missed.',
    '- **Dropped**: iterations k6 could not start on time because all its virtual users were still waiting for',
    '  responses, i.e. the app could not keep up with the offered rate.',
    '- **CPU** columns: average cores used during the confirm run (capacity) or the event, VM limit in brackets.',
    '  CPU db is VM CPU minus backend CPU: Postgres plus Docker and the OS.',
    '- Setup: the app (PostgreSQL + backend) runs in an LXD VM with 1 vCPU (pinned to one host core) and 2 GB',
    '  RAM; k6 and monitoring run on the host, on other cores.',
  ];
  for (const test of tests) {
    const testRuns = runs.filter((r) => r.test === test);
    const entries = [...testRuns.map((run) => ({ id: run.id, run })), ...kept.filter((k) => k.test === test)].sort(
      (a, b) => a.id.localeCompare(b.id),
    );
    const latest = testRuns.at(-1);
    const isEvent = (latest?.config.mode ?? TEST_MODES[test]) === 'event';
    let previousRate = null;
    const rows = entries.map(({ run, line, stableRate }) => {
      if (!run) {
        previousRate = stableRate ?? previousRate;
        return line;
      }
      const cells = isEvent ? eventRow(run, latest) : capacityRow(run, previousRate, latest);
      previousRate = run.final?.rate ?? previousRate;
      return cells && `| ${cells.join(' | ')} |`;
    });
    lines.push(
      '',
      `## ${test}`,
      '',
      ...(TESTS[test] ? [TESTS[test], ''] : []),
      ...(latest ? [`Pass: ${targetText(latest.config)}.`, ''] : passLines.has(test) ? [passLines.get(test), ''] : []),
      ...(isEvent ? EVENT_HEADER : CAPACITY_HEADER),
      ...rows.filter(Boolean),
    );
  }
  writeFileSync(join(resultsDir, 'RESULTS.md'), lines.join('\n') + '\n');
}

const TEST_MODES = { 'flash-sale': 'event' };

// Rows already in RESULTS.md for runs whose JSON is not on this machine (runs/*.json is git-ignored): kept as they are,
// along with each test's Pass line for tests with no saved run left
function keptRows(historyFile, savedIds) {
  const kept = [];
  const passLines = new Map();
  if (!existsSync(historyFile)) return { kept, passLines };
  let test = null;
  for (const line of readFileSync(historyFile, 'utf8').split('\n')) {
    test = line.match(/^## (\S+)/)?.[1] ?? test;
    if (test && line.startsWith('Pass: ')) passLines.set(test, line);
    const id = line.match(/\]\(runs\/([^)]+)\.md\)/)?.[1];
    if (!test || !id || savedIds.has(id)) continue;
    const stableRate = Number(line.split('|')[2]);
    kept.push({ id, test, line, stableRate: Number.isFinite(stableRate) && stableRate > 0 ? stableRate : null });
  }
  return { kept, passLines };
}

function loadRuns(runsDir) {
  if (!existsSync(runsDir)) return [];
  return readdirSync(runsDir)
    .filter((f) => f.endsWith('.json'))
    .sort()
    .map((f) => JSON.parse(readFileSync(join(runsDir, f), 'utf8')));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const resultsDir = join(dirname(fileURLToPath(import.meta.url)), 'results');
  const runsDir = join(resultsDir, 'runs');
  for (const run of loadRuns(runsDir)) writeRunReport(run, runsDir);
  writeResultsPage(resultsDir);
  console.log(`Rebuilt ${join('loadtest', 'results', 'RESULTS.md')} and the run reports`);
}
