# PokeSync

## Project Vision

**PokeSync** is a full-stack technical exercise built around PokeAPI. It
provides a clean, testable REST API to browse external Pokémon data,
show detailed information, synchronize selected Pokémon into a local
relational database, and maintain proprietary/local attributes.

The architectural core is **Clean Architecture**. Business rules and use
cases remain independent from Spring Boot, PostgreSQL, PokeAPI, caching,
security, and the frontend.

## 1. Main Objectives

PokeSync will:

-   Consume PokeAPI.
-   List Pokémon using pagination with sprite, category, weight/mass,
    and abilities.
-   Show detailed information including image, stats, description, and
    evolution chain.
-   Synchronize selected Pokémon into PostgreSQL.
-   Support proprietary fields such as localized/custom name,
    geographical metadata, and internal classification.
-   Provide CRUD operations for local data.
-   Provide user registration/authentication and public/protected
    routes.
-   Include a responsive frontend.
-   Include automated testing and defensive error handling.
-   Be reproducible with Docker.
-   Include the mandatory GenAI assessment and document how AI output
    was validated and improved.

## 2. Core: Clean Architecture

``` text
                 Presentation
             REST Controllers / DTOs
                       |
                       v
                  Application
                   Use Cases
                       |
                       v
                    Domain
             Models / Business Rules
                       ^
                       |
                    Ports
              _________|_________
             |                   |
             v                   v
       PostgreSQL             PokeAPI
        Adapter               Adapter
             \                   /
              \__ Infrastructure/
```

### Dependency Rule

Dependencies point **toward the core**.

The domain must not know about Spring Boot, PostgreSQL, JPA, PokeAPI,
Caffeine, JWT, React, Docker, or Kubernetes.

Controllers should not contain business logic and should not communicate
directly with PostgreSQL or PokeAPI.

Suggested structure:

``` text
backend/src/main/java/.../pokesync/
├── domain/
│   ├── model/
│   ├── repository/
│   └── exception/
├── application/
│   ├── port/
│   │   ├── in/
│   │   └── out/
│   ├── service/
│   └── dto/
├── infrastructure/
│   ├── pokeapi/
│   ├── persistence/
│   ├── cache/
│   └── security/
└── presentation/
    ├── controller/
    └── exception/
```

## 3. PokeAPI Integration

PokeAPI is an external read source behind an adapter:

``` text
Use Case -> PokeApiPort -> PokeApiAdapter -> PokeAPI
```

The adapter can compose Pokémon, Species, and Evolution Chain resources
as needed. PokeAPI-specific response structures should not leak into the
domain.

## 4. Local Synchronization

``` text
PokeAPI
   |
SyncPokemonUseCase
   |
Domain Model
   |
PokemonRepositoryPort
   |
PostgreSQL
```

Possible local model:

``` text
Pokemon
- id
- pokeApiId
- name
- height
- weight
- imageUrl
- description
- customName
- region
- internalClassification
- createdAt
- updatedAt

PokemonAbility
- id
- pokemonId
- name

User
- id
- username
- email
- passwordHash
- role
```

PokeAPI remains the external source. PostgreSQL stores the locally
managed representation and proprietary attributes.

## 5. Database and Configuration

Primary database: **PostgreSQL**.

Configuration is externalized:

``` text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
```

The real `.env` is excluded from Git. The repository provides
`.env.example`.

The application must not depend specifically on the Docker PostgreSQL
instance. A reviewer can point it to another PostgreSQL instance by
changing environment variables.

### Flyway

Use **Flyway** for version-controlled, reproducible database migrations
and optional seed/demo data.

## 6. Docker

Target local experience:

``` bash
git clone <repository>
cd pokesync
cp .env.example .env
docker compose up --build
```

Docker Compose can orchestrate:

``` text
frontend
backend
postgres
```

Docker Compose is the supported local/demo path, but the application
itself remains configurable independently.

## 7. Cache

The delivered adapter uses a bounded in-memory cache: 256 successful
provider responses and a five-minute TTL. Errors are not cached.
Spring Cache + Caffeine remains an alternative if richer cache policies
or metrics are needed; it is not a required dependency of this delivery.

Redis is intentionally not required because the exercise does not
specify multiple replicas or distributed cache state. Redis could be
evaluated if those requirements appeared later.

## 8. Resilience

Because PokeAPI is external:

-   Configure connection/read timeouts.
-   Translate upstream failures into controlled application errors.
-   Add defensive validation.
-   Consider bounded retries only where appropriate.
-   Consider circuit breaking only if it provides demonstrable value.

## 9. Error Handling

Centralize error handling using `@RestControllerAdvice`.

Expected behavior:

-   `400` for malformed/invalid requests.
-   `401/403` for authentication/authorization failures.
-   `404` for missing local resources.
-   Controlled handling of PokeAPI failures.
-   Consistent error response structures.

Example:

``` json
{
  "status": 404,
  "code": "POKEMON_NOT_FOUND",
  "message": "Pokemon 99999 was not found",
  "timestamp": "..."
}
```

## 10. Security

Use **Spring Security** with mandatory JWT authentication.

``` text
POST /auth/login
      |
credentials
      |
     JWT
      |
Authorization: Bearer <token>
      |
Protected endpoint
```

Browsing can remain public while local modification endpoints are
protected.

## 11. API Documentation

Use **OpenAPI / Swagger UI**, preferably through `springdoc-openapi`.

Swagger is the primary interactive API documentation. A Postman
collection can be included as an optional convenience, but automated
integration tests remain the source of confidence.

## 12. Testing Strategy

### Unit Tests

Use:

-   JUnit 5
-   Mockito
-   AssertJ

Unit-test application/domain behavior without starting Spring or
PostgreSQL.

Examples:

``` text
SyncPokemonUseCase
- persists a valid Pokémon
- maps upstream not-found correctly
- handles an already synchronized Pokémon

UpdatePokemonUseCase
- updates an existing Pokémon
- rejects invalid values
- returns not-found for an unknown Pokémon
```

### Integration Tests

Use:

-   Spring Boot Test / MockMvc where appropriate
-   Testcontainers
-   PostgreSQL container
-   Flyway migrations

These verify that persistence, mapping, migrations, and application
wiring work together.

### PokeAPI Tests

Use **MockRestServiceServer** for adapter unit tests and a local JDK
HTTP server fixture for integration tests to simulate:

``` text
PokeAPI -> 200
PokeAPI -> 404
PokeAPI -> 500
PokeAPI -> timeout
PokeAPI -> malformed/unexpected response
```

Tests must not depend on PokeAPI or Internet availability.

### Performance Tests

Optionally use **k6**.

A useful experiment is to compare repeated read traffic with and without
Caffeine and observe latency, error rate, throughput, and reduction in
upstream PokeAPI calls.

No scalability claim should be made without an explicit requirement and
evidence.

## 13. Observability

Use **Spring Boot Actuator** for health information.

Add structured logs and a request/correlation ID so a request can be
traced through cache lookup, PokeAPI calls, use cases, and persistence.

Example:

``` text
requestId=abc123
method=GET
path=/api/v1/pokemon/25
cache=MISS
upstreamStatus=200
durationMs=184
```

## 14. Frontend

Keep the frontend intentionally focused. Recommended: **React +
TypeScript**.

Primary screens:

``` text
Login

Pokemon List
- pagination
- sprite
- category
- weight
- abilities
- view/sync actions

Pokemon Detail
- image
- stats
- description
- evolution chain

Local Pokemon Edit
- custom name
- region
- classification
- save/update/delete
```

The frontend consumes the PokeSync backend rather than bypassing it to
call PokeAPI directly.

## 15. CI with GitHub Actions

GitHub Actions is a valuable addition because the public GitHub
repository is the deliverable.

A CI workflow can:

``` text
checkout
-> backend build
-> unit tests
-> integration tests
-> frontend install/build/test
```

`main` should remain in a deliverable state.

Feature branches can be short-lived, for example:

``` text
feature/caffeine-cache
feature/github-actions
feature/helm
feature/k6-tests
```

Only merge an extra when it is working, tested, and defendable.

## 16. Optional Kubernetes / Helm / Homelab

Kubernetes and Helm are **not requirements**.

If core requirements are complete, a small Helm chart can demonstrate
deployment knowledge. Docker Compose remains the supported
reviewer/local execution path.

A homelab can be used as a separate deployment laboratory:

``` text
Developer machine -> Docker Compose
GitHub -> GitHub Actions
Homelab -> Kubernetes + Helm
```

Useful homelab validation includes:

-   environment/configuration externalization
-   readiness/liveness probes
-   pod restart behavior
-   deployment with Helm
-   persistence behavior
-   k6 testing against a deployed environment

The evaluation must never depend on homelab availability.

## 17. Mandatory GenAI Assessment

The GenAI portion is **mandatory**.

Keep it conceptually separate from the Pokémon implementation. The
requested task-management API should demonstrate both effective
prompting and critical review.

Document:

1.  The exact prompt used with the selected coding assistant.
2.  Representative generated code.
3.  How generated code was validated.
4.  Problems identified.
5.  Corrections/improvements made manually or through refined prompts.
6.  Authentication/authorization considerations.
7.  Validation and edge cases.
8.  Tests used to verify the result.

A strong example of critical review is detecting generated CRUD code
that loads a task only by `taskId`, then correcting it so an
authenticated user cannot update/delete another user's task.

The message should be: **AI accelerates implementation; engineering
judgment remains responsible for correctness, security, architecture,
and validation.**

## 18. Repository and Submission Constraints

The final solution must:

-   Live in **one public GitHub repository**.
-   Be submitted using **one GitHub link only**.
-   Include the mandatory GenAI portion.
-   Include README/setup/technical documentation.
-   Include demonstration/seed data or mock credentials.
-   Include a Dockerfile.
-   Include tests.
-   Include **no symlinks**.
-   Stop receiving changes after submission until further notice.

Deadline from the assessment email: **Friday, October 9, 2026 at 18:00
Mexico time**.

## 19. Priority Order

### Must Have

1.  Functional Pokémon user stories.
2.  Clean Architecture.
3.  PostgreSQL persistence.
4.  Correct CRUD/error handling.
5.  User registration/authentication and protected/public routes.
6.  Unit/integration tests.
7.  Frontend.
8.  Dockerfile and reproducible setup.
9.  README/technical documentation.
10. Seed/demo data.
11. Mandatory GenAI assessment.

### High-Value Enhancements

1.  Caffeine cache.
2.  Swagger/OpenAPI.
3.  Flyway.
4.  Testcontainers.
5.  WireMock.
6.  GitHub Actions.
7.  Actuator and structured logging.

### Only If Core Is Solid

1.  k6 performance tests.
2.  Helm.
3.  Homelab Kubernetes deployment.
4.  More advanced resilience patterns.

## 20. Tentative Stack

``` text
Backend
- Java 21
- Spring Boot 3.5.16
- Maven 3.6.3 or later
- Spring Web
- Spring Data JPA
- Spring Security
- Bounded provider response cache
- Spring Boot Actuator

Database
- PostgreSQL
- Flyway

Testing
- JUnit 5
- Mockito
- AssertJ
- MockMvc
- Testcontainers
- MockRestServiceServer / JDK HTTP fixture
- k6 (optional)

API Documentation
- OpenAPI
- Swagger UI

Frontend
- React
- TypeScript

Infrastructure
- Docker
- Docker Compose
- GitHub Actions
- Helm (optional)
- Kubernetes/homelab (optional)
```

## 21. Design Philosophy

Every technology must have a reason.

The objective is not to maximize the number of tools in the repository.
The objective is to deliver a small, reliable, testable system whose
architectural decisions can be defended clearly during code review.

The final implementation should favor **clarity, correctness,
reproducibility, and defendable engineering decisions over unnecessary
complexity**.
