package com.pokesync.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pokesync.application.exception.InvalidPokemonRequestException;
import com.pokesync.application.exception.PokemonAlreadySynchronizedException;
import com.pokesync.application.exception.PokemonNotFoundException;
import com.pokesync.application.port.out.LocalPokemonRepository;
import com.pokesync.application.port.out.PokemonProvider;
import com.pokesync.domain.model.PokemonDetail;
import com.pokesync.domain.model.StoredPokemon;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalPokemonServiceTest {
    private final LocalPokemonRepository repository = mock(LocalPokemonRepository.class);
    private final PokemonProvider provider = mock(PokemonProvider.class);
    private final LocalPokemonService service = new LocalPokemonService(repository, provider);
    @Test void synchronizationStoresSnapshotAndNormalizesProprietaryFields() {
        var detail = new PokemonDetail(25, "pikachu", "image", "Mouse Pokemon", 6.0, List.of("static"), List.of(), "description", List.of());
        when(provider.find(25)).thenReturn(detail);
        when(repository.create(any())).thenAnswer(invocation -> ((StoredPokemon) invocation.getArgument(0)).record());
        var result = service.synchronize(25, "  Spark  ", "  Kanto ", "  ");
        assertThat(result.id()).isNotNull();
        assertThat(result.customName()).isEqualTo("Spark");
        assertThat(result.region()).isEqualTo("Kanto");
        assertThat(result.internalClassification()).isNull();
        var captor = ArgumentCaptor.forClass(StoredPokemon.class);
        verify(repository).create(captor.capture());
        assertThat(captor.getValue().snapshot()).isSameAs(detail);
    }
    @Test void duplicateDoesNotRequestUpstreamData() {
        when(repository.existsByPokeApiId(25)).thenReturn(true);
        assertThatThrownBy(() -> service.synchronize(25, null, null, null)).isInstanceOf(PokemonAlreadySynchronizedException.class);
        verifyNoInteractions(provider);
        verify(repository, never()).create(any());
    }
    @Test void invalidFieldsDoNotTouchPersistenceOrProvider() {
        assertThatThrownBy(() -> service.synchronize(0, null, null, null)).isInstanceOf(InvalidPokemonRequestException.class);
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), "x".repeat(256), null, null)).isInstanceOf(InvalidPokemonRequestException.class);
        verifyNoInteractions(provider, repository);
    }
    @Test void missingFindUpdateAndDeleteAreDomainNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.find(id)).thenReturn(Optional.empty());
        when(repository.update(id, null, null, null)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.find(id)).isInstanceOf(PokemonNotFoundException.class);
        assertThatThrownBy(() -> service.update(id, null, null, null)).isInstanceOf(PokemonNotFoundException.class);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(PokemonNotFoundException.class);
    }
    @Test void deletionIsLocalAndNeverFetchesProvider() {
        UUID id = UUID.randomUUID();
        when(repository.delete(id)).thenReturn(true);
        service.delete(id);
        verify(repository).delete(id);
        verifyNoInteractions(provider);
    }
}
