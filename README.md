# PokeSync

Spring Boot REST API and React frontend for browsing PokeAPI, synchronizing Pokemon into PostgreSQL and maintaining custom names, regions and classifications. Registration and mandatory JWT authentication protect the shared local collection.

## Requirements and environment

Java 21/Maven >=3.6.3; Node >=22.12/npm; PostgreSQL for runtime; Docker for container setup and integration tests. Internet access is needed for dependency installation and live public browsing. Tests use isolated provider fixtures.

Copy `.env.example` to `.env` only if you do not already have a local file. Set `DB_PASSWORD` and `JWT_SECRET`; the sample has blank secret placeholders. Generate the JWT key yourself with `openssl rand -base64 32`. Do not commit real credentials. JWT requires Base64 with at least 32 decoded bytes and a positive lifetime.

| Variable | Default / purpose |
| --- | --- |
| DB_HOST | localhost; Compose sets database internally |
| DB_PORT | 5432 |
| DB_NAME / DB_USER | pokesync |
| DB_PASSWORD | Required, no default |
| JWT_SECRET | Required Base64 HMAC key, no default |
| JWT_EXPIRATION_SECONDS | 3600 |
| POKE_API_BASE_URL | https://pokeapi.co/api/v2; standalone backend override |
| SPRING_PROFILES_ACTIVE | default; demo opts into demonstration fixtures |

Compose reads the local `.env`. For standalone Spring execution, export Bash-compatible assignments explicitly:

```bash
set -a
source .env
set +a
mvn -f backend/pom.xml spring-boot:run
```

Start your PostgreSQL instance first. Flyway creates `app_users` and `local_pokemon`; Hibernate validates the schema.

## Container setup and demo

Run these commands yourself from the root after configuring required variables:

```bash
docker compose up --build
```

Frontend: http://localhost:3000. Backend: http://localhost:8080. Database port binds to localhost. Data persists in `postgres-data`. `docker compose down` stops containers without deleting the volume. Backend startup waits for PostgreSQL health; frontend may show a retryable error while Spring initializes.

The root Dockerfile builds the backend without tests. `deploy/frontend.Dockerfile` builds the frontend and serves it with nginx. API/auth/docs/health requests are proxied on the same origin. JWT signing keys are never included in frontend assets.

```bash
SPRING_PROFILES_ACTIVE=demo docker compose up --build
```

The explicit `demo` profile seeds mock account `demo` / `DemoTrainer123!` and a local Bulbasaur snapshot with custom fields. Seeds are idempotent and require no PokeAPI request. The default profile creates no known-password account. Use demo only for demonstration; public browsing still uses PokeAPI.

## Frontend development

In `frontend/`, run `npm ci --ignore-scripts`, then `npm run dev`. Vite proxies backend routes to localhost:8080. The responsive app includes public pagination/detail/evolution, registration/login/logout and authenticated local create/read/update/delete. Tokens stay in memory and expire automatically; refreshing signs out. Local records are shared by authenticated users; no per-user Pokemon ownership requirement was requested.

## API and documentation

Swagger UI: http://localhost:8080/swagger-ui/index.html. OpenAPI JSON: http://localhost:8080/v3/api-docs. YAML: http://localhost:8080/v3/api-docs.yaml. Import the JSON URL into Postman. Swagger's `bearerAuth` accepts the login `accessToken`. Documentation identifies public/protected operations, DTOs, errors and correlation headers.

| Method / route | Access | Result |
| --- | --- | --- |
| POST /auth/register | Public | username/email/password ->201 ID/username/email |
| POST /auth/login | Public | username/password ->200 accessToken/tokenType/expiresIn; no-store |
| GET /api/v1/pokemon?limit=20&offset=0 | Public | count/results: sprite/category/mass in kg/skills |
| GET /api/v1/pokemon/{id} | Public | Image/statistics/description/evolution |
| POST /api/v1/local-pokemon | Bearer JWT | pokeApiId/customName/region/internalClassification ->201 local record and Location |
| GET /api/v1/local-pokemon?limit=20&offset=0 | Bearer JWT | count/results from PostgreSQL |
| GET /api/v1/local-pokemon/{uuid} | Bearer JWT | Saved record |
| PUT /api/v1/local-pokemon/{uuid} | Bearer JWT | Replace custom fields ->200 |
| DELETE /api/v1/local-pokemon/{uuid} | Bearer JWT | 204 |
| GET /actuator/health and health probes | Public | Status without diagnostic details |
| GET /actuator/metrics | Bearer JWT | Available HTTP/JVM metrics |

Pagination: limit 1..100, offset >=0. Custom fields: optional, max 255 characters; blanks become null. Synchronization stores the full upstream detail JSON and a local record. Local reads/updates/deletes work independently of PokeAPI. Duplicate synchronization returns 409. Evolution contains species IDs/names; category is English genus; skills are ability names.

## Errors and CDR-style logs

`@RestControllerAdvice` and `@ExceptionHandler` return a consistent structure:

```json
{"status":404,"code":"POKEMON_NOT_FOUND","message":"Pokemon was not found","timestamp":"2026-10-07T12:00:00Z","requestId":"example-request-1"}
```

400 invalid payload/parameters; 401 invalid credentials/token; 403 denied access; 404 missing records; 409 duplicate user/synchronization; 502 provider failure/invalid response; 504 provider timeout; 500 unexpected error. Spring Security uses the same structure and retains bearer challenge headers.

Console format follows CDR:

```text
2026-10-07 12:00:00.000 | RequestLogFilter | INFO | example-request-1 | POKESYNC-HTTP-0001 | start method=GET path=/api/v1/pokemon
2026-10-07 12:00:00.041 | RequestLogFilter | INFO | example-request-1 | POKESYNC-HTTP-0002 | complete method=GET path=/api/v1/pokemon status=200 durationMs=41
```

These are format examples, not captured runtime evidence. Optional `X-Request-ID` accepts `[A-Za-z0-9._-]{1,64}`; unsafe/missing values receive a UUID. Every response returns the ID; error JSON includes it. Start/end events include status/duration for successful, rejected and failed requests. Provider fetch events identify upstream phases; cache-hit events are at DEBUG (enable with `LOGGING_LEVEL_COM_POKESYNC_INFRASTRUCTURE_UPSTREAM=DEBUG` for standalone execution). Application ready/closing/startup-failure events cover lifecycle changes. Error diagnostics include cause types and application class/method/line locations. Request bodies, incoming query strings, credentials, tokens and raw exception messages are excluded; generated provider pagination paths contain only limit/offset. MDC is cleaned after each request. Follow container output with `docker compose logs -f backend`.

Provider connect/read timeouts are 3s/5s per request with no implicit retries. Cache: 256 successful responses, five-minute TTL; errors are not cached. A cold enriched page requires multiple provider calls; duration logs identify slow requests. Health, HTTP/JVM metrics and safe logs provide baseline observability; no hosted monitoring or alert delivery is configured.

## Unit, integration and automation tests

Run these yourself. The assistant did not execute tests or start services:

```bash
# Unit/MockMvc, no PostgreSQL
mvn --batch-mode --no-transfer-progress -f backend/pom.xml test
# Unit + real PostgreSQL integration, Docker required
mvn --batch-mode --no-transfer-progress -f backend/pom.xml verify
# Compile/package only
mvn -f backend/pom.xml -Dmaven.test.skip=true package
# From frontend/
npm ci --ignore-scripts
npm run typecheck
npm run test:unit
npm run build
npx playwright install --with-deps chromium
npm run test:e2e
```

JUnit 5/Mockito/AssertJ cover auth/bcrypt/JWT, persistence behavior, Pokemon use cases, provider contracts, HTTP errors/security and safe logging. Surefire runs `*Test`; Failsafe runs `*IT` during `verify`. Integration uses disposable PostgreSQL and a local JDK HTTP provider fixture, without production credentials or live PokeAPI. Browser automation exercises UI workflows with intercepted API fixtures; backend integration separately validates Spring/database/provider wiring. Playwright starts the Vite development server when executed by you or CI.

`.github/workflows/ci.yml` runs on pushes to main, PRs and manual dispatch. Independent backend/frontend jobs have time limits and upload reports even on failure. Both validation jobs passed in [the first GitHub Actions run](https://github.com/salazarpp/ITV/actions/runs/37693655621). After validation on main, the new image job builds and publishes backend/frontend images to GHCR using commit SHA tags; PRs never publish images. This image job has not run yet.

[Home lab Compose delivery](docs/home-lab-compose.md) describes deployment to jl-S using the shared PostgreSQL service. After image publication, a gated deployment job targets a dedicated runner and updates frontend/backend containers. It remains disabled until database provisioning and runner preparation are complete. The existing `k8s-dev` environment holds deployment secrets; its name does not select Kubernetes. Kubernetes manifests remain inactive alternatives.

## Architecture and assessment artifacts

`domain`/`application`: framework-independent models, ports and use cases. `infrastructure`: PostgreSQL/JPA, PokeAPI, JWT, logging/config. `presentation`: controllers and centralized HTTP errors. Use cases enforce business validation, controllers transport validation. PostgreSQL uniqueness handles concurrent duplicate creation.

[docs/genai-exercise.md](docs/genai-exercise.md) contains the separate task-management generation prompt, representative scaffold and critical review. That scaffold is outside the Pokemon runtime application.

Primary references: [PokeAPI](https://pokeapi.co/docs/v2/), [Spring Boot3.5](https://docs.spring.io/spring-boot/3.5/), [springdoc](https://springdoc.org/v2/), [Testcontainers](https://java.testcontainers.org/), [Playwright](https://playwright.dev/docs/intro).

## Verification limits

Passed: frontend TypeScript checks and production build; POM XML and frontend JSON parsing; three YAML configuration parses; static Docker Compose configuration validation using `/dev/null` as its environment file; source whitespace/dependency-direction review. Prepared: 58 backend unit/HTTP/observability tests, eight PostgreSQL integration tests, seven frontend unit cases and three browser scenarios across two viewports. None were executed.

Backend compilation was subsequently verified through the authorized Docker build: Java 21 compiled all 55 production files, Maven reported BUILD SUCCESS and image `pokesync-backend:local` was created. Test compilation and execution were skipped. The frontend development server was started on localhost:5173 for manual review and returned HTTP 200.

GitHub's first CI run passed backend and frontend jobs; the assistant did not run tests locally. Full manual application use, live PokeAPI integration, image publication and Kubernetes deployment remain unverified. The frontend development server was subsequently stopped; the backend was never started locally. No assistant tests, commits/pushes, deployment, database connections or secret-file reads.
