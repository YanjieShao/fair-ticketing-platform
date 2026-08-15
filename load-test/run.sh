#!/usr/bin/env bash
# Stampede checkout against a running Fair Ticketing API.
# The API must have been started with FT_LOADTEST_ENABLED=true and the waiting
# room left off. Wait 30–60s between two 10k profiles; macOS TIME_WAIT from
# the first stampede otherwise shows up as ConnectException on the next.
# Usage:
#
#   ./run.sh smoke                         # 500 vs 100, matches the concurrency IT
#   ./run.sh contention                    # 10k vs 3k, demand exceeds supply
#   ./run.sh target                        # 10k vs 30k, the README headline
#   ./run.sh target http://localhost:8080
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PROFILE="${1:-smoke}"
BASE="${2:-http://localhost:8080}"

case "$PROFILE" in
  smoke) BUYERS=500; STOCK=100 ;;
  contention) BUYERS=10000; STOCK=3000 ;;
  target) BUYERS=10000; STOCK=30000 ;;
  *) echo "unknown profile: $PROFILE (smoke|contention|target)" >&2; exit 1 ;;
esac

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="$JAVA_HOME/bin:$PATH"

echo "=== load-test $PROFILE ($BUYERS buyers, $STOCK tickets) against $BASE ==="
curl -sf "$BASE/actuator/health" >/dev/null
java "$ROOT/CheckoutLoadClient.java" "$BASE" "$BUYERS" "$STOCK"
