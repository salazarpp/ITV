package com.pokesync.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs only through Maven Failsafe; requires Docker when executed by the user or CI. */
@SpringBootTest(properties = {
        "pokesync.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "pokesync.security.jwt.expiration-seconds=3600",
        "pokesync.poke-api.connect-timeout-millis=1000",
        "pokesync.poke-api.read-timeout-millis=1000"
})
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PokeSyncApplicationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("pokesync_test")
            .withUsername("pokesync_test")
            .withPassword("integration-test-only");

    private static PokeApiHttpFixture upstream;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        upstream = PokeApiHttpFixture.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("pokesync.poke-api.base-url", upstream::baseUrl);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtDecoder jwtDecoder;

    @BeforeEach
    void resetUpstream() {
        upstream.reset();
    }

    @AfterAll
    static void stopUpstream() {
        if (upstream != null) upstream.close();
    }

    @Test
    void flywayMigrationsRegistrationAndJwtUseRealDatabaseAndSecurity() throws Exception {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class))
                .isGreaterThanOrEqualTo(2);
        String username = "user-" + UUID.randomUUID();
        JsonNode user = register(username);
        String passwordHash = jdbc.queryForObject(
                "select password_hash from app_users where username = ?", String.class, username);
        assertThat(passwordHash).startsWith("$2").isNotEqualTo("integration-password");
        String token = login(username);
        assertThat(jwtDecoder.decode(token).getSubject()).isEqualTo(user.get("id").asText());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(username)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "username", username, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void synchronizationAndCrudPersistSnapshotAndProprietaryFields() throws Exception {
        String username = "user-" + UUID.randomUUID();
        register(username);
        String authorization = "Bearer " + login(username);
        String createBody = """
                {"pokeApiId":1,"customName":"Bulbasaur local","region":"Kanto","internalClassification":"starter"}
                """;
        var created = mvc.perform(post("/api/v1/local-pokemon")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pokeApiId").value(1))
                .andExpect(jsonPath("$.name").value("bulbasaur"))
                .andExpect(jsonPath("$.customName").value("Bulbasaur local"))
                .andReturn();
        String id = json.readTree(created.getResponse().getContentAsString()).get("id").asText();
        JsonNode snapshot = json.readTree(jdbc.queryForObject(
                "select snapshot_json from local_pokemon where id = ?", String.class, UUID.fromString(id)));
        assertThat(snapshot.path("image").asText()).isEqualTo("https://example.com/bulbasaur-art.png");
        assertThat(snapshot.path("description").asText()).isEqualTo("A seed grows on its back.");
        assertThat(snapshot.path("stats").get(0).path("name").asText()).isEqualTo("hp");
        assertThat(snapshot.path("stats").get(0).path("value").asInt()).isEqualTo(45);
        assertThat(snapshot.path("evolution").get(1).path("name").asText()).isEqualTo("ivysaur");
        mvc.perform(post("/api/v1/local-pokemon").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isConflict());
        // The saved record remains usable even when the upstream becomes unavailable.
        upstream.reply("/pokemon/1", 503, "{}");
        mvc.perform(get("/api/v1/local-pokemon/{id}", id).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.region").value("Kanto"));
        mvc.perform(get("/api/v1/local-pokemon").param("limit", "1").param("offset", "0")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].id").value(id));
        mvc.perform(put("/api/v1/local-pokemon/{id}", id).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Updated name","region":"Johto","internalClassification":"research"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customName").value("Updated name"));
        mvc.perform(get("/api/v1/local-pokemon/{id}", id).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.region").value("Johto"))
                .andExpect(jsonPath("$.internalClassification").value("research"));
        mvc.perform(delete("/api/v1/local-pokemon/{id}", id).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/local-pokemon/{id}", id).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicPokemonListAndDetailConsumeLocalHttpUpstream() throws Exception {
        mvc.perform(get("/api/v1/pokemon").param("limit", "1").param("offset", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].id").value(1))
                .andExpect(jsonPath("$.results[0].category").value("Seed Pokemon"))
                .andExpect(jsonPath("$.results[0].mass").value(6.9));
        assertThat(upstream.requests()).anyMatch(path -> path.contains("limit=1") && path.contains("offset=0"));
        mvc.perform(get("/api/v1/pokemon/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stats[0].value").value(45))
                .andExpect(jsonPath("$.description").value("A seed grows on its back."))
                .andExpect(jsonPath("$.evolution[1].name").value("ivysaur"));
    }

    @Test
    void protectedRoutesRejectMissingAndMalformedTokensWithCorrelation() throws Exception {
        mvc.perform(get("/api/v1/local-pokemon").header("X-Request-ID", "integration-auth-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-ID", "integration-auth-1"))
                .andExpect(jsonPath("$.requestId").value("integration-auth-1"));
        mvc.perform(get("/api/v1/local-pokemon").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        var unsafeId = mvc.perform(get("/api/v1/local-pokemon").header("X-Request-ID", "unsafe|request"))
                .andExpect(status().isUnauthorized()).andReturn();
        String generated = unsafeId.getResponse().getHeader("X-Request-ID");
        assertThat(generated).isNotEqualTo("unsafe|request").matches("[A-Za-z0-9._-]{1,64}");
        assertThat(json.readTree(unsafeId.getResponse().getContentAsString()).path("requestId").asText())
                .isEqualTo(generated);
    }

    @Test
    void invalidPayloadsAndMissingRecordsProduceConsistentErrors() throws Exception {
        String username = "user-" + UUID.randomUUID();
        register(username);
        String authorization = "Bearer " + login(username);
        mvc.perform(post("/api/v1/local-pokemon").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pokeApiId\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(put("/api/v1/local-pokemon/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(post("/api/v1/local-pokemon").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/pokemon").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upstreamMissingUnavailableAndInvalidJsonAreMappedWithoutLeakingBodies() throws Exception {
        upstream.reply("/pokemon/499", 404, "{\"detail\":\"upstream-private-detail\"}");
        mvc.perform(get("/api/v1/pokemon/499").header("X-Request-ID", "integration-404"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.requestId").value("integration-404"));
        upstream.reply("/pokemon/500", 503, "{\"detail\":\"upstream-private-detail\"}");
        var unavailable = mvc.perform(get("/api/v1/pokemon/500"))
                .andExpect(status().isBadGateway()).andReturn();
        assertThat(unavailable.getResponse().getContentAsString()).doesNotContain("upstream-private-detail");
        upstream.reply("/pokemon/600", 200, "invalid-json-private-detail");
        var invalid = mvc.perform(get("/api/v1/pokemon/600"))
                .andExpect(status().isBadGateway()).andReturn();
        assertThat(invalid.getResponse().getContentAsString()).doesNotContain("invalid-json-private-detail");
    }

    @Test
    void upstreamReadTimeoutReturnsGatewayTimeout() throws Exception {
        upstream.delay("/pokemon/501", 200, "{}", Duration.ofSeconds(3));
        mvc.perform(get("/api/v1/pokemon/501"))
                .andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.status").value(504));
    }

    @Test
    void openApiAndHealthArePublicAndDocumentProtectedOperations() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/local-pokemon']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.paths['/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/local-pokemon'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/local-pokemon'].post.responses['400'].content['application/json']")
                        .exists());
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    private JsonNode register(String username) throws Exception {
        var result = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(username)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String registrationBody(String username) throws Exception {
        return json.writeValueAsString(java.util.Map.of("username", username,
                "email", username + "@example.com", "password", "integration-password"));
    }

    private String login(String username) throws Exception {
        var result = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "username", username, "password", "integration-password"))))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
