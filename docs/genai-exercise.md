# Generative AI task-management exercise

This submission contains the prompt, representative generated code and review for a user-associated task API. The selected coding tool is Codex. The Java scaffold was authored with Codex earlier in this delivery; the instruction below was used for this documentation revision to review and retain that output. It is not presented as a recovered transcript of the original generation.

The output is a scaffold, outside PokeSync's application source tree. It provides task CRUD with `title`, `description`, `status` and `due_date`, assuming that JWT authentication and the existing User model are supplied by the surrounding application.

## Prompt used for this revision

```text
Generate a representative Java 21 / Spring Boot task-management REST API scaffold with create, list, get, update and delete operations under /api/v1/tasks. Each task has title, description, status and due_date and is associated with an existing user. Assume JWT authentication and the user model already exist. Resolve ownership from the authenticated JWT subject, validate request fields, use standard HTTP responses, scope every lookup/update/delete by owner and task ID, and show controller, DTOs, status enum and persistence port. Clearly identify missing adapters/security/error wiring and meaningful tests. Do not claim compilation, execution or tests that were not performed.
```

## Representative output

The complete representative controller, request/response records, status enum and persistence port are included below. The retained Java sample has the same contents.

```java
package example.taskmanagement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Representative exercise scaffold, outside PokeSync's runtime component scan. */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final OwnedTaskStore tasks;

    public TaskController(OwnedTaskStore tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    List<Task> list(@AuthenticationPrincipal Jwt principal) {
        return tasks.list(owner(principal));
    }

    @PostMapping
    ResponseEntity<Task> create(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody TaskInput input) {
        Task task = tasks.create(owner(principal), input);
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + task.id())).body(task);
    }

    @GetMapping("/{id}")
    Task find(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        return tasks.find(owner(principal), id).orElseThrow(TaskController::missing);
    }

    @PutMapping("/{id}")
    Task update(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id, @Valid @RequestBody TaskInput input) {
        return tasks.update(owner(principal), id, input).orElseThrow(TaskController::missing);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        if (!tasks.delete(owner(principal), id)) throw missing();
        return ResponseEntity.noContent().build();
    }

    private static UUID owner(Jwt principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        try {
            return UUID.fromString(principal.getSubject());
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Task was not found");
    }

    public enum Status { TODO, IN_PROGRESS, DONE }
    public record TaskInput(@NotBlank @Size(max = 255) String title,
                            @NotNull @Size(max = 4000) String description,
                            @NotNull Status status, @NotNull LocalDate due_date) {}
    public record Task(UUID id, UUID userId, String title, String description, Status status, LocalDate due_date) {}

    /** Implementations must scope every query and mutation by both owner and task ID. */
    public interface OwnedTaskStore {
        List<Task> list(UUID owner);
        Optional<Task> find(UUID owner, UUID id);
        Task create(UUID owner, TaskInput input);
        Optional<Task> update(UUID owner, UUID id, TaskInput input);
        boolean delete(UUID owner, UUID id);
    }
}
```

## API behavior and user association

| Operation | Route | Successful response |
| --- | --- | --- |
| Create | `POST /api/v1/tasks` | `201 Created`, task body and `Location` header |
| List this user's tasks | `GET /api/v1/tasks` | `200 OK`, task array |
| Read one task | `GET /api/v1/tasks/{id}` | `200 OK`, task body |
| Replace task fields | `PUT /api/v1/tasks/{id}` | `200 OK`, updated task body |
| Delete | `DELETE /api/v1/tasks/{id}` | `204 No Content` |

The authenticated JWT subject identifies the existing user. `TaskInput` contains no owner field; `Task.userId` represents the association in responses. The store must generate task IDs, save the authenticated owner's ID and enforce a relationship to the existing user. Every read, update and delete receives both owner ID and task ID; list receives only the owner ID. The controller maps an owner-scoped missing result to `404` for read, update and delete. Its update path never calls `create`; the adapter must return empty instead of inserting a missing task.

Example create or update body:

```json
{
  "title": "Review inventory",
  "description": "Check the pending inventory report.",
  "status": "TODO",
  "due_date": "2026-10-09"
}
```

## How the AI output was validated

Validation performed for this exercise was static source inspection. Each route annotation was checked against its store call and response branch. The DTOs were checked for all four requested fields and the response user association. The owner helper and every CRUD operation were reviewed for use of the JWT subject rather than client-provided ownership. The inline code was checked against the retained Java sample.

This establishes what the scaffold expresses. It does not establish that a persistence adapter enforces ownership, that a security filter validates JWTs, or that the code compiles or runs. No compilation, application startup, database operation or automated test was performed for this standalone sample. It is outside the application build, so PokeSync's CI results do not validate it.

## Corrections and improvements made

The existing Java scaffold already matched the requested representative form, so it was retained without code changes during this revision. No defective first draft or successful repair is claimed.

The documentation was improved concretely: the complete code now appears inline instead of requiring a separate linked file; the prompt is identified honestly as the instruction used for this revision; CRUD responses, existing-user association and missing wiring are explicit; and the unrelated statement about eventual PokeSync CI validation was removed. Static review and proposed behavioral checks are distinguished from executed tests.

## Authentication, validation and edge cases

| Case reviewed | Behavior expressed in the scaffold | Behavioral check still required |
| --- | --- | --- |
| Missing principal, missing subject or an unparseable user UUID | Owner helper throws `401 Unauthorized` | Exercise each helper branch through authenticated and unauthenticated HTTP requests |
| Expired, incorrectly signed or otherwise invalid JWT | Existing JWT security configuration must reject the request before the controller | Verify token validation and protected task routes |
| A user requests another user's task ID | Owner-scoped store lookup/update/delete must return empty or false; controller returns `404` | User B cannot list, read, update or delete user A's tasks |
| Task does not exist | Read/update/delete return `404` through explicit missing-result branches | Verify no update inserts a missing task and repeated deletion returns `404` |
| Empty or whitespace-only title; title longer than 255 characters | Jakarta validation rejects the request | Verify `400` and that the store is not called |
| Missing description or description longer than 4,000 characters | Jakarta validation rejects the request; an empty description is permitted | Verify boundary, null and empty cases |
| Missing status/date; unknown status; malformed date or task UUID; malformed JSON | Validation or Spring request conversion rejects invalid input | Verify consistent `400` responses through exception handling |
| Past due date | No future-date restriction exists | Verify a past date is accepted; the assessment specifies no scheduling rule |
| No tasks for the authenticated user | Store returns an empty list | Verify `200` with an empty array |
| Persistence operation fails | No dedicated storage-error behavior is implemented in this sample | Verify sanitized server errors after completing error wiring |

## Remaining wiring and meaningful tests

To turn the scaffold into an executable module, provide an `OwnedTaskStore` adapter and relational migration, enforce user foreign keys and owner predicates in that adapter, integrate the existing JWT security chain, configure validation and JSON date/enum conversion dependencies, and wire exception advice for consistent error bodies. The existing User model is assumed; registration and login are not regenerated here. Additional business rules require an explicit requirement.

The proposed tests have not been authored or run for this standalone sample. Controller unit tests should verify store arguments, response bodies, statuses and `Location`. HTTP tests should cover authentication, invalid payloads and malformed identifiers. Persistence integration tests should use two users to verify isolation across list/read/update/delete, user associations and the absence of insertion on a missing update. An automated CRUD scenario should create, read, update, delete and confirm that the deleted task returns `404`.
