# Security decisions

## Authentication and protected operations

JWT authentication is a required part of PokeSync. Registration and login are public, as are Pokemon browsing, API documentation and basic health endpoints. Access to the local collection and other protected endpoints requires a valid bearer token.

The backend uses stateless authentication. It verifies a token on each protected request instead of maintaining a server-side login session. This keeps authentication compatible with separate frontend and backend processes and avoids storing session state inside an application instance.

The implementation authenticates users without defining administrative roles or record ownership. Every authenticated user can operate on the shared local collection. Role-based authorization and private collections would require additional business rules and are not part of the current behavior.

## Password handling

Passwords are hashed with BCrypt before persistence. The database stores the hash rather than the original password, and registration responses do not expose it. Passwords exceeding BCrypt's supported input size are rejected rather than silently accepting a truncated value.

Login failures use a generic invalid-credentials response. The authentication service performs a hash comparison even when the username does not exist, using a dummy hash. This reduces the difference between the processing paths for an unknown account and an incorrect password; it does not establish a guarantee against timing analysis.

Username and email uniqueness are enforced by persistence as well as an initial application check. The database constraint handles concurrent registrations that could otherwise both pass the initial check.

## Token validation and browser storage

Tokens use HS256 with a signing key supplied through configuration. The key must be valid Base64 and contain at least 32 decoded bytes. Token validation checks the expected issuer and timestamps, and requires expiration and subject claims. The configured lifetime must be positive.

The frontend keeps the token in memory. It sends the token in the Authorization header and does not persist it in browser storage. Refreshing the page therefore ends the frontend session. An expiration timer and authenticated unauthorized responses also clear the session.

This choice reduces persistent token storage at the cost of requiring another login after a refresh. It does not protect an active token from malicious code already executing inside the page. Logout clears the browser's token; there is no server-side revocation list or refresh-token flow.

CSRF protection is disabled for the current bearer-header model, which does not authenticate through automatically attached session cookies. Form login, HTTP Basic and server sessions are also disabled. Introducing cookie authentication would require revisiting this decision.

## Secrets and configuration

Database credentials and the JWT signing key are supplied at runtime. Required secrets have no application defaults and are not included in frontend assets or container builds. Kubernetes resources refer to Secret keys rather than containing credential values.

The development GitHub environment contains separate database and JWT secrets. Separating them prevents the database password from also becoming the signing key. Configuration such as database names, ports and token lifetime remains distinct from secret material.

The demonstration account is created only when the demo profile is explicitly enabled. Normal startup does not create an account with a known password. The profile exists to make evaluation reproducible without making demonstration credentials part of ordinary operation.

## Error and log exposure

API failures return controlled messages rather than raw exception text. Security failures use the same error structure as application failures. Logs contain correlation IDs, outcomes and bounded diagnostics, while excluding request bodies, passwords, bearer tokens and incoming query strings.

The request correlation header is validated before use. This prevents arbitrary client-provided text from being accepted as a logging identifier. Health endpoints report status without detailed diagnostics.

The current implementation does not include login rate limiting, account recovery, email verification or TLS termination. Those are additional capabilities, not guarantees supplied by JWT. External access to the home lab remains pending its network and deployment configuration.
