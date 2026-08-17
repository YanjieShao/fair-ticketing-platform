# Seed data

A new platform has no sales history, so the forecasting model would have
nothing to learn from and every dashboard would be empty.
`SyntheticDataGenerator` builds that history: artists with a popularity
score, venues, past and upcoming events, ticket tiers, and the individual
orders behind them, placed along a sales curve that front-loads the events
in demand.

```bash
FT_SEED_ENABLED=true ./mvnw spring-boot:run
```

Same switch for the application-image overlay (Compose reads `.env`):

```bash
# .env already has FT_SEED_ENABLED=true when copied from .env.example
docker compose -f docker-compose.yml -f docker-compose.app.yml up --build
```

It runs once and skips if data is already present. The random seed is
`20260814` (see `ticketing.seed` in `application.yml`), so the dataset is
reproducible. Admin **Generate now** only phrases live sales; it does not
create this history. Without a seed (or real orders) the dashboard is zeros.

Defaults:

| Knob | Count |
| --- | --- |
| Artists | 40 (about 15% headliners, popularity 80–100; the rest 10–74) |
| Venues | 15 |
| Past events | 120 |
| Upcoming events | 12 |
| Buyers | 4,000 |

Sales are censored at capacity: a headliner that sells out tells us demand
was *at least* the house size, not what it actually was. That is why the
model forecasts demand from the features rather than fitting past sales.

One run with that seed produced this split, which is the shape the model is
meant to pick up:

| Artist tier | Tiers | Mean sell-through | Sold out |
| --- | --- | --- | --- |
| Headliner | 91 | 1.00 | 98.9% |
| Long tail | 372 | 0.62 | 18.5% |
