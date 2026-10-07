package com.pokesync.presentation.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pokesync.application.exception.InvalidPokemonRequestException;
import com.pokesync.application.exception.PokeApiTimeoutException;
import com.pokesync.application.exception.PokeApiUnavailableException;
import com.pokesync.application.exception.PokemonAlreadySynchronizedException;
import com.pokesync.application.exception.PokemonNotFoundException;
import com.pokesync.application.service.LocalPokemonService;
import com.pokesync.application.service.PokemonService;
import com.pokesync.domain.model.LocalPokemon;
import com.pokesync.domain.model.PageResult;
import com.pokesync.domain.model.PokemonSummary;
import com.pokesync.infrastructure.security.JwtConfiguration;
import com.pokesync.infrastructure.security.SecurityConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {PokemonController.class, LocalPokemonController.class}, properties = {
        "pokesync.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "pokesync.security.jwt.expiration-seconds=3600"
})
@Import({SecurityConfiguration.class, JwtConfiguration.class})
class PokemonControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private PokemonService pokemon;
    @MockitoBean private LocalPokemonService local;
    @Test void browsingIsPublicAndPreservesContract() throws Exception {
        when(pokemon.list(20, 0)).thenReturn(new PageResult<>(1302, List.of(
                new PokemonSummary(25, "pikachu", "sprite", "Mouse Pokemon", 6.0, List.of("static")))));
        mvc.perform(get("/api/v1/pokemon")).andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1302)).andExpect(jsonPath("$.results[0].skills[0]").value("static"));
    }
    @Test void localRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/local-pokemon")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/local-pokemon").contentType(MediaType.APPLICATION_JSON).content("{\"pokeApiId\":25}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void synchronizationReturns201AndLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(local.synchronize(25, null, null, null)).thenReturn(new LocalPokemon(id, 25, "pikachu", "image", null, null, null));
        mvc.perform(post("/api/v1/local-pokemon").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"pokeApiId\":25}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/local-pokemon/" + id))
                .andExpect(jsonPath("$.pokeApiId").value(25));
    }
    @Test void malformedIdentifiersAndBodiesReturn400() throws Exception {
        mvc.perform(get("/api/v1/local-pokemon/not-a-uuid").with(jwt())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/local-pokemon").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/local-pokemon").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"pokeApiId\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/local-pokemon/" + UUID.randomUUID()).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"customName\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void invalidPaginationUses400Advice() throws Exception {
        when(pokemon.list(-1, 0)).thenThrow(new InvalidPokemonRequestException());
        mvc.perform(get("/api/v1/pokemon?limit=-1")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    @Test void missingPokemonReturns404() throws Exception {
        when(pokemon.find(99999)).thenThrow(new PokemonNotFoundException());
        mvc.perform(get("/api/v1/pokemon/99999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POKEMON_NOT_FOUND"));
    }
    @Test void duplicateSynchronizationReturns409() throws Exception {
        when(local.synchronize(25, null, null, null)).thenThrow(new PokemonAlreadySynchronizedException());
        mvc.perform(post("/api/v1/local-pokemon").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"pokeApiId\":25}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POKEMON_ALREADY_SYNCHRONIZED"));
    }
    @Test void providerFailureAndTimeoutAreDistinctGatewayErrors() throws Exception {
        when(pokemon.find(25)).thenThrow(new PokeApiUnavailableException());
        when(pokemon.find(26)).thenThrow(new PokeApiTimeoutException());
        mvc.perform(get("/api/v1/pokemon/25")).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("POKE_API_UNAVAILABLE"));
        mvc.perform(get("/api/v1/pokemon/26")).andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.code").value("POKE_API_TIMEOUT"));
    }
    @Test void deletionReturns204() throws Exception {
        mvc.perform(delete("/api/v1/local-pokemon/" + UUID.randomUUID()).with(jwt())).andExpect(status().isNoContent());
    }
}
