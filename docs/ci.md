# CI

Two runners, different jobs. **GitHub Actions gates pull requests.** The
`Jenkinsfile` is a second, optional pipeline for a local or company Jenkins
controller. Do not delete the Actions workflow in favour of Jenkins: this
repository lives on GitHub, and the PR check is what actually blocks a merge.

## GitHub Actions

Workflow: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml).

| Job | What it runs |
| --- | --- |
| `backend` | `./mvnw verify` — unit tests with a **95% line** JaCoCo gate, then the MySQL/Redis `*IT` suite |
| `frontend` | `oxlint`, Vitest with coverage, `npm run build` |
| `ml` | `pytest` in `ml-service/` |
| `images` | `docker build` of the API and the nginx UI (after tests pass) |
| `publish` | on push to `main` only: push those images to GHCR |

JaCoCo is **unit tests only**. Integration tests, Redis Lua, JDBC read
models, and Spring wiring are excluded from the 95% bundle on purpose; `mvn
verify` and the Actions MySQL/Redis services still run them. Report:
`backend/target/site/jacoco/index.html`.

Frontend coverage is scoped to `src/api/**` and `src/auth/**` (the request
layer and auth). Thresholds: 95% lines and statements, 90% functions.
Playwright E2E is not part of that number and is not in CI.

GHCR names are lowercase: `ghcr.io/<owner>/fair-ticketing-backend` and
`…/fair-ticketing-frontend`, tagged with the git SHA and `latest`. Packages
must be public, or a host must `docker login ghcr.io`, before a VM can pull
them. See [deploy.md](deploy.md).

## Jenkins

[`Jenkinsfile`](../Jenkinsfile) at the repository root. It does **not** run
the Testcontainers `*IT` suite and does **not** publish to GHCR. Those stay
on Actions. The Jenkins job is:

1. Backend `./mvnw test` (same 95% unit gate). Archives the JaCoCo HTML
   report and Surefire XML.
2. Frontend `npm ci`, Vitest with coverage, `oxlint`.
3. `docker build` of both application images.

To try it locally, start Jenkins on **8081** so it does not collide with the
API on 8080, and mount the Docker socket so the image stage can run:

```bash
docker run --name ft-jenkins --rm -p 8081:8080 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v jenkins_home:/var/jenkins_home \
  jenkins/jenkins:lts-jdk21
```

Create a Pipeline job, point it at this Git repository, script path
`Jenkinsfile`. The agent needs JDK 21, Node 22, and `docker`.
