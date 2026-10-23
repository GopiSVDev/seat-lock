#!/usr/bin/env bash
# CPU-profiles the backend while a k6 test runs at a steady rate below capacity.
#
#   loadtest/profile.sh [test] [rate] [profile-seconds] [note]
#   loadtest/profile.sh book 300 60 "baseline"
#
# Steps: copies async-profiler into the backend container in the LXD VM, starts k6 at <rate>,
# waits WARMUP_SECONDS (default 30), samples the JVM for <profile-seconds>, then saves to
# loadtest/results/profiles/<date>-<test>-<rate>rps/:
#   flamegraph.html   open in a browser; wider box = more CPU time
#   profile.jfr       raw recording (can diff two runs: jfrconv --diff a.jfr b.jfr out.html)
#   collapsed.txt     one line per stack with sample count
#   top.txt           total samples, CPU by layer, socket I/O share, top methods by self time
set -euo pipefail

TEST=${1:-book}
RATE=${2:-300}
PROFILE_SECONDS=${3:-60}
NOTE=${4:-}
WARMUP_SECONDS=${WARMUP_SECONDS:-30}
CONTAINER=${CONTAINER:-seat-lock-backend}
VM=${VM:-seat-lock}
in_vm=(sudo lxc exec "$VM" --)
AP_VERSION=4.5

here=$(cd "$(dirname "$0")" && pwd)
tools="$here/.tools"
ap="$tools/async-profiler-$AP_VERSION-linux-x64"
out="$here/results/profiles/$(date -u +%Y-%m-%d-%H-%M)-$TEST-${RATE}rps"

if [ ! -x "$ap/bin/asprof" ]; then
  echo "Downloading async-profiler $AP_VERSION ..."
  mkdir -p "$tools"
  curl -sSL "https://github.com/async-profiler/async-profiler/releases/download/v$AP_VERSION/async-profiler-$AP_VERSION-linux-x64.tar.gz" |
    tar xz -C "$tools"
fi

sudo lxc file push -r "$ap" "$VM/tmp/" >/dev/null
"${in_vm[@]}" docker cp -q "/tmp/$(basename "$ap")" "$CONTAINER:/tmp/async-profiler"
mkdir -p "$out"

echo "k6: $TEST at $RATE req/s for $((WARMUP_SECONDS + PROFILE_SECONDS + 5))s (profiling after ${WARMUP_SECONDS}s warm-up)"
RATE=$RATE DURATION="$((WARMUP_SECONDS + PROFILE_SECONDS + 5))s" \
  k6 run -q --summary-export="$out/k6-summary.json" "$here/$TEST.js" >/dev/null &
k6_pid=$!
trap 'kill $k6_pid 2>/dev/null || true' EXIT

sleep "$WARMUP_SECONDS"
echo "Profiling for ${PROFILE_SECONDS}s ..."
# itimer: CPU sampling that works inside containers without perf_events permissions.
# The JVM is PID 1 in the container (ENTRYPOINT java -jar).
"${in_vm[@]}" docker exec "$CONTAINER" /tmp/async-profiler/bin/asprof \
  -d "$PROFILE_SECONDS" -e itimer -i 1ms -f /tmp/profile.jfr 1 >/dev/null
"${in_vm[@]}" docker cp -q "$CONTAINER:/tmp/profile.jfr" /tmp/profile.jfr
sudo lxc file pull "$VM/tmp/profile.jfr" "$out/profile.jfr"
sudo chown "$(id -u):$(id -g)" "$out/profile.jfr"
wait $k6_pid || true
trap - EXIT

"$ap/bin/jfrconv" --cpu -o html --title "$TEST at $RATE req/s ${NOTE:+($NOTE)}" "$out/profile.jfr" "$out/flamegraph.html"
"$ap/bin/jfrconv" --cpu -o collapsed "$out/profile.jfr" "$out/collapsed.txt"

# Self time: the last frame of each stack is the method actually on the CPU.
total=$(awk '{ n += $NF } END { print n }' "$out/collapsed.txt")
{
  echo "$TEST at $RATE req/s, ${PROFILE_SECONDS}s, $total samples ${NOTE:+- $NOTE}"
  echo
  echo "CPU by layer: each sample counted once, under the innermost layer doing the work"
  echo "(compare total samples between profiles at the same rate: fewer = cheaper per request)"
  awk '
    { cnt = $NF; $NF = ""; total += cnt; n = split($0, f, ";"); layer = "Other JVM / JDK"
      for (i = n; i >= 1; i--) { x = f[i]
        if (x ~ /org\/postgresql/) { layer = "Postgres JDBC driver"; break }
        if (x ~ /com\/zaxxer\/hikari/) { layer = "HikariCP"; break }
        if (x ~ /org\/hibernate\/validator/) { layer = "Bean Validation"; break }
        if (x ~ /org\/hibernate/) { layer = "Hibernate ORM"; break }
        if (x ~ /(tools|com\/fasterxml)\/jackson/) { layer = "Jackson JSON"; break }
        if (x ~ /io\/micrometer/) { layer = "Micrometer / observation"; break }
        if (x ~ /org\/springframework\/transaction|JpaTransactionManager/) { layer = "Spring transactions"; break }
        if (x ~ /com\/seatlock/) { layer = "App code (com.seatlock)"; break }
        if (x ~ /org\/springframework/) { layer = "Spring framework (MVC, filters, proxies)"; break }
        if (x ~ /org\/apache\/(coyote|tomcat|catalina)/) { layer = "Tomcat (HTTP handling)"; break }
      }
      sum[layer] += cnt
      if ($0 ~ /SocketDispatcher\.(write0|read0)/) { if ($0 ~ /org\/postgresql/) db += cnt; else http += cnt }
    }
    END {
      for (l in sum) printf "%6.2f%%  %s\n", 100 * sum[l] / total, l | "sort -rn"
      close("sort -rn")
      printf "\nSocket I/O (system calls, included above): DB %.1f%%, HTTP %.1f%%\n", 100 * db / total, 100 * http / total
    }
  ' "$out/collapsed.txt"
  echo
  echo "Top 30 methods by self CPU time"
  awk '{ count = $NF; $NF = ""; n = split($0, f, ";"); self[f[n]] += count }
       END { for (m in self) printf "%6.2f%%  %s\n", 100 * self[m] / '"$total"', m }' "$out/collapsed.txt" |
    sort -rn | head -30
} >"$out/top.txt"

achieved=$(jq -r '.metrics.http_reqs.rate | floor' "$out/k6-summary.json" 2>/dev/null || echo '?')
p95=$(jq -r '.metrics.http_req_duration["p(95)"] * 10 | floor / 10' "$out/k6-summary.json" 2>/dev/null || echo '?')
dropped=$(jq -r '.metrics.dropped_iterations.count // 0' "$out/k6-summary.json" 2>/dev/null || echo 0)
echo "k6 achieved $achieved req/s, p95 ${p95}ms, dropped $dropped"

# A profile only shows the normal cost per request if the backend kept up with the rate.
# Typical cause of a miss: a cold JVM right after a rebuild (JIT still compiling); warm it up first:
#   RATE=300 DURATION=4m k6 run -q loadtest/<test>.js
if [ "$achieved" = '?' ] || [ "$achieved" -lt $((RATE * 98 / 100)) ] || [ "$dropped" -gt 0 ]; then
  warning="INVALID PROFILE: k6 achieved $achieved of $RATE req/s, dropped $dropped requests, p95 ${p95}ms. Backend did not keep up (cold JVM?); warm it up and re-run."
  echo "$warning" >&2
  { echo "$warning"; echo; cat "$out/top.txt"; } >"$out/top.txt.tmp" && mv "$out/top.txt.tmp" "$out/top.txt"
fi
echo "Saved: ${out#"$here/../"}/flamegraph.html"
