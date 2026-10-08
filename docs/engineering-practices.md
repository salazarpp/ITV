# Engineering practices and reliability

## Validation and consistent behavior

Validation is split by responsibility. HTTP request validation handles malformed inputs and transport constraints. Application services enforce rules such as positive Pokemon identifiers, bounded pagination and custom field lengths. Keeping these rules in application services also protects operations invoked outside an HTTP controller.

Blank custom fields become null, and surrounding whitespace is removed before storage. This gives optional fields a consistent representation instead of mixing empty strings, whitespace-only values and missing values.

Local persistence uses transactions for create, update and delete operations. Database uniqueness provides a final check for duplicate synchronization and duplicate users. Only the recognized unique-constraint failure is translated into a conflict; unrelated persistence failures are not mislabeled as duplicates.

## Centralized exception handling

The backend uses RestControllerAdvice and ExceptionHandler to translate failures into a consistent HTTP response. Each error contains status, a stable code, a controlled message, a timestamp and a request correlation ID.

Invalid input produces a bad-request response, missing records produce not-found responses and duplicates produce conflicts. Provider failures and timeouts have separate gateway error statuses. Unexpected failures produce an internal-error response without exposing implementation details to the client.

Spring Security handles some failures before a controller is reached. Its authentication and access-denied handlers therefore generate the same response structure separately. This prevents the frontend from needing different parsing rules for security and application errors.

## Operational visibility

Logs use a CDR-style line format with time, component, severity, correlation ID and event details. HTTP requests produce start and completion events, including the result status and duration. The response returns the correlation ID so a reported frontend error can be connected to backend logs.

Provider calls and application lifecycle transitions also produce events. Exception diagnostics retain cause types and selected application locations within bounded limits. This preserves useful debugging context while avoiding raw message dumps that could contain sensitive data.

Correlation context is cleaned after each request to avoid carrying one request's identifier into another request handled by the same thread. Logs, health probes and metrics provide a baseline for diagnosis; a centralized monitoring service and alert delivery have not been configured.

## External dependency behavior

PokeAPI access is isolated in a provider adapter. It translates provider responses into application models and translates network failures into application exceptions. The provider uses configured connection and read timeouts so a single external request is not allowed to wait indefinitely.

A bounded, in-memory cache stores successful provider responses for five minutes. The capacity limit controls memory use, while expiration allows information to be refreshed. Failed responses are not cached. The cache belongs to each backend process and is cleared when that process restarts; it is not shared between replicas.

The browser retains up to ten successful public Pokemon pages for five minutes, keyed by page size and offset. This avoids another API request when returning to a fresh visited page, including after switching sections. Expiration is absolute and capacity eviction is FIFO. Failed or aborted results are excluded, and cancellation prevents stale navigation responses from being stored. This cache holds public metadata only; authentication and local collection data remain outside it. A full reload clears the cache. Pages are loaded on demand without speculative prefetch.

Provider cache decisions are visible at INFO with the request correlation context: initial hit or miss, shared in-flight wait and a successful race recheck. These events distinguish backend resource reuse from browser page reuse and outbound provider requests.

There are no automatic retries. This keeps the number of provider attempts predictable. Page enrichment uses a shared executor with four workers per backend process and a bounded queue; submission backpressure prevents unbounded queued work. Concurrent requests for the same upstream path share one in-flight fetch, including its result or failure. Results retain provider ordering, and workers capture and restore MDC correlation context so request diagnostics remain attributable. An enriched page may still require multiple provider calls, so a per-call timeout does not establish a timeout for the entire page operation. Reduced page latency must be measured before an improvement is claimed.

## Testing strategy

Unit tests exercise business services and individual security, persistence, HTTP and observability behaviors. Backend integration tests use disposable PostgreSQL and a controlled HTTP provider fixture to exercise the Spring application together with real persistence.

Frontend unit tests cover the API transport and public page cache reuse, expiration, capacity and cancellation behavior. Browser automation exercises user interactions with intercepted API fixtures across desktop and mobile viewports. These browser tests verify interface behavior independently of provider availability; they do not constitute a full browser-to-live-backend integration test.

The pipeline separates backend and frontend validation, preserves reports and sets job time limits. Type checking and production builds complement behavioral tests by checking compatibility and packaging. Passing checks provide evidence for the scenarios exercised, rather than proving complete coverage or production readiness.

## API clarity and maintainability

The API uses standard HTTP methods, paginated response structures and explicit creation, deletion and failure statuses. OpenAPI describes public and protected operations, schemas and error responses so consumers can inspect the contract without inferring it from frontend code.

Shared frontend request and presentation helpers keep error handling, pagination and loading behavior consistent. The backend uses ports where dependencies cross the business boundary. These choices reduce duplicated behavior while keeping the number of architectural mechanisms proportional to the application's scope.
