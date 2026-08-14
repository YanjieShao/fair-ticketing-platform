# Fair Ticketing Platform

A concert ticketing backend built around the problems that break real on-sale
events: tickets vanishing in seconds, overselling, duplicate orders, bots, and
fans left with no idea where they stand.

## What "fair" means here

The name commits the system to three mechanisms, each of which is testable:

1. **The waiting room admits people in arrival order.** When an on-sale is
   expected to be oversubscribed, buyers are queued and released at a
   controlled rate instead of racing each other.
2. **The waitlist is strictly first in, first out.** Inventory returned by a
   cancelled or expired order goes to the head of the queue, who gets an
   exclusive, time-boxed window to buy before it passes on.
3. **Purchase limits, rate limits, and idempotency remove the bot advantage.**
   A script cannot obtain more than a human, and a retried request cannot
   produce a second order.

## Status

A buyer can browse events, hold tickets, pay, and cancel, and unpaid holds are
returned automatically. The waiting room, waitlist, Redis inventory path, and
the forecasting service are not built yet.

## Requirements

- Java 21 (the machine also has JDK 26; Maven must run on 21)
- Maven 3.9+
- Colima + Docker CLI

## Running

Start the infrastructure:

```bash
colima start
docker compose up -d
```

Run the backend:

```bash
cd backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./mvnw spring-boot:run
```

## Testing

Unit tests need nothing but a JVM:

```bash
cd backend
./mvnw test
```

Tests named `*IT` start a real MySQL through Testcontainers and run under
`mvn verify`. On Colima the Docker socket is not where Testcontainers looks by
default, so point it there first:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./mvnw verify
```

Two of them carry most of the weight. `AbstractCheckoutConcurrencyIT` puts 500
buyers in a race for 100 tickets and asserts not only that exactly 100 sell, but
that every buyer who missed out was turned away for being too late rather than
because the system dropped their request; it runs once per inventory strategy.
`BuyingTicketsApiIT` drives the same flow over HTTP through the real security
filters, since the concurrency tests call the service directly and would
otherwise leave the entire web layer unverified.

## API

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/auth/register`, `/api/auth/login` | none | obtain a token |
| GET | `/api/events` | none | search by city, artist, category, date, price |
| GET | `/api/events/{id}` | none | tiers and remaining stock |
| POST | `/api/orders` | buyer | hold tickets, requires `Idempotency-Key` |
| POST | `/api/orders/{orderNo}/pay` | buyer | pay through the mock provider |
| POST | `/api/orders/{orderNo}/cancel` | buyer | release the hold |
| GET | `/api/orders`, `/api/orders/{orderNo}` | buyer | order history |

### Errors

Every failure, including the ones rejected by security filters before any
controller runs, comes back in one shape:

```json
{ "code": "SOLD_OUT", "message": "Not enough tickets left in this tier", "timestamp": "..." }
```

`code` is the stable part and the one clients should branch on. Losing a race
for a ticket is an ordinary outcome rather than a fault, so those answer 409
with a code that says which rule stopped the buyer: `SOLD_OUT`,
`PURCHASE_LIMIT_EXCEEDED`, `DUPLICATE_ACTIVE_ORDER`, `EVENT_NOT_ON_SALE`.
A 500 means the server genuinely broke, and nothing a client can send should
produce one.

## Configuration

Secrets and switches come from the environment; the defaults in
`application.yml` are for local development only and `FT_JWT_SECRET` must be set
to something private before this is deployed anywhere real.

| Variable | Default | Purpose |
| --- | --- | --- |
| `FT_JWT_SECRET` | dev placeholder | HMAC key for access tokens |
| `FT_INVENTORY_STRATEGY` | `DB_PESSIMISTIC_LOCK` | which reserver to use |
| `FT_PAYMENT_FAILURE_RATE` | `0.0` | forces declined payments for demos |
| `FT_SEED_ENABLED` | `false` | generates the synthetic sales history |

## Seed data

A new platform has no sales history, so the forecasting model would have nothing
to learn from and every dashboard would be empty. `SyntheticDataGenerator` builds
that history: artists with a popularity score, venues, past and upcoming events,
ticket tiers, and the individual orders behind them, placed along a sales curve
that front-loads the events in demand.

```bash
FT_SEED_ENABLED=true ./mvnw spring-boot:run
```

It runs once and skips if data is already present. The random seed is fixed, so
the dataset is reproducible. The result separates cleanly along the feature the
model is meant to pick up:

| Artist tier | Tiers | Mean sell-through | Sold out |
| --- | --- | --- | --- |
| Headliner | 91 | 1.00 | 98.9% |
| Long tail | 372 | 0.62 | 18.5% |

Note that sales are censored at capacity: a headliner that sells out tells us
demand was *at least* the house size, not what it actually was. That is precisely
why the model forecasts demand from the features rather than fitting past sales.

## Architecture

A modular monolith. Splitting into microservices would add operational work
without buying anything at this size; the one process that does live on its own
is the Python model service, because it is a different language runtime.

```
backend/src/main/java/com/fairticketing/
├── common/         cross-cutting: errors, clock, idempotency, rate limiting
├── auth/           registration, login, JWT, roles
├── event/          artists, venues, events, search
├── inventory/      ticket tiers and stock, two interchangeable reservers
├── queue/          virtual waiting room
├── order/          checkout, state machine, expiry
├── payment/        mock payment provider
├── waitlist/       FIFO queue, offers, conversion
├── analytics/      aggregates behind the dashboard
├── ai/             forecasting, insight generation, recommendations
├── notification/   transactional and insight-driven messages
└── audit/          who did what, and when
```

### Inventory: three implementations, on purpose

`InventoryReserver` is implemented three ways, selected by
`ticketing.inventory.strategy`, so the load test can measure them instead of
relying on an argument about which is faster:

| Strategy | How stock is held |
| --- | --- |
| `DB_PESSIMISTIC_LOCK` | `SELECT ... FOR UPDATE`, read, decide, write |
| `DB_CONDITIONAL_UPDATE` | one `UPDATE` whose `WHERE` clause is the oversell guard |
| `REDIS_LUA` | atomic decrement in a Lua script, reconciled against the database |

Every strategy has to pass the same concurrency test. The plan is for Redis to
hold the authoritative counter on the hot path, with the database keeping the
durable record and a scheduled job reconciling the two, treating the database as
correct.

### Checkout takes the contended lock first

Inserting an order row takes a shared lock on the ticket tier it references,
because of the foreign key. Reserving stock needs an exclusive lock on that same
row. Doing them in that order means concurrent buyers each sit on a shared lock
while waiting to upgrade it, which is a deadlock: an early version of this code
lost 400 of 500 sales that way.

Reserving before inserting makes every transaction take the contended row first,
so buyers queue instead of colliding. The audit entry is written after the order
exists; both happen in one transaction, so they cannot disagree.

### Every stock movement is written down

`inventory_ledger` is append-only and records the reason for each change. Once
Redis owns the hot counter, this is what reconciliation replays to decide which
side drifted.

### Time is injected

Nothing calls `LocalDateTime.now()` directly. A `java.time.Clock` bean is
injected everywhere, so payment windows and offer expiry can be tested without
sleeping.

### Model inference is off the hot path

Demand forecasting runs as a scheduled batch job that writes to
`demand_forecasts`. Request handling only reads that table, so the Python
service being slow or down cannot affect checkout.

## Load test targets

| Metric | Target |
| --- | --- |
| Inventory | 30,000 tickets |
| Concurrent buyers | 10,000 |
| Oversold tickets | 0 |
| Checkout p99 | < 200 ms |
