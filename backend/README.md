# Backend

Spring Boot 4.1 API for Fair Ticketing. Java 21. MySQL 8.4 and Redis 7 come
from the Compose file in the repository root.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # if Maven would otherwise pick another JDK
cd ../   # repository root
docker compose up -d
cd backend
./mvnw spring-boot:run
```

Listens on http://localhost:8080. Environment variables:
[docs/configuration.md](../docs/configuration.md). HTTP routes:
[docs/api.md](../docs/api.md).

To fill dashboards and train the demand model, start once with
`FT_SEED_ENABLED=true` ([docs/seed-data.md](../docs/seed-data.md)). To have
forecasts turn the waiting room on for HIGH-demand shows, run
[ml-service](../ml-service/README.md) on :8090 and set
`FT_FORECAST_ON_START=true` (or `POST /api/admin/forecasts/run` as admin).
`FT_WAITING_ROOM_ENABLED=true` is the global switch; the forecast decides
which events use the room.

## Tests

```bash
./mvnw test      # unit tests, no Docker
./mvnw verify    # unit tests plus *IT against a real MySQL and Redis
```

Integration tests need a real database because the behaviour that matters
(row locks, unique indexes, oversell checks) does not exist in H2.

GitHub Actions starts MySQL and Redis as job services and sets
`FT_IT_EXTERNAL=true` so the suite talks to localhost. Locally, `mvn verify`
still uses Testcontainers. On Colima:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./mvnw verify
```

`AbstractCheckoutConcurrencyIT` puts 500 buyers in a race for 100 tickets
and asserts that exactly 100 sell, and that everyone else was turned away
for being too late rather than because the system dropped the request. It
runs once per inventory strategy. `BuyingTicketsApiIT` drives the same flow
over HTTP. `WaitingRoomCheckoutIT` checks that a queue-jumper cannot buy.
`WaitlistCheckoutIT` checks that a cancelled ticket is held for the next
person in line.
