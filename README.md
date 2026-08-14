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

A buyer can browse events, join a waiting room, hold tickets, pay, and cancel
from the React UI. Unpaid holds are returned automatically. Three inventory
strategies sit behind the same interface and must each pass the same concurrency
test. Cancelled and expired tickets are offered to the waitlist in join order.
A scheduled job forecasts demand for upcoming shows and turns the waiting room
on when expected demand exceeds capacity. LLM insights and waitlist
recommendations are not built yet.

## Requirements

- Java 21
- Maven 3.9+ (or the wrapper in `backend/`)
- Node 22+ (for the UI)
- Python 3.12+ (for the demand model; Anaconda 3.12 on this machine)
- Docker (Colima, Docker Desktop, or any engine Testcontainers can reach)

## Running

```bash
git clone <your-fork-url>
cd fair-ticketing-platform
cp .env.example .env   # then set FT_JWT_SECRET
docker compose up -d
cd backend
./mvnw spring-boot:run
```

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

The Vite server at http://localhost:5173 proxies `/api` to the backend on 8080.

The demand model is a separate process. After seeding, run it and ask the
backend to score upcoming shows:

```bash
cd ml-service
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --port 8090
```

Then, with the backend already running on seeded data:

```bash
export FT_FORECAST_ON_START=true
# or POST /api/admin/forecasts/run as admin@fairticketing.local / password123
```

To see the waiting room actually gate checkout, also set
`FT_WAITING_ROOM_ENABLED=true`. The forecast decides which events use it; the
global switch is the load-test kill switch.

If more than one JDK is installed, point Maven at 21. On this machine that is:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
```

`docker-compose.yml` uses local demo credentials (`ticketing` / `ticketing`).
Do not reuse them anywhere public.

## Testing

Unit tests need nothing but a JVM:

```bash
cd backend
./mvnw test
```

The UI tests run in Vitest and do not need the backend:

```bash
cd frontend
npm test
```

The model service:

```bash
cd ml-service
pytest
```

Tests named `*IT` start a real MySQL and Redis through Testcontainers and run
under `mvn verify`. GitHub Actions already has a Docker socket where
Testcontainers expects it. On Colima, point the tests at the VM first:

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
filters. `WaitingRoomCheckoutIT` checks that a queue-jumper cannot buy.
`WaitlistCheckoutIT` checks that a cancelled ticket is held for the next person
in line rather than whoever hits checkout first.

## API

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/auth/register`, `/api/auth/login` | none | obtain a token |
| GET | `/api/events` | none | search by city, artist, category, date, price |
| GET | `/api/events/{id}` | none | tiers, remaining stock, latest demand forecast |
| POST | `/api/admin/forecasts/run` | admin | score upcoming shows and open waiting rooms |
| POST | `/api/waiting-room/{eventId}/join` | buyer | take a place in line |
| GET | `/api/waiting-room/{eventId}` | buyer | position; polling is what moves the line |
| DELETE | `/api/waiting-room/{eventId}` | buyer | give up a place |
| POST | `/api/waitlist` | buyer | join after a tier sells out |
| GET | `/api/waitlist`, `/api/waitlist/{id}` | buyer | place in line and offer window |
| DELETE | `/api/waitlist/{id}` | buyer | leave the queue |
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
Jumping the waiting room answers 403 with `WAITING_ROOM_TOKEN_REQUIRED`.
A 500 means the server genuinely broke, and nothing a client can send should
produce one.

## Configuration

Secrets and switches come from the environment. The defaults in
`application.yml` are for local development only. Copy `.env.example` and set
`FT_JWT_SECRET` to something private before this is reachable from anywhere
other than your laptop.

| Variable | Default | Purpose |
| --- | --- | --- |
| `FT_JWT_SECRET` | dev placeholder | HMAC key for access tokens |
| `FT_INVENTORY_STRATEGY` | `DB_PESSIMISTIC_LOCK` | which reserver to use |
| `FT_WAITING_ROOM_ENABLED` | `false` | gate checkout behind the queue |
| `FT_PAYMENT_FAILURE_RATE` | `0.0` | forces declined payments for demos |
| `FT_SEED_ENABLED` | `false` | generates the synthetic sales history |
| `FT_CORS_ORIGINS` | `http://localhost:5173` | browser origins allowed to call the API directly |
| `FT_ML_BASE_URL` | `http://localhost:8090` | Python demand model |
| `FT_FORECAST_ON_START` | `false` | run a forecast pass when the backend boots |

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
├── inventory/      ticket tiers and stock, interchangeable reservers
├── waitingroom/    virtual waiting room
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

Every strategy has to pass the same concurrency test. Redis holds the
authoritative counter on the hot path; the database keeps the durable record,
and a scheduled job reconciles the two, treating the ledger as correct.

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
service being slow or down cannot affect checkout. A HIGH forecast is what
turns `waiting_room_enabled` on for that event.

## Load test targets

| Metric | Target |
| --- | --- |
| Inventory | 30,000 tickets |
| Concurrent buyers | 10,000 |
| Oversold tickets | 0 |
| Checkout p99 | < 200 ms |
