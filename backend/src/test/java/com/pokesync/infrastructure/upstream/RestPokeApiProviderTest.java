package com.pokesync.infrastructure.upstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.pokesync.application.exception.PokeApiTimeoutException;
import com.pokesync.application.exception.PokeApiUnavailableException;
import com.pokesync.application.exception.PokemonNotFoundException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestPokeApiProviderTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://fixture.invalid/api/v2");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RestPokeApiProvider provider = new RestPokeApiProvider(builder.build());
    @AfterEach void closeProvider() { provider.close(); }
    private static final String POKEMON = """
            {"id":25,"name":"pikachu","weight":60,"species":{"url":"https://fixture.invalid/api/v2/pokemon-species/25/"},
             "abilities":[{"ability":{"name":"static"}}],"stats":[{"base_stat":90,"stat":{"name":"speed"}}],
             "sprites":{"front_default":"sprite","other":{"official-artwork":{"front_default":"artwork"}}}}
            """;
    private static final String SPECIES = """
            {"genera":[{"genus":"Mouse Pokemon","language":{"name":"en"}}],
             "flavor_text_entries":[{"flavor_text":"Stores\\nenergy\\fin its cheeks.","language":{"name":"en"}}],
             "evolution_chain":{"url":"https://fixture.invalid/api/v2/evolution-chain/10/"}}
            """;
    @Test void detailMapsSpeciesArtworkStatsEvolutionAndCachesResources() {
        server.expect(requestTo("https://fixture.invalid/api/v2/pokemon/25/")).andRespond(withSuccess(POKEMON, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://fixture.invalid/api/v2/pokemon-species/25/")).andRespond(withSuccess(SPECIES, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://fixture.invalid/api/v2/evolution-chain/10/")).andRespond(withSuccess("""
                {"chain":{"species":{"name":"pichu","url":"https://fixture.invalid/api/v2/pokemon-species/172/"},
                  "evolves_to":[{"species":{"name":"pikachu","url":"https://fixture.invalid/api/v2/pokemon-species/25/"},"evolves_to":[]}]}}
                """, MediaType.APPLICATION_JSON));
        var detail = provider.find(25);
        assertThat(detail.mass()).isEqualTo(6.0);
        assertThat(detail.image()).isEqualTo("artwork");
        assertThat(detail.category()).isEqualTo("Mouse Pokemon");
        assertThat(detail.skills()).containsExactly("static");
        assertThat(detail.stats().get(0).value()).isEqualTo(90);
        assertThat(detail.description()).isEqualTo("Stores energy in its cheeks.");
        assertThat(detail.evolution()).extracting(value -> value.name()).containsExactly("pichu", "pikachu");
        assertThat(provider.find(25)).isEqualTo(detail);
        server.verify();
    }
    @Test void paginationEnrichesEntriesAndPreservesCount() {
        server.expect(requestTo("https://fixture.invalid/api/v2/pokemon/?limit=1&offset=24")).andRespond(withSuccess("""
                {"count":1302,"results":[{"name":"pikachu","url":"https://fixture.invalid/api/v2/pokemon/25/"}]}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://fixture.invalid/api/v2/pokemon/25/")).andRespond(withSuccess(POKEMON, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://fixture.invalid/api/v2/pokemon-species/25/")).andRespond(withSuccess(SPECIES, MediaType.APPLICATION_JSON));
        var page = provider.list(1, 24);
        assertThat(page.count()).isEqualTo(1302);
        assertThat(page.results().get(0).sprite()).isEqualTo("sprite");
        server.verify();
    }
    @Test void upstreamNotFoundIs404DomainFailure() {
        server.expect(requestTo("https://fixture.invalid/api/v2/pokemon/99999/")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> provider.find(99999)).isInstanceOf(PokemonNotFoundException.class);
        server.verify();
    }
    @Test void upstreamFailuresDoNotLeakBody() {
        server.expect(requestTo("https://fixture.invalid/api/v2/pokemon/25/"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("private-upstream-body"));
        assertThatThrownBy(() -> provider.find(25)).isInstanceOf(PokeApiUnavailableException.class)
                .hasMessage("Pokemon provider is unavailable");
        server.verify();
    }
    @Test void timeoutHasDedicatedDomainFailure() {
        server.expect(requestTo("https://fixture.invalid/api/v2/pokemon/25/"))
                .andRespond(withException(new SocketTimeoutException("private-address")));
        assertThatThrownBy(() -> provider.find(25)).isInstanceOf(PokeApiTimeoutException.class)
                .hasMessage("Pokemon provider request timed out");
        server.verify();
    }
    @Test void malformedProviderDataDoesNotBecomeClient400OrRaw500() {
        server.expect(requestTo("https://fixture.invalid/api/v2/pokemon/25/"))
                .andRespond(withSuccess("{\"name\":\"pikachu\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> provider.find(25)).isInstanceOf(PokeApiUnavailableException.class);
        server.verify();
    }
}
