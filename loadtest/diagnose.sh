#!/usr/bin/env bash
# Measures where a request's time goes while k6 runs at a steady rate, from three sides at once:
#
#   loadtest/diagnose.sh [test] [rate] [duration-seconds] [note]
#   loadtest/diagnose.sh book 300 180 "where does DB hold time go"
#
#   Postgres  pg_stat_activity sampled every 10 ms (what each app connection is doing: running,
#             waiting on WAL/locks, or idle waiting for the JVM) and pg_stat_wal with
#             track_wal_io_timing (WAL syncs per commit, ms per sync)
#   Kernel    VM CPU split from /proc/stat (user, system, softirq, iowait, steal), pressure stall
#             info /proc/pressure/{cpu,io} (time tasks waited for a CPU or for I/O), vmstat run queue,
#             CPU per process name from /proc/<pid>/stat (java, postgres, docker-proxy, kernel threads)
#   JVM       async-profiler wall-clock profile of the Tomcat request threads (what they wait on)
#
# All three cover the same window: MEASURE_SECONDS (default 60) starting after START_SECONDS
# (default 60), so the start and end of the k6 run are left out.
# Saves to loadtest/results/diagnose/<date>-<test>-<rate>rps/ (summary.md + raw files).
set -euo pipefail

TEST=${1:-book}
RATE=${2:-300}
DURATION=${3:-180}
NOTE=${4:-}
START_SECONDS=${START_SECONDS:-60}
MEASURE_SECONDS=${MEASURE_SECONDS:-60}
K6_CPUS=${K6_CPUS:-0-9}
VM=${VM:-seat-lock}
CONTAINER=${CONTAINER:-seat-lock-backend}
PROM=${PROMETHEUS_QUERY_URL:-http://localhost:9090/api/v1/query}
AP_VERSION=4.5

if [ $((START_SECONDS + MEASURE_SECONDS + 10)) -gt "$DURATION" ]; then
  echo "DURATION must be at least START_SECONDS + MEASURE_SECONDS + 10" >&2
  exit 1
fi

here=$(cd "$(dirname "$0")" && pwd)
ap="$here/.tools/async-profiler-$AP_VERSION-linux-x64"
out="$here/results/diagnose/$(date -u +%Y-%m-%d-%H-%M)-$TEST-${RATE}rps"
in_vm=(sudo lxc exec "$VM" --)
# Credentials come from the container's own environment
psql=("${in_vm[@]}" docker exec -i seat-lock-db sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"' psql -X -q -At -F '|')

if [ ! -x "$ap/bin/asprof" ]; then
  echo "Downloading async-profiler $AP_VERSION ..."
  mkdir -p "$here/.tools"
  curl -sSL "https://github.com/async-profiler/async-profiler/releases/download/v$AP_VERSION/async-profiler-$AP_VERSION-linux-x64.tar.gz" |
    tar xz -C "$here/.tools"
fi
mkdir -p "$out"

# --- setup (asks for the sudo password here, before k6 starts) ---
sudo lxc file push -r "$ap" "$VM/tmp/" >/dev/null
"${in_vm[@]}" docker cp -q "/tmp/$(basename "$ap")" "$CONTAINER:/tmp/async-profiler"
"${psql[@]}" -c "ALTER SYSTEM SET track_wal_io_timing = on" -c "SELECT pg_reload_conf()" >/dev/null
restore() {
  kill "${k6_pid:-}" 2>/dev/null || true
  "${psql[@]}" -c "ALTER SYSTEM RESET track_wal_io_timing" -c "SELECT pg_reload_conf()" >/dev/null || true
}
trap restore EXIT

echo "k6: $TEST at $RATE req/s for ${DURATION}s on CPUs $K6_CPUS (measuring ${MEASURE_SECONDS}s after ${START_SECONDS}s)"
RATE=$RATE DURATION="${DURATION}s" \
  taskset -c "$K6_CPUS" k6 run -q --summary-export="$out/k6-summary.json" "$here/$TEST.js" >/dev/null </dev/null &
k6_pid=$!
sleep "$START_SECONDS"

# --- measurement window ---
db_snapshot() {
  "${psql[@]}" -c "SELECT w.wal_records, w.wal_write, w.wal_sync, w.wal_write_time, w.wal_sync_time, d.xact_commit
                   FROM pg_stat_wal w, pg_stat_database d WHERE d.datname = current_database()"
}
kernel_snapshot() {
  "${in_vm[@]}" sh -c 'date +%s%N; head -1 /proc/stat; cat /proc/pressure/cpu /proc/pressure/io'
}
# "pid name user+system ticks" for every process; the name is between the first "(" and the last ")"
proc_snapshot() {
  "${in_vm[@]}" sh -c 'cat /proc/[0-9]*/stat 2>/dev/null' | awk '{
    o = index($0, "("); c = match($0, /\) [A-Za-z] /)
    name = substr($0, o + 1, c - o - 1); gsub(/ /, "_", name)
    split(substr($0, c + 2), f, " ")
    print substr($0, 1, o - 2), name, f[12] + f[13] }'
}

echo "Measuring for ${MEASURE_SECONDS}s ..."
db_before=$(db_snapshot)
kernel_before=$(kernel_snapshot)
proc_snapshot >"$out/proc-before.txt"

# One Postgres session samples pg_stat_activity in a loop (cheap: no new process per sample).
# Only the app's connections (pgjdbc). The stats snapshot is cleared each round, otherwise the
# whole loop would see the first sample (one transaction).
samples=$((MEASURE_SECONDS * 100))
"${psql[@]}" >"$out/pg-activity.txt" <<SQL &
CREATE TEMP TABLE s (state text, wtype text, wevent text);
DO \$\$ BEGIN
  FOR i IN 1..$samples LOOP
    PERFORM pg_stat_clear_snapshot();
    INSERT INTO s SELECT state, wait_event_type, wait_event FROM pg_stat_activity
      WHERE backend_type = 'client backend' AND pid <> pg_backend_pid()
        AND application_name LIKE 'PostgreSQL JDBC%';
    PERFORM pg_sleep(0.01);
  END LOOP;
END \$\$;
SELECT coalesce(state, '?'), coalesce(wtype, '-'), coalesce(wevent, '-'), round(count(*)::numeric / $samples, 3)
FROM s GROUP BY 1, 2, 3 ORDER BY 4 DESC;
SQL
pg_pid=$!

"${in_vm[@]}" vmstat 1 $((MEASURE_SECONDS + 1)) >"$out/vmstat.txt" </dev/null &
vmstat_pid=$!

"${in_vm[@]}" docker exec "$CONTAINER" /tmp/async-profiler/bin/asprof \
  -d "$MEASURE_SECONDS" -e wall -i 5ms -f /tmp/wall.jfr 1 >/dev/null

db_after=$(db_snapshot)
kernel_after=$(kernel_snapshot)
proc_snapshot >"$out/proc-after.txt"
window_end=$(date +%s)
wait "$vmstat_pid" || true
wait "$pg_pid"

"${in_vm[@]}" docker cp -q "$CONTAINER:/tmp/wall.jfr" /tmp/wall.jfr
sudo lxc file pull "$VM/tmp/wall.jfr" "$out/wall.jfr"
sudo chown "$(id -u):$(id -g)" "$out/wall.jfr"
"$ap/bin/jfrconv" --wall --threads -o collapsed "$out/wall.jfr" "$out/wall-collapsed.txt"
"$ap/bin/jfrconv" --wall --threads -o html --title "$TEST at $RATE req/s, wall clock" "$out/wall.jfr" "$out/wall-flamegraph.html"

echo "Waiting for k6 to finish ..."
wait "$k6_pid" || true
k6_pid=

# --- summary ---
prom() {
  curl -s --get --data-urlencode "query=$1" --data-urlencode "time=$window_end" "$PROM" |
    jq -r '.data.result[0].value[1] // "?" | if . == "?" then . else (tonumber * 100 | round / 100 | tostring) end'
}
w="${MEASURE_SECONDS}s"
reqs="sum(increase(http_server_requests_seconds_count{uri!~\"/actuator.*\"}[$w]))"
server_ms=$(prom "1000 * sum(increase(http_server_requests_seconds_sum{uri!~\"/actuator.*\"}[$w])) / $reqs")
wait_ms=$(prom "1000 * sum(increase(hikaricp_connections_acquire_seconds_sum[$w])) / $reqs")
hold_ms=$(prom "1000 * sum(increase(hikaricp_connections_usage_seconds_sum[$w])) / $reqs")
req_rate=$(prom "$reqs / $MEASURE_SECONDS")

{
  echo "# Diagnose: $TEST at $RATE req/s ${NOTE:+($NOTE)}"
  echo
  echo "- Date: $(date -u +%Y-%m-%dT%H:%M:%SZ), window ${MEASURE_SECONDS}s after ${START_SECONDS}s of a ${DURATION}s run"
  achieved=$(jq -r '.metrics.http_reqs.rate | floor' "$out/k6-summary.json" 2>/dev/null || echo '?')
  p95=$(jq -r '.metrics.http_req_duration["p(95)"] * 10 | floor / 10' "$out/k6-summary.json" 2>/dev/null || echo '?')
  dropped=$(jq -r '.metrics.dropped_iterations.count // 0' "$out/k6-summary.json" 2>/dev/null || echo 0)
  echo "- k6 (whole run): achieved $achieved req/s, p95 ${p95} ms, dropped $dropped"
  echo "- Backend (window): $req_rate req/s, server avg $server_ms ms, waiting for DB connection $wait_ms ms, holding DB connection $hold_ms ms"
  echo

  echo "## Postgres: what the app's connections were doing"
  echo
  echo "Average number of connections in each state (pool size 10) and the same as ms per request"
  echo "(connections / request rate). 'idle' = connection not running SQL: either back in the pool or"
  echo "held by the JVM between sending and receiving (network, JVM waiting for CPU, Java code)."
  echo "'active' with no wait event = running on the CPU or waiting for one."
  echo
  echo "| State | Wait type | Wait event | Avg connections | ms per request |"
  echo "|---|---|---|---:|---:|"
  awk -F'|' -v rate="$req_rate" 'NF == 4 {
    ms = (rate + 0 > 0) ? sprintf("%.3f", 1000 * $4 / rate) : "?"
    printf "| %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, ms }' "$out/pg-activity.txt"
  echo

  echo "## Postgres: WAL"
  echo
  paste -d'|' <(echo "$db_before") <(echo "$db_after") | awk -F'|' -v win="$MEASURE_SECONDS" '{
    records = $7 - $1; writes = $8 - $2; syncs = $9 - $3; write_ms = $10 - $4; sync_ms = $11 - $5; commits = $12 - $6
    printf "- Commits: %d (%.0f/s)\n", commits, commits / win
    printf "- WAL records: %d (%.2f per commit)\n", records, records / commits
    printf "- WAL writes: %d (%.2f per commit), %.3f ms each, %.3f ms per commit\n", writes, writes / commits, writes ? write_ms / writes : 0, write_ms / commits
    printf "- WAL syncs: %d (%.2f per commit), %.3f ms each, %.3f ms per commit\n", syncs, syncs / commits, syncs ? sync_ms / syncs : 0, sync_ms / commits
    printf "- Time spent syncing WAL: %.1f%% of the window (one process at a time)\n", 100 * sync_ms / (win * 1000)
  }'
  echo

  echo "## VM kernel"
  echo
  # Lines: before = 1 time, 2 /proc/stat cpu, 3-6 pressure (cpu some, cpu full, io some, io full);
  # after = 7 time, 8 /proc/stat cpu, 9-12 pressure. /proc/stat: user nice system idle iowait irq softirq steal
  printf '%s\n%s\n' "$kernel_before" "$kernel_after" | awk '
    NR == 1 { t0 = $1 } NR == 7 { t1 = $1 }
    NR == 2 { for (i = 2; i <= 9; i++) a[i] = $i }
    NR == 8 { for (i = 2; i <= 9; i++) b[i] = $i }
    $1 == "some" || $1 == "full" { split($NF, kv, "="); v[NR] = kv[2] }
    END {
      secs = (t1 - t0) / 1e9; total = 0
      for (i = 2; i <= 9; i++) { d[i] = b[i] - a[i]; total += d[i] }
      printf "VM CPU over %.1fs (1 vCPU): user %.1f%%, system %.1f%%, softirq %.1f%%, irq %.1f%%, iowait %.1f%%, steal %.1f%%, idle %.1f%%\n\n",
        secs, 100*(d[2]+d[3])/total, 100*d[4]/total, 100*d[8]/total, 100*d[7]/total, 100*d[6]/total, 100*d[9]/total, 100*d[5]/total
      printf "Pressure stall (share of time at least one task was waiting):\n\n"
      printf "- CPU some: %.1f%% (a runnable task had to wait for the CPU)\n", 100 * (v[9] - v[3]) / (secs * 1e6)
      printf "- IO some: %.1f%%, IO full: %.1f%% (all tasks blocked on I/O)\n", 100 * (v[11] - v[5]) / (secs * 1e6), 100 * (v[12] - v[6]) / (secs * 1e6)
    }'
  awk 'NR > 3 && $1 ~ /^[0-9]+$/ { n++; r += $1; if ($1 > max) max = $1 }
       END { if (n) printf "- Run queue (vmstat r): avg %.1f, max %d tasks running or waiting for the 1 CPU\n", r / n, max }' "$out/vmstat.txt"
  echo
  echo "CPU per process name over the window (user + system, % of the 1 vCPU; softirq work is mostly"
  echo "charged to whichever process was running). Processes that exited during the window are missing."
  echo
  echo "| Process | Processes | CPU % |"
  echo "|---|---:|---:|"
  hz=$("${in_vm[@]}" getconf CLK_TCK)
  awk -v hz="$hz" -v win="$MEASURE_SECONDS" '
    NR == FNR { before[$1] = $3; next }
    { d = $3 - before[$1]; if (d <= 0) next; cpu[$2] += d; n[$2]++ }
    END { for (p in cpu) printf "| %s | %d | %.1f |\n", p, n[p], 100 * cpu[p] / hz / win }' \
    "$out/proc-before.txt" "$out/proc-after.txt" | sort -t'|' -k4 -rn | head -15
  echo

  echo "## JVM: where the Tomcat request threads spent their time (wall clock)"
  echo
  echo "Share of non-idle request-thread time and, times server avg ($server_ms ms), ms per request."
  echo "'Java code' includes time a runnable thread waited for the CPU (see CPU pressure above)."
  echo
  echo "| Category | Share | ≈ ms per request |"
  echo "|---|---:|---:|"
  awk -v server="$server_ms" '
    { cnt = $NF; $NF = ""; split($0, f, ";"); if (f[1] !~ /http-nio.*exec/) next
      s = $0
      if (s ~ /ThreadPoolExecutor\.getTask|TaskQueue\.(take|poll)/) next
      if (s ~ /HikariPool\.getConnection/) c = "Waiting for a DB connection (pool)"
      else if (s ~ /org\/postgresql/ && s ~ /(read0|NioSocketImpl\.(park|timedRead)|Net\.poll)/) c = "Waiting for Postgres reply (socket read)"
      else if (s ~ /org\/postgresql/ && s ~ /write0/) c = "Sending to Postgres (socket write)"
      else if (s ~ /org\/postgresql/) c = "JDBC driver code"
      else if (s ~ /(read0|write0|writev0)/) c = "HTTP socket I/O"
      else c = "Java code (running or waiting for CPU)"
      sum[c] += cnt; busy += cnt }
    END { for (c in sum) printf "| %s | %.1f%% | %s |\n", c, 100 * sum[c] / busy,
            (server + 0 > 0) ? sprintf("%.2f", server * sum[c] / busy) : "?" | "sort -t\"|\" -k3 -rn"
          close("sort -t\"|\" -k3 -rn") }' "$out/wall-collapsed.txt"
  echo
  echo "Top 15 innermost frames of non-idle request-thread time:"
  echo
  echo '```'
  awk '{ cnt = $NF; $NF = ""; split($0, f, ";"); if (f[1] !~ /http-nio.*exec/) next
         if ($0 ~ /ThreadPoolExecutor\.getTask|TaskQueue\.(take|poll)/) next
         n = split($0, g, ";"); leaf[g[n]] += cnt; busy += cnt }
       END { for (l in leaf) printf "%6.2f%%  %s\n", 100 * leaf[l] / busy, l }' "$out/wall-collapsed.txt" |
    sort -rn | head -15
  echo '```'
} >"$out/summary.md"

echo "Saved: ${out#"$here/../"}/summary.md"
