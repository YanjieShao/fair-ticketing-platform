# Load test report

Measured on 14 August 2026 against a single hot ticket tier. The README
headline is **30,000 tickets / 10,000 concurrent buyers / zero oversell /
checkout p99 < 200 ms**. This document records what actually happened on
this laptop, how to reproduce it, and why p99 missed.

Raw client output lives next to the harness:

- [`load-test/results-pessimistic.txt`](../load-test/results-pessimistic.txt)
- [`load-test/results-conditional.txt`](../load-test/results-conditional.txt)
- [`load-test/results-redis.txt`](../load-test/results-redis.txt)

## Method

The in-process suite `AbstractCheckoutConcurrencyIT` (500 threads vs 100
tickets, all three `InventoryReserver` implementations) remains the
**correctness** check. This report is the **HTTP stampede**: virtual threads,
one `CountDownLatch`, HTTP/1.1, every buyer posting `/api/orders` at the same
instant.

Three profiles in `load-test/run.sh`:

| Profile | Buyers | Stock | Why it exists |
| --- | --- | --- | --- |
| `smoke` | 500 | 100 | Same shape as the concurrency IT, over the real HTTP stack |
| `contention` | 10,000 | 3,000 | Demand exceeds supply; this is the HTTP oversell proof |
| `target` | 10,000 | 30,000 | The README headline; everyone can succeed |

Source of truth after a run is `GET /api/admin/load-test/result/{tierId}`, not
the client's HTTP 201 count. `oversold` is true when `orderCount > total` or
`remaining < 0`. For `REDIS_LUA`, `reservedQuantity` on the tier row lags
until reconciliation; `remaining` is the Redis counter and `orderCount` is
the durable sale count.

The waiting room stays **off**. The point of this stampede is to hit one SKU
at once; the waiting room exists to prevent that in production.

## Machine and knobs

| Item | Value |
| --- | --- |
| Host | macOS, 8 cores, 8 GiB RAM |
| Colima VM | 2 CPU, 4 GiB, MySQL 8.4 (`ft-mysql`) + Redis 7 (`ft-redis`) |
| JDK | Homebrew OpenJDK 21.0.11 |
| App | Spring Boot 4.1.0, `spring.threads.virtual.enabled=true` |
| Date | 2026-08-14 |

Knobs that made 10k requests complete instead of dying on connect timeouts or
Hikari 500s:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export FT_LOADTEST_ENABLED=true          # never on a public process
export FT_WAITING_ROOM_ENABLED=false
export FT_SEED_ENABLED=false
export FT_INVENTORY_STRATEGY=DB_PESSIMISTIC_LOCK   # or DB_CONDITIONAL_UPDATE / REDIS_LUA
export SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=32
export SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=180000
export FT_TOMCAT_MAX_CONNECTIONS=20000
export FT_TOMCAT_ACCEPT_COUNT=4096
```

A pessimistic lock holds a pool connection while it waits on the tier row, so
**raising the pool does not help**; lengthening `connectionTimeout` does. The
client uses a 60s connect timeout and a 120s request timeout.

Leave `FT_LOADTEST_ENABLED=false` in `.env`. The fixture routes are
`permitAll` when the controller is on the classpath.

## Headline results

Tables below keep the **best complete** run per strategy. Failed attempts
(ephemeral-port exhaustion, see below) stay in the raw files and are not
used as the score.

### Smoke — 500 buyers, 100 tickets

| Strategy | Wall | HTTP 201 | HTTP 409 | Remaining | Oversold | p99 |
| --- | --- | --- | --- | --- | --- | --- |
| `DB_PESSIMISTIC_LOCK` | 2,533 ms | 100 | 400 `SOLD_OUT` | 0 | no | 2,391 ms |
| `DB_CONDITIONAL_UPDATE` | 1,683 ms | 100 | 400 `SOLD_OUT` | 0 | no | 1,568 ms |
| `REDIS_LUA` | 2,278 ms | 100 | 400 `SOLD_OUT` | 0 | no | 2,143 ms |

### Contention — 10,000 buyers, 3,000 tickets

| Strategy | Wall | Sold | Rejected | Remaining | Oversold | p99 |
| --- | --- | --- | --- | --- | --- | --- |
| `DB_PESSIMISTIC_LOCK` | 23.3 s | 3,000 | 6,857 `SOLD_OUT` + 143 connect/IO | 0 | no | 22.7 s |
| `DB_CONDITIONAL_UPDATE` | 30.4 s | 3,000 | 7,000 `SOLD_OUT` | 0 | no | 29.6 s |
| `REDIS_LUA` | 14.2 s | 2,988 | 6,964 `SOLD_OUT` + 36 other | 0 | no | 13.2 s |

Pessimistic and conditional sold **exactly** the stock. Redis sold 12 short
of 3,000 with Redis remaining already 0 (see findings).

### Target — 10,000 buyers, 30,000 tickets

| Strategy | Wall | HTTP 201 | Remaining | Oversold | p50 | p99 | p99 under 200 ms |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `DB_PESSIMISTIC_LOCK` | 66.1 s | 10,000 | 20,000 | no | 35.6 s | 64.0 s | **no** |
| `DB_CONDITIONAL_UPDATE` | 51.3 s | 10,000 | 20,000 | no | 25.3 s | 49.8 s | **no** |
| `REDIS_LUA` (best) | 16.6 s | 9,795 | 20,181 | no | 9.3 s | 15.5 s | **no** |

Redis never landed a clean 10,000/10,000 on this machine. The best attempt
was 9,795 creates, 24 `DUPLICATE_ACTIVE_ORDER`, and ~180 connect/IO failures.
Stock did not go negative.

## Why p99 is not under 200 ms

The stampede is **one hot row** (or one Redis key, then 10k inserts) with
the waiting room off. Client latency includes time spent queued behind
everyone who arrived at the same millisecond.

With an exclusive lock, concurrency on the contended resource is 1. The
last buyers wait for almost the whole wall clock, so **p99 ≈ wall**. On this
run that is tens of seconds, not 200 ms.

A p99 under 200 ms on a true 10k stampede would need a different shape:
waiting-room admission, many independent SKUs, or a much larger machine.
It is not a realistic claim for a laptop Colima VM hitting one tier. The
number stays in the README as the **target**, not as a result.

## Findings worth keeping

1. **Zero oversell held** on every strategy, including contention. That is
   the claim this harness can support.
2. **Conditional update was faster than pessimistic lock** on the complete
   target run (51 s vs 66 s). Both serialize on the tier row; the
   `UPDATE … WHERE remaining >= qty` path spends less time in InnoDB lock
   upgrade.
3. **Redis Lua is the fastest wall clock** because reserve no longer takes
   the MySQL row lock, so inserts can overlap. The same overlap is why this
   laptop's accept queue and ephemeral ports show up here first.
4. **Two 10k stampedes back-to-back fail.** macOS `TIME_WAIT` sockets from
   the first run eat ephemeral ports; the next run returns thousands of
   `ConnectException`s. Wait 30–60 s between `contention` and `target`, or
   retry. The first conditional target (6,325/10,000) and the first Redis
   target (2,831/10,000) are that failure mode, not inventory bugs. Server
   `result` still matched what actually committed.
5. **Hikari 64 × 30 s timeout produced 6,449 HTTP 500s** in an earlier
   pessimistic attempt (`CannotCreateTransactionException`). Pool waiters
   sit behind `FOR UPDATE`; they need a long `connectionTimeout`, not a
   bigger pool.
6. **Redis decrement used to leak on a failed insert.** `saveAndFlush` hitting a
   unique index mapped every integrity error to `DUPLICATE_ACTIVE_ORDER` and
   did not call `inventory.release`. Database strategies rolled the decrement
   back with the transaction; Redis did not. Checkout now releases on that
   path, and order numbers are generated with `ThreadLocalRandom` so virtual
   threads do not share one generator.
7. **`SPRING_TASK_SCHEDULING_ENABLED=false` did not silence jobs** in these
   runs. Reconciliation still logged `No active transaction` on a schedule
   because the `@Scheduled` method called `this.reconcileTier` and skipped
   the proxy. The scheduled method is now `@Transactional` itself.
8. **Per-account rate limits do not apply to this stampede.** Each buyer sends
   one checkout. Caps are 8/minute per user; they are for a script on one
   account, not 10k distinct buyers.

## Reproduce

```bash
# MySQL + Redis already up via docker compose.
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export FT_LOADTEST_ENABLED=true
export FT_WAITING_ROOM_ENABLED=false
export FT_SEED_ENABLED=false
export FT_INVENTORY_STRATEGY=DB_PESSIMISTIC_LOCK
export SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=32
export SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=180000
export FT_TOMCAT_MAX_CONNECTIONS=20000
export FT_TOMCAT_ACCEPT_COUNT=4096

cd backend && ./mvnw -B spring-boot:run
```

In another terminal:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
cd load-test
./run.sh smoke
./run.sh contention
# wait 30–60s for TIME_WAIT to drain
./run.sh target
```

Repeat after restarting the API with `FT_INVENTORY_STRATEGY` set to
`DB_CONDITIONAL_UPDATE` and `REDIS_LUA`. Stop the process when finished so
the fixture routes are not left enabled.
