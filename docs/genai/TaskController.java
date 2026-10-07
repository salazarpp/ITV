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
