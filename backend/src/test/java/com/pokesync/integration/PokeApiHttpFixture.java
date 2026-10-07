package com.pokesync.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local-only HTTP upstream used when the integration suite is executed. */
final class PokeApiHttpFixture implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Response> responses = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();

    private PokeApiHttpFixture() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v2/", this::respond);
        server.setExecutor(executor);
        server.start();
        reset();
    }

    static PokeApiHttpFixture start() {
        try {
            return new PokeApiHttpFixture();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v2";
    }

    List<String> requests() {
        return List.copyOf(requests);
    }

    void reset() {
        responses.clear();
        requests.clear();
        reply("/pokemon", 200, """
                {"count":1,"next":null,"previous":null,"results":[
                  {"name":"bulbasaur","url":"%s/pokemon/1/"}]}
                """.formatted(baseUrl()));
        reply("/pokemon/1", 200, """
                {"id":1,"name":"bulbasaur","weight":69,
                 "sprites":{"front_default":"https://example.com/bulbasaur.png",
                   "other":{"official-artwork":{"front_default":"https://example.com/bulbasaur-art.png"}}},
                 "species":{"name":"bulbasaur","url":"%s/pokemon-species/1/"},
                 "abilities":[{"ability":{"name":"overgrow"},"is_hidden":false,"slot":1}],
                 "moves":[{"move":{"name":"tackle"}}],
                 "types":[{"slot":1,"type":{"name":"grass"}},{"slot":2,"type":{"name":"poison"}}],
                 "stats":[{"base_stat":45,"stat":{"name":"hp"}}]}
                """.formatted(baseUrl()));
        reply("/pokemon-species/1", 200, """
                {"id":1,"name":"bulbasaur",
                 "genera":[{"genus":"Seed Pokemon","language":{"name":"en"}}],
                 "flavor_text_entries":[{"flavor_text":"A seed grows on its back.","language":{"name":"en"}}],
                 "evolution_chain":{"url":"%s/evolution-chain/1/"}}
                """.formatted(baseUrl()));
        reply("/evolution-chain/1", 200, """
                {"id":1,"chain":{"species":{"name":"bulbasaur","url":"%s/pokemon-species/1/"},
                  "evolves_to":[{"species":{"name":"ivysaur","url":"%s/pokemon-species/2/"},
                    "evolves_to":[]}]}}
                """.formatted(baseUrl(), baseUrl()));
    }

    void reply(String path, int status, String json) {
        delay(path, status, json, Duration.ZERO);
    }

    void delay(String path, int status, String json, Duration delay) {
        responses.put(path, new Response(status, json, delay));
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            requests.add(exchange.getRequestURI().toString());
            String path = exchange.getRequestURI().getPath().substring("/api/v2".length());
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            Response response = responses.getOrDefault(path, new Response(404, "{}", Duration.ZERO));
            if (!response.delay().isZero()) {
                try {
                    Thread.sleep(response.delay());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            byte[] body = response.json().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), body.length);
            exchange.getResponseBody().write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private record Response(int status, String json, Duration delay) {}
}
