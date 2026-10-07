package com.pokesync.infrastructure.upstream;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@EnableConfigurationProperties(PokeApiProperties.class)
public class RestPokeApiProvider implements PokemonProvider {
    private static final Logger log = LoggerFactory.getLogger(RestPokeApiProvider.class);
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private final RestClient client;
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
        List<PokemonSummary> results = new ArrayList<>();
        for (JsonNode reference : page.path("results")) {
            int id = resourceId(requiredText(reference, "url"));
            JsonNode pokemon = get("/pokemon/" + id + "/");
            JsonNode species = species(pokemon);
            results.add(new PokemonSummary(requiredId(pokemon), requiredText(pokemon, "name"),
                    optionalText(pokemon.path("sprites"), "front_default"), category(species),
                    mass(pokemon), skills(pokemon)));
        }
        return new PageResult<>(page.path("count").longValue(), results);
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

    private JsonNode get(String path) {
        synchronized (cache) {
            var entry = cache.get(path);
            if (entry != null && entry.expiresAt().isAfter(Instant.now())) {
                log.debug("POKESYNC-UPSTREAM-0003 | cache_hit path={}", path);
                return entry.body();
            }
            cache.remove(path);
        }
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
