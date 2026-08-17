# Deploy

This project can go to a cloud VM on its own. Do not wait for other side
projects to exist.

## Two ways to run

**Laptop / demo with hot reload:** Compose for MySQL and Redis only, then
`./mvnw spring-boot:run` and `npm run dev`. The UI is http://localhost:5173
and Vite proxies `/api` to :8080. That is the [root README](../README.md)
quickstart.

**Application images:** `backend/Dockerfile` (Temurin 21) and
`frontend/Dockerfile` (Node 22 build, nginx 1.27). The UI is on port 80 and
proxies `/api/` to the backend container, including the waiting-room SSE
stream. CORS does not matter for that path.

```bash
cp .env.example .env   # set FT_JWT_SECRET (Compose reads .env for you)
docker compose -f docker-compose.yml -f docker-compose.app.yml up --build
```

`docker-compose.app.yml` overrides the JDBC URL and Redis host to the Compose
service names (`mysql`, `redis`) and sets `FT_CORS_ORIGINS` to
`http://localhost` for the nginx UI. Leave `FT_LOADTEST_ENABLED=false`.

## Images on GitHub

GitHub Actions builds both images on every PR. A push to `main` publishes:

- `ghcr.io/<you>/fair-ticketing-backend:latest`
- `ghcr.io/<you>/fair-ticketing-frontend:latest`

`<you>` is the GitHub owner in **lowercase**. Make the packages public, or
`docker login ghcr.io` on the VM.

To run published images instead of building on the VM, pull and retag:

```bash
docker pull ghcr.io/<you>/fair-ticketing-backend:latest
docker pull ghcr.io/<you>/fair-ticketing-frontend:latest
docker tag ghcr.io/<you>/fair-ticketing-backend:latest fair-ticketing-backend:local
docker tag ghcr.io/<you>/fair-ticketing-frontend:latest fair-ticketing-frontend:local
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d
```

## Cloud VM (AWS Lightsail, EC2, or Azure)

1. Open ports 80 (and 22 for SSH).
2. Install Docker and the Compose plugin.
3. Copy this repository, or pull/retag the GHCR images as above.
4. Put a production `FT_JWT_SECRET` in `.env`. If the browser will call
   `:8080` directly, set `FT_CORS_ORIGINS` to that public origin.
5. Run the same Compose overlay command.

A managed MySQL/Redis pair plus two container apps is a later hardening
step, not required to show that the images run off your laptop. The Python
model is optional here too: checkout never calls it.
