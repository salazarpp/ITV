package com.pokesync.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokesync.application.exception.PokemonAlreadySynchronizedException;
import com.pokesync.domain.model.LocalPokemon;
import com.pokesync.domain.model.PokemonDetail;
import com.pokesync.domain.model.StoredPokemon;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class JpaLocalPokemonRepositoryTest {
    private final SpringDataLocalPokemonRepository data = mock(SpringDataLocalPokemonRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final JpaLocalPokemonRepository repository = new JpaLocalPokemonRepository(data, mock(EntityManager.class), mapper);
    private StoredPokemon pokemon() {
        var record = new LocalPokemon(UUID.randomUUID(), 25, "pikachu", "image", "Spark", "Kanto", null);
        var snapshot = new PokemonDetail(25, "pikachu", "image", "Mouse Pokemon", 6, List.of("static"), List.of(), "description", List.of());
        return new StoredPokemon(record, snapshot);
    }
    @Test void assignedUuidCreatesNewEntity() {
        var stored = pokemon();
        when(data.saveAndFlush(any())).thenAnswer(invocation -> {
            LocalPokemonEntity entity = invocation.getArgument(0);
            assertThat(entity.isNew()).isTrue();
            assertThat(entity.getId()).isEqualTo(stored.record().id());
            entity.markPersisted();
            assertThat(entity.isNew()).isFalse();
            return entity;
        });
        assertThat(repository.create(stored)).isEqualTo(stored.record());
    }
    @Test void uniqueRaceBecomesSynchronizationConflict() {
        when(data.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("database details", new SQLException("constraint", "23505")));
        assertThatThrownBy(() -> repository.create(pokemon())).isInstanceOf(PokemonAlreadySynchronizedException.class);
    }
    @Test void otherDatabaseConstraintFailuresRemainInternalErrors() {
        var failure = new DataIntegrityViolationException("not-null", new SQLException("column", "23502"));
        when(data.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> repository.create(pokemon())).isSameAs(failure);
    }
    @Test void updatesMetadataAndPreservesExternalIdentity() {
        var stored = pokemon();
        var entity = new LocalPokemonEntity(stored.record(), "{}"); entity.markPersisted();
        when(data.findById(stored.record().id())).thenReturn(Optional.of(entity));
        when(data.saveAndFlush(entity)).thenReturn(entity);
        var result = repository.update(stored.record().id(), "New name", "Johto", "team").orElseThrow();
        assertThat(result.pokeApiId()).isEqualTo(25);
        assertThat(result.name()).isEqualTo("pikachu");
        assertThat(result.customName()).isEqualTo("New name");
        assertThat(result.region()).isEqualTo("Johto");
    }
    @Test void deletionUsesAtomicAffectedRowCount() {
        UUID existing = UUID.randomUUID(); UUID missing = UUID.randomUUID();
        when(data.deleteRecord(existing)).thenReturn(1);
        when(data.deleteRecord(missing)).thenReturn(0);
        assertThat(repository.delete(existing)).isTrue();
        assertThat(repository.delete(missing)).isFalse();
    }
}
