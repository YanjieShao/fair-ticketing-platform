# Architecture

A modular monolith. Splitting into microservices would add operational work
without buying anything at this size. The one process that lives on its own
is the Python model service, because it is a different language runtime.

```
backend/src/main/java/com/fairticketing/
├── common/         errors, clock, rate limiting
├── auth/           registration, login, JWT, roles
├── event/          artists, venues, events, search
├── inventory/      ticket tiers and three interchangeable reservers
├── waitingroom/    virtual waiting room
├── order/          checkout, state machine, expiry
├── payment/        mock payment provider
├── waitlist/       FIFO queue, offers, conversion
├── analytics/      aggregates behind the dashboard
├── ai/             forecasting, insight generation, recommendations
├── notification/   transactional and insight-driven messages
```

`audit_logs` exists in the schema for later, but there is no audit package
in v1. Stock movements are already append-only in `inventory_ledger`.

## Inventory: three implementations, on purpose

`InventoryReserver` is implemented three ways, selected by
`FT_INVENTORY_STRATEGY`, so the load test can measure them instead of
relying on an argument about which is faster:

| Strategy | How stock is held |
| --- | --- |
| `DB_PESSIMISTIC_LOCK` | `SELECT ... FOR UPDATE`, read, decide, write |
| `DB_CONDITIONAL_UPDATE` | one `UPDATE` whose `WHERE` clause is the oversell guard |
| `REDIS_LUA` | atomic decrement in a Lua script, reconciled against the database |

Every strategy has to pass the same concurrency test. Redis holds the
authoritative counter on the hot path; the database keeps the durable
record, and a scheduled job reconciles the two, treating the ledger as
correct.

## Checkout takes the contended lock first

Inserting an order row takes a shared lock on the ticket tier it references,
because of the foreign key. Reserving stock needs an exclusive lock on that
same row. Doing them in that order means concurrent buyers each sit on a
shared lock while waiting to upgrade it, which is a deadlock: an early
version of this code lost 400 of 500 sales that way.

Reserving before inserting makes every transaction take the contended row
first, so buyers queue instead of colliding. The ledger entry is written
after the order exists; both happen in one transaction, so they cannot
disagree.

## Rate limits are per account, not per IP

Checkout, waitlist join, and waiting-room join share a Redis counter keyed
by user id. Defaults are 8 checkouts a minute, 20 joins a minute, and 5 of
those writes in any 10 seconds. The short window is the anomaly detector: a
script on one account trips 429 `RATE_LIMITED` before a person retrying a
slow page would. Browse and waiting-room polling are not capped. The 10k
stampede sends one request per buyer, so it does not hit this path.

## Every stock movement is written down

`inventory_ledger` is append-only and records the reason for each change.
Once Redis owns the hot counter, this is what reconciliation replays to
decide which side drifted.

## Time is injected

Nothing calls `LocalDateTime.now()` directly. A `java.time.Clock` bean is
injected everywhere, so payment windows and offer expiry can be tested
without sleeping. Instants are stored in UTC and shown in the venue's
timezone.

## Model inference is off the hot path

Demand forecasting runs as a scheduled batch job that writes to
`demand_forecasts`. Request handling only reads that table, so the Python
service being slow or down cannot affect checkout. A HIGH forecast is what
turns `waiting_room_enabled` on for that event.

Sales insights work the same way. The backend computes sold percent, hours
on sale, and waitlist pressure; an LLM (or a template if `OPENAI_API_KEY`
is blank) only writes the paragraph. The prompt forbids inventing numbers,
and copy that does not cite the computed sold percent is discarded. Results
land in `ai_insights` and are what the event page and admin Insights view
read.

Waitlist recommendations are content-based because a new platform has no
purchase graph. Same genre is required; city, category, and price within
30% only rank the shortlist. The scorer is a plain Java class, and checkout
never calls it.

The admin dashboard is the same idea in a different shape: JDBC aggregates
sell-through, waitlist pressure, paid revenue, order status, category mix,
and the last 14 days of paid tickets. Recharts only draws those arrays.

## Notifications are in-process

There is no Kafka topic and no outbox table in v1. Order, waitlist, and
insight code call `NotificationService` directly. Each write runs in a
`REQUIRES_NEW` transaction so a duplicate-key miss does not roll back the
purchase. Transactional copy is a template; insight copy is a paragraph the
analytics job already produced. `payloadJson` and `generatedBy` stay on the
row. Dedup is the unique `dedupe_key`.

Prometheus and Grafana would graph *process* metrics (request rate, p99,
JVM, MySQL) for operators. They are not the sales dashboard and stay
optional. How images are built and how a VM runs them:
[ci.md](ci.md), [deploy.md](deploy.md).

Admins can list a show from `/admin/events/new`. Only a draft can be taken
down; once tickets are on sale, cancellation is out of scope. Buyers read
waitlist offers from `/notifications` as well as the waitlist page.
