package com.pokesync.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokesync.application.exception.PokemonAlreadySynchronizedException;
import com.pokesync.application.port.out.LocalPokemonRepository;
import com.pokesync.domain.model.LocalPokemon;
import com.pokesync.domain.model.PageResult;
import com.pokesync.domain.model.StoredPokemon;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaLocalPokemonRepository implements LocalPokemonRepository {
    private final SpringDataLocalPokemonRepository repository;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    public JpaLocalPokemonRepository(SpringDataLocalPokemonRepository repository, EntityManager entityManager, ObjectMapper mapper) {
        this.repository = repository; this.entityManager = entityManager; this.mapper = mapper;
    }
    @Override
    public boolean existsByPokeApiId(int id) { return repository.existsByPokeApiId(id); }
    @Override
    @Transactional(readOnly = true)
    public PageResult<LocalPokemon> list(int limit, int offset) {
        var records = entityManager.createQuery("select p from LocalPokemonEntity p order by p.pokeApiId", LocalPokemonEntity.class)
                .setFirstResult(offset).setMaxResults(limit).getResultList().stream().map(LocalPokemonEntity::toDomain).toList();
        return new PageResult<>(repository.count(), records);
    }
    @Override
    public Optional<LocalPokemon> find(UUID id) { return repository.findById(id).map(LocalPokemonEntity::toDomain); }
    @Override
    @Transactional
    public LocalPokemon create(StoredPokemon pokemon) {
        String snapshot;
        try { snapshot = mapper.writeValueAsString(pokemon.snapshot()); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize Pokemon snapshot", exception); }
        try { return repository.saveAndFlush(new LocalPokemonEntity(pokemon.record(), snapshot)).toDomain(); }
        catch (DataIntegrityViolationException exception) {
            if (uniqueViolation(exception)) throw new PokemonAlreadySynchronizedException();
            throw exception;
        }
    }
    @Override
    @Transactional
    public Optional<LocalPokemon> update(UUID id, String customName, String region, String classification) {
        return repository.findById(id).map(entity -> {
            entity.updateFields(customName, region, classification);
            return repository.saveAndFlush(entity).toDomain();
        });
    }
    @Override
    @Transactional
    public boolean delete(UUID id) { return repository.deleteRecord(id) > 0; }
    private static boolean uniqueViolation(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) return true;
        }
        return false;
    }
}
