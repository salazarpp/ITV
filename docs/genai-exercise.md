# Generative AI exercise

This is the separate task-management scaffold requested by the assessment. The representative output is [TaskController.java](genai/TaskController.java). It is outside the application source tree and is not a deployed PokeSync feature. Codex authored this sample in this delivery; no historical prompt or test result is claimed.

## Prompt

The following is the proposed generation prompt for this scaffold:

```text
Generate a representative Java 21 / Spring Boot 3.5 task-management REST API scaffold.
Use Spring MVC and Jakarta Validation. Assume JWT authentication and a User model exist.
Implement create, list, get, update and delete routes under /api/v1/tasks.
Each task has id, title, description, status and due_date and belongs to a user.
Resolve the owner from the authenticated JWT subject, never from request input.
Keep persistence behind an OwnedTaskStore port whose lookup and mutation methods
require both owner ID and task ID. Return 201 with Location for create, 200 for
read/update and 204 for delete. Return 404 for absent or other users' records.
Validate required fields, bounded text and enum/date payload types; use 400 for
malformed input. Do not add a rule requiring future due dates.
Include a controller, request/response records, status enum and persistence port.
Identify missing infrastructure and tests explicitly. Do not claim code compiled
or tests passed unless they were actually executed.
```

## Critical review and safeguards in the output

| Risk reviewed | Scaffold behavior | Required behavioral verification |
| --- | --- | --- |
| Client supplies another user's identity | Owner comes only from signed JWT subject | Reject missing/invalid authentication; ignore client owner fields |
| Another user's task ID is guessed | Every port operation includes owner and ID; absent result is 404 | User B cannot read/update/delete user A's task |
| Inconsistent CRUD responses | 201/Location, 200, 204 and 404 branches are explicit | MockMvc status, body and header assertions |
| Malformed payload | Required fields, enum, LocalDate and text limits | Blank title, missing status/date, bad date/enum, oversized text ->400 |
| Updating a missing record creates it | Update port returns Optional; controller maps empty to404 | Missing update must not insert |
| Arbitrary domain rules | No future-date restriction added | A past due date remains valid |

This review is by source inspection. The owner-aware port is a requirement for its eventual adapter, not proof that an adapter enforces ownership. A complete task module still needs a concrete data adapter, migration, service/domain rules, exception advice wiring, security configuration, dependency setup and the tests above. The assessment permits a scaffold or representative output; this sample deliberately supplies that form.

The main PokeSync implementation includes its own prepared unit, HTTP, PostgreSQL integration and browser tests. None were executed by the assistant. CI logs are the eventual source of runtime validation evidence.
