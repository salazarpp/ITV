package com.pokesync.application.port.out;

import com.pokesync.domain.model.LocalPokemon;
import com.pokesync.domain.model.PageResult;
import com.pokesync.domain.model.StoredPokemon;
import java.util.Optional;
import java.util.UUID;

public interface LocalPokemonRepository {
    boolean existsByPokeApiId(int id);
    PageResult<LocalPokemon> list(int limit, int offset);
    Optional<LocalPokemon> find(UUID id);
    LocalPokemon create(StoredPokemon pokemon);
    Optional<LocalPokemon> update(UUID id, String customName, String region, String classification);
    boolean delete(UUID id);
}
