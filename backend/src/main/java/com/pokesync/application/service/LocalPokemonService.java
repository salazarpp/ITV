package com.pokesync.application.service;

import com.pokesync.application.exception.InvalidPokemonRequestException;
import com.pokesync.application.exception.PokemonAlreadySynchronizedException;
import com.pokesync.application.exception.PokemonNotFoundException;
import com.pokesync.application.port.out.LocalPokemonRepository;
import com.pokesync.application.port.out.PokemonProvider;
import com.pokesync.domain.model.LocalPokemon;
import com.pokesync.domain.model.PageResult;
import com.pokesync.domain.model.StoredPokemon;
import java.util.UUID;

public class LocalPokemonService {
    private final LocalPokemonRepository repository;
    private final PokemonProvider provider;
    public LocalPokemonService(LocalPokemonRepository repository, PokemonProvider provider) {
        this.repository = repository;
        this.provider = provider;
    }
    public PageResult<LocalPokemon> list(int limit, int offset) {
        PokemonService.validatePagination(limit, offset);
        return repository.list(limit, offset);
    }
    public LocalPokemon find(UUID id) {
        return repository.find(id).orElseThrow(PokemonNotFoundException::new);
    }
    public LocalPokemon synchronize(int pokeApiId, String customName, String region, String classification) {
        if (pokeApiId <= 0) throw new InvalidPokemonRequestException();
        validateFields(customName, region, classification);
        if (repository.existsByPokeApiId(pokeApiId)) throw new PokemonAlreadySynchronizedException();
        var snapshot = provider.find(pokeApiId);
        var record = new LocalPokemon(UUID.randomUUID(), snapshot.id(), snapshot.name(), snapshot.image(),
                normalize(customName), normalize(region), normalize(classification));
        return repository.create(new StoredPokemon(record, snapshot));
    }
    public LocalPokemon update(UUID id, String customName, String region, String classification) {
        validateFields(customName, region, classification);
        return repository.update(id, normalize(customName), normalize(region), normalize(classification))
                .orElseThrow(PokemonNotFoundException::new);
    }
    public void delete(UUID id) {
        if (!repository.delete(id)) throw new PokemonNotFoundException();
    }
    private static void validateFields(String... fields) {
        for (String field : fields) if (field != null && field.length() > 255) throw new InvalidPokemonRequestException();
    }
    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
