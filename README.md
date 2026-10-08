# PokeSync

Browse Pokemon from the public [PokeAPI](https://pokeapi.co/), save the ones you want into your own database and give them custom names, regions and classifications. Built with Spring Boot, React and PostgreSQL.

## Documentation

- **[Full documentation (HTML)](docs/genai-exercise.html)**: architecture, decisions, security, testing, performance and the AI-assisted workflow, with diagrams. Download it and open it in a browser; GitHub shows `.html` files as source.
- [Same documentation (Markdown)](docs/genai-exercise.md): renders with diagrams directly on GitHub.

| Topic | Document |
| --- | --- |
| Architecture and design decisions | [docs/architecture.md](docs/architecture.md) |
| Security | [docs/security.md](docs/security.md) |
| Validation, errors, logging, caching, testing | [docs/engineering-practices.md](docs/engineering-practices.md) |
| Configuration, containers and CI/CD | [docs/delivery-decisions.md](docs/delivery-decisions.md) |
| Home lab deployment (Docker Compose) | [docs/home-lab-compose.md](docs/home-lab-compose.md) |
| Kubernetes (inactive alternative) | [docs/kubernetes.md](docs/kubernetes.md) |

## Features

- Paginated Pokemon list with sprite, category, weight and abilities.
- Pokemon detail with image, stats, description and evolution chain.
- Save a Pokemon locally and edit its custom name, region and classification.
- Registration and login with JWT; browsing is public, the local collection requires sign-in.
- Responsive interface for desktop and mobile.

## Tech stack

| Layer | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot, Spring Security (JWT), Spring Data JPA, Flyway, springdoc OpenAPI |
| Frontend | React 19, TypeScript, Vite |
| Database | PostgreSQL 17 |
| Tests | JUnit 5, Mockito, MockMvc, Testcontainers, Vitest, Playwright |
| Delivery | Docker, Docker Compose, nginx, GitHub Actions |

## Quick start

**Prerequisites:** Docker with Docker Compose. Internet access for PokeAPI.

1. Create your local configuration. Skip the copy if you already have a `.env`.

   ```bash
   cp .env.example .env
   openssl rand -base64 32   # paste the output as JWT_SECRET in .env
   ```

   Set `DB_PASSWORD` and `JWT_SECRET` in `.env`. Never commit real credentials.

2. Start the app with demo data:

   ```bash
   SPRING_PROFILES_ACTIVE=demo docker compose up --build
   ```

3. Open the app:

   | What | URL |
   | --- | --- |
   | Web app | http://localhost:3000 |
   | API | http://localhost:8080 |
   | Swagger UI | http://localhost:8080/swagger-ui/index.html |

**Demo account:** `demo` / `DemoTrainer123!`, with a saved Bulbasaur. The demo data exists only with the `demo` profile; a normal start creates no known-password account.

Stop with `docker compose down`. Data stays in the `postgres-data` volume. While the backend starts, the web app may briefly show a retryable error.

## Configuration

Environment variables, read from `.env` by Docker Compose.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_HOST` | `localhost` | Database host (Compose sets it internally) |
| `DB_PORT` | `5432` | Database port |
| `DB_NAME` / `DB_USER` | `pokesync` | Database name and user |
| `DB_PASSWORD` | *required* | Database password |
| `JWT_SECRET` | *required* | Base64 signing key, at least 32 bytes decoded |
| `JWT_EXPIRATION_SECONDS` | `3600` | Token lifetime |
| `POKE_API_BASE_URL` | `https://pokeapi.co/api/v2` | PokeAPI address |
| `SPRING_PROFILES_ACTIVE` | default | `demo` loads demonstration data |

## Local development

Requirements: Java 21, Maven 3.6.3+, Node 22.12+, a running PostgreSQL.

**Backend** (from the repository root):

```bash
set -a; source .env; set +a
mvn -f backend/pom.xml spring-boot:run
```

Flyway creates the tables on startup.

**Frontend** (from `frontend/`):

```bash
npm ci --ignore-scripts
npm run dev
```

Vite forwards API requests to `localhost:8080`.

## API

Interactive documentation is generated from the code:

- Swagger UI: `/swagger-ui/index.html`. Click **Authorize** and paste the `accessToken` from login.
- OpenAPI: `/v3/api-docs` (JSON) and `/v3/api-docs.yaml`.
- Postman: *Import → Link* and paste `http://localhost:8080/v3/api-docs`.

| Method | Route | Access | Description |
| --- | --- | --- | --- |
| POST | `/auth/register` | Public | Create an account (`username`, `email`, `password`) |
| POST | `/auth/login` | Public | Get an `accessToken` |
| GET | `/api/v1/pokemon?limit=20&offset=0` | Public | Paginated Pokemon list |
| GET | `/api/v1/pokemon/{id}` | Public | Pokemon detail |
| POST | `/api/v1/local-pokemon` | Token | Save a Pokemon (`pokeApiId`, `customName`, `region`, `internalClassification`) |
| GET | `/api/v1/local-pokemon?limit=20&offset=0` | Token | List saved Pokemon |
| GET | `/api/v1/local-pokemon/{uuid}` | Token | Read a saved Pokemon |
| PUT | `/api/v1/local-pokemon/{uuid}` | Token | Replace its custom fields |
| DELETE | `/api/v1/local-pokemon/{uuid}` | Token | Delete it |
| GET | `/actuator/health` | Public | Application status |
| GET | `/actuator/metrics` | Token | HTTP and JVM metrics |

Rules: `limit` 1–100, `offset` 0 or more; custom fields are optional, up to 255 characters. Saving the same Pokemon twice returns `409`. Saved Pokemon are shared by all signed-in users.

Errors always have the same shape:

```json
{"status":404,"code":"POKEMON_NOT_FOUND","message":"Pokemon was not found","timestamp":"2026-10-07T12:00:00Z","requestId":"example-request-1"}
```

`400` invalid input · `401` missing or invalid token · `403` access denied · `404` not found · `409` duplicate · `502` PokeAPI failed · `504` PokeAPI timed out · `500` unexpected error.

Every response carries an `X-Request-ID` header. Use it to find the request in the logs (`docker compose logs -f backend`).

## Testing

```bash
# Backend unit and web layer tests (no database needed)
mvn -f backend/pom.xml test

# Backend unit + integration tests with a real PostgreSQL (Docker required)
mvn -f backend/pom.xml verify

# Frontend (from frontend/)
npm run typecheck
npm run test:unit
npx playwright install --with-deps chromium
npm run test:e2e
```

| Type | What it covers |
| --- | --- |
| Backend unit | Services, repositories, password hashing, logging, PokeAPI client |
| Web layer | Routes, validation, status codes, JWT protection |
| Integration | Whole backend with a disposable PostgreSQL and a simulated PokeAPI |
| Frontend unit | API client and page cache |
| Browser | Main user flows on desktop and mobile, with simulated API responses |

Details and test counts: [Testing](docs/genai-exercise.md#7-testing).

## Project structure

```text
backend/     Spring Boot API (domain, application, infrastructure, presentation)
frontend/    React app, unit tests (src/) and browser tests (tests/)
deploy/      nginx config, frontend Dockerfile, home lab Compose, Kubernetes manifests
docs/        Documentation and the task-management exercise (docs/genai/)
Dockerfile   Backend image
compose.yml  Local stack: PostgreSQL, backend, frontend
```

## Deployment

GitHub Actions (`.github/workflows/ci.yml`) runs on pushes to `main`, pull requests and manual runs:

1. Backend and frontend tests run in parallel.
2. On `main`, both images are built and published, tagged with the commit SHA.
3. When enabled, the home lab runner deploys them with Docker Compose and checks health.

See [docs/home-lab-compose.md](docs/home-lab-compose.md) for the home lab setup.

## Project status

Delivered. Open items, including the measurement of the latest performance change, are listed in [Risks and open items](docs/genai-exercise.md#13-risks-and-open-items).

## Authors

Built by jl with AI coding assistants (Claude Code, Codex). How we worked: [How we built it with AI](docs/genai-exercise.md#10-how-we-built-it-with-ai).
