package com.pokesync.infrastructure.upstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pokesync.application.exception.PokemonNotFoundException;
import com.pokesync.domain.model.PokemonSummary;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

class RestPokeApiProviderConcurrencyTest {
    @Test
    void enrichmentOverlapsAcrossPagesWithGlobalCapAndKeepsReferenceOrder() throws Exception {
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        var contexts = new ConcurrentHashMap<Integer, String>();
        var fixture = new Fixture(uri -> {
            if (uri.getPath().matches(".*/pokemon/[0-9]+/")) {
                int id = id(uri);
                contexts.put(id, String.valueOf(MDC.get("requestId")));
                int concurrent = active.incrementAndGet();
                maximum.accumulateAndGet(concurrent, Math::max);
                entered.countDown();
                try { await(release); }
                finally { active.decrementAndGet(); }
            }
            return success(body(uri));
        });
        try (var provider = fixture.provider()) {
            var first = task(() -> withContext("first-page", () -> provider.list(3, 0)));
            var second = task(() -> withContext("second-page", () -> provider.list(3, 3)));
            first.thread().start(); second.thread().start();
            try {
                await(entered);
                assertThat(active.get()).isEqualTo(4);
            } finally { release.countDown(); }
            assertThat(first.future().get(5, TimeUnit.SECONDS).results())
                    .extracting(PokemonSummary::id).containsExactly(3, 2, 1);
            assertThat(second.future().get(5, TimeUnit.SECONDS).results())
                    .extracting(PokemonSummary::id).containsExactly(6, 5, 4);
            assertThat(maximum.get()).isEqualTo(4);
            assertThat(contexts).containsEntry(1, "first-page").containsEntry(2, "first-page")
                    .containsEntry(3, "first-page").containsEntry(4, "second-page")
                    .containsEntry(5, "second-page").containsEntry(6, "second-page");
            provider.list(1, 6);
            assertThat(contexts.get(7)).isEqualTo("null");
            assertThat(MDC.get("requestId")).isNull();
        } finally { release.countDown(); MDC.clear(); }
    }

    @Test
    void simultaneousSuccessfulResourceLoadsShareOneHttpFetch() throws Exception {
        sharedFetch(false);
    }

    @Test
    void simultaneousFailurePreservesDomainExceptionAndNextCallRetries() throws Exception {
        sharedFetch(true);
    }

    private void sharedFetch(boolean fail) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var fixture = new Fixture(uri -> {
            if (uri.getPath().equals("/api/v2/pokemon/25/")) {
                int call = calls.incrementAndGet();
                if (call == 1) {
                    entered.countDown(); await(release);
                    if (fail) return new MockClientHttpResponse(new byte[0], HttpStatus.NOT_FOUND);
                }
            }
            return success(body(uri));
        });
        try (var provider = fixture.provider()) {
            var owner = task(() -> provider.find(25));
            var waiter = task(() -> provider.find(25));
            owner.thread().start(); await(entered);
            waiter.thread().start();
            try {
                awaitFlightWait(waiter.thread());
                assertThat(calls.get()).isEqualTo(1);
            } finally { release.countDown(); }
            if (fail) {
                Throwable first = failure(owner.future());
                Throwable second = failure(waiter.future());
                assertThat(first).isInstanceOf(PokemonNotFoundException.class);
                assertThat(second).isSameAs(first);
                assertThat(provider.find(25).id()).isEqualTo(25);
                assertThat(calls.get()).isEqualTo(2);
            } else {
                assertThat(owner.future().get(5, TimeUnit.SECONDS)).isEqualTo(waiter.future().get(5, TimeUnit.SECONDS));
                assertThat(provider.find(25).id()).isEqualTo(25);
                assertThat(calls.get()).isEqualTo(1);
            }
        } finally { release.countDown(); }
    }

    private static Throwable failure(FutureTask<?> future) throws Exception {
        try { future.get(5, TimeUnit.SECONDS); throw new AssertionError("Expected shared failure"); }
        catch (java.util.concurrent.ExecutionException exception) { return exception.getCause(); }
    }

    private static void awaitFlightWait(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Arrays.stream(thread.getStackTrace()).anyMatch(frame ->
                    frame.getClassName().equals("java.util.concurrent.CompletableFuture")
                            && frame.getMethodName().equals("waitingGet"))) return;
            if (!thread.isAlive()) throw new AssertionError("Caller finished before sharing the in-flight fetch");
            Thread.onSpinWait();
        }
        throw new AssertionError("Caller did not wait on the shared resource future");
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(5, TimeUnit.SECONDS)).as("Latch completed within the safety deadline").isTrue(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    private static <T> T withContext(String requestId, java.util.concurrent.Callable<T> action) throws Exception {
        MDC.put("requestId", requestId);
        try { return action.call(); }
        finally { MDC.remove("requestId"); }
    }

    private static <T> Task<T> task(java.util.concurrent.Callable<T> action) {
        var future = new FutureTask<T>(action);
        return new Task<>(new Thread(future, "provider-test-caller"), future);
    }

    private record Task<T>(Thread thread, FutureTask<T> future) {}

    private record Fixture(Function<URI, MockClientHttpResponse> response) {
        RestPokeApiProvider provider() {
            ClientHttpRequestFactory factory = (uri, method) -> {
                ClientHttpRequest request = mock(ClientHttpRequest.class);
                when(request.getURI()).thenReturn(uri);
                when(request.getMethod()).thenReturn(method);
                when(request.getHeaders()).thenReturn(new HttpHeaders());
                when(request.getBody()).thenReturn(new ByteArrayOutputStream());
                when(request.execute()).thenAnswer(invocation -> response.apply(uri));
                return request;
            };
            return new RestPokeApiProvider(RestClient.builder().baseUrl("https://fixture.invalid/api/v2")
                    .requestFactory(factory).build());
        }
    }

    private static MockClientHttpResponse success(String json) {
        var response = new MockClientHttpResponse(json.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response;
    }

    private static int id(URI uri) {
        String[] parts = uri.getPath().split("/");
        return Integer.parseInt(parts[parts.length - 1]);
    }

    private static String body(URI uri) {
        if (uri.getPath().endsWith("/pokemon/")) {
            int offset = Integer.parseInt(uri.getQuery().split("offset=")[1]);
            int limit = Integer.parseInt(uri.getQuery().split("limit=")[1].split("&")[0]);
            return "{\"count\":100,\"results\":[" + java.util.stream.IntStream.range(0, limit)
                    .mapToObj(index -> "{\"url\":\"https://fixture.invalid/api/v2/pokemon/"
                            + (offset + limit - index) + "/\"}")
                    .collect(java.util.stream.Collectors.joining(",")) + "]}";
        }
        if (uri.getPath().contains("/pokemon-species/")) return """
                {"genera":[{"genus":"Fixture Pokemon","language":{"name":"en"}}],
                 "flavor_text_entries":[],"evolution_chain":null}
                """;
        return """
                {"id":%d,"name":"pokemon-%d","weight":10,"species":{"url":"https://fixture.invalid/api/v2/pokemon-species/%d/"},
                 "abilities":[],"stats":[],"sprites":{"front_default":"sprite"}}
                """.formatted(id(uri), id(uri), id(uri));
    }
}
