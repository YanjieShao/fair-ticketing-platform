# Fair Ticketing Platform

A concert ticketing system built around the problems that break real on-sales:
tickets vanishing in seconds, overselling, duplicate orders, bots, and fans
left with no idea where they stand.

## What "fair" means here

The name commits the system to three mechanisms, each of which is testable:

1. **The waiting room admits people in arrival order.** When an on-sale is
   expected to be oversubscribed, buyers are queued and released at a
   controlled rate instead of racing each other.
2. **The waitlist is strictly first in, first out.** Inventory returned by a
   cancelled, expired, or mock-returned order goes to the head of the queue,
   who gets an exclusive, time-boxed window to buy before it passes on.
3. **Purchase limits, rate limits, and idempotency remove the bot advantage.**
   A script cannot obtain more than a human, and a retried request cannot
   produce a second order.

## Quickstart

### Requirements

- Java 21
- Maven 3.9+ (or the wrapper in `backend/`)
- Node 22+
- Python 3.12+ (only if you want the demand model)
- Docker (Colima, Docker Desktop, or any engine Compose and Testcontainers can reach)

### Running

```bash
git clone <your-fork-url>
cd fair-ticketing-platform
cp .env.example .env   # then set FT_JWT_SECRET
docker compose up -d
cd backend
./mvnw spring-boot:run
```

If Maven is not using Java 21:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
```

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` to the backend on 8080.

Sign in as `admin@fairticketing.local` / `password123` for the dashboard, or
create a buyer account from the UI. The admin row is created on startup if it
is missing.

`docker-compose.yml` uses local demo credentials (`ticketing` / `ticketing`).
Do not reuse them anywhere public.

To run the API and UI as images instead of Vite (nginx on port 80, `/api`
proxied to the backend):

```bash
cp .env.example .env   # first time; includes FT_SEED_ENABLED=true
docker compose -f docker-compose.yml -f docker-compose.app.yml up --build
```

Open http://localhost (not :5173). The first start fills the dashboard from
synthetic sales history and can take a few minutes; later starts skip if
the data is already there. Then sign in as admin and use **Generate now**
on Insights to write the briefings (that button does not create sales).
Details: [docs/seed-data.md](docs/seed-data.md). A cloud VM uses the same
overlay: [docs/deploy.md](docs/deploy.md).

The demand model and waiting room stay optional:
[backend/README.md](backend/README.md),
[ml-service/README.md](ml-service/README.md).

### Architecture

A modular monolith. The Python model is the only separate process, because it
is a different language runtime. Checkout never calls it.

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

Inventory strategies, lock order, and why inference stays off the hot path:
[docs/architecture.md](docs/architecture.md).

### Testing

```bash
cd backend && ./mvnw test          # unit tests + 95% line coverage gate
cd backend && ./mvnw verify        # also the MySQL/Redis integration tests
cd frontend && npm run lint        # oxlint
cd frontend && npm test            # Vitest
cd frontend && npm test -- --coverage   # src/api and src/auth, 95% lines
cd frontend && npm run e2e         # Playwright; needs the API on :8080
cd ml-service && pytest
```

Pull requests run on GitHub Actions. A `Jenkinsfile` is a second, optional
runner (unit tests and image builds only). Details: [docs/ci.md](docs/ci.md).

Colima, Testcontainers, and what the concurrency tests actually assert:
[backend/README.md](backend/README.md).

## Further reading

| Topic | Where |
| --- | --- |
| Environment variables | [docs/configuration.md](docs/configuration.md) |
| HTTP API and error codes | [docs/api.md](docs/api.md) |
| Synthetic sales history | [docs/seed-data.md](docs/seed-data.md) |
| How the pieces fit | [docs/architecture.md](docs/architecture.md) |
| 10k-buyer stampede | [docs/load-test.md](docs/load-test.md) |
| CI (GitHub Actions and Jenkins) | [docs/ci.md](docs/ci.md) |
| Docker images and cloud VM | [docs/deploy.md](docs/deploy.md) |
