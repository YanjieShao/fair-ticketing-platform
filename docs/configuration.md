# Configuration

Secrets and switches come from the environment. The defaults in
`backend/src/main/resources/application.yml` are for local development only.
Copy `.env.example` and set `FT_JWT_SECRET` to something private before this
is reachable from anywhere other than your laptop.

Spring Boot does not load `.env` by itself. Export the variables, or prefix
the run command:

```bash
export $(grep -v '^#' .env | xargs) && ./mvnw spring-boot:run
```

`docker compose` **does** load `.env` from the repository root. That is how
`docker-compose.app.yml` picks up `FT_JWT_SECRET`. The overlay also points
the API at the `mysql` and `redis` service hostnames and sets
`FT_CORS_ORIGINS` to `http://localhost` for the nginx UI on port 80. The
Vite default below (`http://localhost:5173`) is for the hot-reload
quickstart.

| Variable | Default | Purpose |
| --- | --- | --- |
| `FT_JWT_SECRET` | dev placeholder | HMAC key for access tokens; at least 32 bytes |
| `FT_INVENTORY_STRATEGY` | `DB_PESSIMISTIC_LOCK` | `DB_PESSIMISTIC_LOCK`, `DB_CONDITIONAL_UPDATE`, or `REDIS_LUA` |
| `FT_WAITING_ROOM_ENABLED` | `false` | gate checkout behind the queue |
| `FT_WAITING_ROOM_RATE` | `20` | admissions per second once the room is on |
| `FT_PAYMENT_FAILURE_RATE` | `0.0` | forces declined payments for demos |
| `FT_SEED_ENABLED` | `true` in `.env.example` / Compose overlay; `false` in `application.yml` if unset | synthetic sales history for dashboards; skips when artists already exist |
| `FT_CORS_ORIGINS` | `http://localhost:5173` | browser origins allowed to call the API directly |
| `FT_ML_BASE_URL` | `http://localhost:8090` | Python demand model |
| `FT_FORECAST_ON_START` | `false` | run a forecast pass when the backend boots |
| `FT_INSIGHTS_ON_START` | `false` | run an insight pass when the backend boots |
| `FT_LLM_BASE_URL` | `https://api.openai.com/v1` | OpenAI-compatible chat API |
| `FT_LLM_MODEL` | `gpt-4o-mini` | model id for insight phrasing |
| `OPENAI_API_KEY` | empty | optional; blank uses the template composer |
| `FT_LOADTEST_ENABLED` | `false` | local stampede fixture; never on a public process |
| `FT_RATE_LIMIT_ENABLED` | `true` | per-account caps on checkout and join |
| `FT_TOMCAT_MAX_CONNECTIONS` | `8192` | raise for a 10k stampede |
| `FT_TOMCAT_ACCEPT_COUNT` | `100` | raise for a 10k stampede |

Payment holds last 10 minutes. A waitlist offer lasts 15 minutes. The
per-account caps are 8 checkouts a minute, 20 joins a minute, and 5 of those
writes in any 10 seconds. Those windows live in `application.yml`, not env
vars.

`FT_IT_EXTERNAL` is a test-only switch used by GitHub Actions. It is not a
runtime setting; see [backend/README.md](../backend/README.md).
