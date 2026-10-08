package com.pokesync.infrastructure.upstream;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import com.pokesync.application.exception.PokeApiTimeoutException;
import com.pokesync.application.exception.PokeApiUnavailableException;
import com.pokesync.application.exception.PokemonNotFoundException;
import com.pokesync.application.port.out.PokemonProvider;
import com.pokesync.domain.model.EvolutionPokemon;
import com.pokesync.domain.model.PageResult;
import com.pokesync.domain.model.PokemonDetail;
import com.pokesync.domain.model.PokemonStat;
import com.pokesync.domain.model.PokemonSummary;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@EnableConfigurationProperties(PokeApiProperties.class)
public class RestPokeApiProvider implements PokemonProvider, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(RestPokeApiProvider.class);
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int ENRICHMENT_WORKERS = 4;
    private static final int MAX_PENDING_ENRICHMENTS = 8;
    private final RestClient client;
    private final Semaphore submissionSlots = new Semaphore(MAX_PENDING_ENRICHMENTS, true);
    private final ThreadPoolExecutor enrichments = createEnrichmentExecutor();
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> inFlight = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(32, 0.75f, true);

    @Autowired
    public RestPokeApiProvider(RestClient.Builder builder, PokeApiProperties properties) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis())).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()));
        client = builder.baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
    }

    RestPokeApiProvider(RestClient client) { this.client = client; }

    @Override
    public PageResult<PokemonSummary> list(int limit, int offset) {
        JsonNode page = get("/pokemon/?limit=" + limit + "&offset=" + offset);
        if (!page.path("count").canConvertToLong() || page.path("count").longValue() < 0
                || !page.path("results").isArray() || page.path("results").size() > limit) unavailable();
        List<Future<PokemonSummary>> pending = new ArrayList<>();
        try {
            for (JsonNode reference : page.path("results")) {
                int id = resourceId(requiredText(reference, "url"));
                pending.add(submitEnrichment(id, MDC.getCopyOfContextMap()));
            }
            List<PokemonSummary> results = new ArrayList<>();
            for (Future<PokemonSummary> result : pending) results.add(await(result));
            return new PageResult<>(page.path("count").longValue(), results);
        } finally {
            // Cancel queued work; let running fetches finish for other flight waiters.
            for (Future<PokemonSummary> result : pending) result.cancel(false);
        }
    }

    private Future<PokemonSummary> submitEnrichment(int id, Map<String, String> context) {
        try {
            while (!submissionSlots.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                if (enrichments.isShutdown()) throw new RejectedExecutionException();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PokeApiUnavailableException(exception);
        } catch (RejectedExecutionException exception) {
            throw new PokeApiUnavailableException(exception);
        }
        FutureTask<PokemonSummary> task = new FutureTask<>(() -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                restoreContext(context);
                JsonNode pokemon = get("/pokemon/" + id + "/");
                JsonNode species = species(pokemon);
                return new PokemonSummary(requiredId(pokemon), requiredText(pokemon, "name"),
                        optionalText(pokemon.path("sprites"), "front_default"), category(species),
                        mass(pokemon), skills(pokemon));
            } finally {
                restoreContext(previous);
            }
        }) {
            @Override protected void done() {
                // Removal precedes release so cancelled queued tasks cannot fill the queue.
                enrichments.remove(this);
                submissionSlots.release();
            }
        };
        try {
            enrichments.execute(task);
            return task;
        } catch (RuntimeException | Error failure) {
            task.cancel(false);
            if (failure instanceof Error error) throw error;
            throw new PokeApiUnavailableException(failure);
        }
    }

    private static ThreadPoolExecutor createEnrichmentExecutor() {
        AtomicInteger number = new AtomicInteger();
        var executor = new ThreadPoolExecutor(ENRICHMENT_WORKERS, ENRICHMENT_WORKERS, 30,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(MAX_PENDING_ENRICHMENTS), task -> {
                    Thread thread = new Thread(task, "pokesync-enrichment-" + number.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static void restoreContext(Map<String, String> context) {
        if (context == null) MDC.clear();
        else MDC.setContextMap(context);
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PokeApiUnavailableException(exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException failure) throw failure;
            if (cause instanceof Error failure) throw failure;
            throw new PokeApiUnavailableException(cause);
        } catch (CancellationException exception) {
            throw new PokeApiUnavailableException(exception);
        }
    }

    @PreDestroy
    @Override
    public void close() {
        for (Runnable queued : enrichments.shutdownNow()) {
            if (queued instanceof Future<?> future) future.cancel(false);
        }
        try {
            enrichments.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public PokemonDetail find(int id) {
        JsonNode pokemon = get("/pokemon/" + id + "/");
        JsonNode species = species(pokemon);
        List<PokemonStat> stats = new ArrayList<>();
        if (!pokemon.path("stats").isArray()) unavailable();
        for (JsonNode stat : pokemon.path("stats")) {
            if (!stat.path("base_stat").canConvertToInt()) unavailable();
            stats.add(new PokemonStat(requiredText(stat.path("stat"), "name"), stat.path("base_stat").intValue()));
        }
        String image = optionalText(pokemon.path("sprites").path("other").path("official-artwork"), "front_default");
        if (image == null) image = optionalText(pokemon.path("sprites"), "front_default");
        List<EvolutionPokemon> evolution = new ArrayList<>();
        if (!species.path("evolution_chain").isNull() && !species.path("evolution_chain").isMissingNode()) {
            int chainId = resourceId(requiredText(species.path("evolution_chain"), "url"));
            collectEvolution(get("/evolution-chain/" + chainId + "/").path("chain"), evolution, 0);
        }
        String description = "";
        for (JsonNode entry : species.path("flavor_text_entries")) {
            if ("en".equals(entry.path("language").path("name").asText())) {
                description = requiredText(entry, "flavor_text").replaceAll("\\s+", " ").strip();
                break;
            }
        }
        return new PokemonDetail(requiredId(pokemon), requiredText(pokemon, "name"), image,
                category(species), mass(pokemon), skills(pokemon), List.copyOf(stats), description,
                List.copyOf(evolution));
    }

    private JsonNode species(JsonNode pokemon) {
        int speciesId = resourceId(requiredText(pokemon.path("species"), "url"));
        return get("/pokemon-species/" + speciesId + "/");
    }
    private String category(JsonNode species) {
        for (JsonNode genus : species.path("genera")) {
            if ("en".equals(genus.path("language").path("name").asText())) return requiredText(genus, "genus");
        }
        return "Unknown";
    }
    private double mass(JsonNode pokemon) {
        if (!pokemon.path("weight").canConvertToInt()) unavailable();
        return pokemon.path("weight").intValue() / 10.0;
    }
    private List<String> skills(JsonNode pokemon) {
        if (!pokemon.path("abilities").isArray()) unavailable();
        List<String> result = new ArrayList<>();
        for (JsonNode ability : pokemon.path("abilities")) result.add(requiredText(ability.path("ability"), "name"));
        return List.copyOf(result);
    }
    private void collectEvolution(JsonNode link, List<EvolutionPokemon> result, int depth) {
        if (depth > 32 || result.size() > 100) unavailable();
        var species = link.path("species");
        result.add(new EvolutionPokemon(resourceId(requiredText(species, "url")), requiredText(species, "name")));
        if (!link.path("evolves_to").isArray()) unavailable();
        for (JsonNode child : link.path("evolves_to")) collectEvolution(child, result, depth + 1);
    }

    private JsonNode cached(String path) {
        synchronized (cache) {
            var entry = cache.get(path);
            if (entry != null && entry.expiresAt().isAfter(Instant.now())) {
                return entry.body();
            }
            cache.remove(path);
            return null;
        }
    }

    private JsonNode get(String path) {
        JsonNode cached = cached(path);
        if (cached != null) {
            log.info("POKESYNC-UPSTREAM-0003 | cache_hit path={}", path);
            return cached;
        }
        log.info("POKESYNC-UPSTREAM-0004 | cache_miss path={}", path);
        CompletableFuture<JsonNode> ownFlight = new CompletableFuture<>();
        CompletableFuture<JsonNode> existing = inFlight.putIfAbsent(path, ownFlight);
        if (existing != null) {
            log.info("POKESYNC-UPSTREAM-0005 | in_flight_wait path={}", path);
            return await(existing);
        }
        try {
            // Another owner may have populated the cache before this flight was registered.
            cached = cached(path);
            if (cached != null) {
                log.info("POKESYNC-UPSTREAM-0006 | cache_recheck_hit path={}", path);
            }
            JsonNode response = cached != null ? cached : fetch(path);
            ownFlight.complete(response);
            return response;
        } catch (RuntimeException | Error failure) {
            ownFlight.completeExceptionally(failure);
            throw failure;
        } finally {
            inFlight.remove(path, ownFlight);
        }
    }

    private JsonNode fetch(String path) {
        long started = System.nanoTime();
        int[] status = {0};
        log.info("POKESYNC-UPSTREAM-0001 | start GET path={}", path);
        try {
            JsonNode response = client.get().uri(path).retrieve()
                    .onStatus(code -> code.value() == 404, (request, upstream) -> { status[0] = 404; throw new PokemonNotFoundException(); })
                    .onStatus(code -> code.isError(), (request, upstream) -> { status[0] = upstream.getStatusCode().value(); throw new PokeApiUnavailableException(); })
                    .onStatus(code -> !code.isError(), (request, upstream) -> { status[0] = upstream.getStatusCode().value(); })
                    .body(JsonNode.class);
            if (response == null || !response.isObject()) throw new PokeApiUnavailableException();
            synchronized (cache) {
                cache.put(path, new CacheEntry(response, Instant.now().plus(CACHE_TTL)));
                while (cache.size() > MAX_CACHE_ENTRIES) cache.remove(cache.keySet().iterator().next());
            }
            return response;
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) throw new PokeApiTimeoutException(exception);
            throw new PokeApiUnavailableException(exception);
        } catch (RestClientException exception) {
            throw new PokeApiUnavailableException(exception);
        } finally {
            log.info("POKESYNC-UPSTREAM-0002 | complete GET path={} status={} duration_ms={}",
                    path, status[0], (System.nanoTime() - started) / 1_000_000);
        }
    }
    private static boolean isTimeout(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) return true;
        }
        return false;
    }
    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) throw new PokeApiUnavailableException();
        return value.asText();
    }
    private static String optionalText(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText() : null;
    }
    private static int requiredId(JsonNode node) {
        if (!node.path("id").canConvertToInt() || node.path("id").intValue() <= 0) throw new PokeApiUnavailableException();
        return node.path("id").intValue();
    }
    private static int resourceId(String url) {
        try {
            String path = URI.create(url).getPath();
            String[] segments = path.split("/");
            int id = Integer.parseInt(segments[segments.length - 1]);
            if (id <= 0) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException | NullPointerException | IndexOutOfBoundsException exception) {
            throw new PokeApiUnavailableException();
        }
    }
    private static void unavailable() { throw new PokeApiUnavailableException(); }
    private record CacheEntry(JsonNode body, Instant expiresAt) {}
}
