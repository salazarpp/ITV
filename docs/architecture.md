# Architecture and design decisions

## What was built

PokeSync combines a Spring Boot REST API, a React interface and PostgreSQL. Users can browse Pokemon from PokeAPI, inspect their details, synchronize selected Pokemon into a local collection and maintain custom names, regions and classifications. Public browsing is available without signing in. The local collection requires authentication.

The application separates external Pokemon information from locally maintained information. This allows PokeAPI to supply the original data while PokeSync owns the custom fields and the persistence of synchronized records.

## Clean Architecture

The backend follows Clean Architecture principles through four responsibilities: domain, application, infrastructure and presentation. The central decision is to keep business rules independent of HTTP, Spring and database entities.

The domain contains the models that describe users, Pokemon, local records and paginated results. These models do not contain persistence mappings or controller annotations. This keeps their meaning independent of how information enters the system or where it is stored.

The application layer implements registration, login, public browsing and local collection operations. It also defines ports for persistence, Pokemon retrieval, password hashing and token issuance. A use case depends on those interfaces rather than a particular database client or security library.

Infrastructure implements the ports using JPA, PostgreSQL, an HTTP client, BCrypt and JWT. Spring configuration connects these implementations to the application services. Transactions and serialization belong here because they concern persistence rather than the meaning of a Pokemon operation.

Presentation exposes the HTTP contract, validates incoming request shapes and translates application failures into responses. Controllers delegate business operations to services instead of implementing database access or provider calls themselves.

This structure makes the core easier to exercise with substitutes for external dependencies. It also allows an adapter to change without forcing business services to adopt that adapter's technology. The outer layers still use Spring and share transport concerns; independence is concentrated in the domain and application layers.

## Synchronization and local persistence

Synchronization retrieves a Pokemon detail from the provider, assigns a local UUID and stores both the local record and a serialized detail snapshot. The provider ID is retained separately and must be unique. A local UUID identifies the application's record, while the provider ID identifies its external origin.

The custom fields are stored separately from the snapshot. Updating a custom name or classification therefore does not overwrite the original Pokemon information. Local listing, retrieval, modification and deletion use PostgreSQL without requiring a fresh PokeAPI request.

The snapshot is stored as JSON text because the implementation preserves the retrieved detail without introducing a relational table for every nested provider structure. The tradeoff is that those nested details are not normalized for relational querying. Synchronization currently creates a saved record; it does not periodically refresh existing snapshots.

The local collection is shared by authenticated users. Authentication controls access, but there is no ownership rule that restricts each Pokemon to its creator. This matches the implemented collection model and avoids implying a permission model that does not exist.

## Frontend organization

The interface separates browsing, detail, authentication and collection management into components. A common API client handles JSON requests, bearer tokens and error conversion. Shared helpers provide pagination, image fallbacks and request loading states.

React state is used directly because the implemented interactions do not require an additional state management library. Requests can be cancelled when their view changes, preventing an obsolete response from replacing the current view's data.

The frontend and backend remain separate applications. During container execution, nginx serves the built interface and forwards API requests to the backend on the same origin. This gives the browser one application address while preserving separate backend and frontend builds.
