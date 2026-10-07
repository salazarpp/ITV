package com.pokesync.application.service;

import com.pokesync.application.exception.InvalidPokemonRequestException;
import com.pokesync.application.port.out.PokemonProvider;
import com.pokesync.domain.model.PageResult;
import com.pokesync.domain.model.PokemonDetail;
import com.pokesync.domain.model.PokemonSummary;

public class PokemonService {
    private final PokemonProvider provider;
    public PokemonService(PokemonProvider provider) { this.provider = provider; }
    public PageResult<PokemonSummary> list(int limit, int offset) {
        validatePagination(limit, offset);
        return provider.list(limit, offset);
    }
    public PokemonDetail find(int id) {
        if (id <= 0) throw new InvalidPokemonRequestException();
        return provider.find(id);
    }
    public static void validatePagination(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw new InvalidPokemonRequestException();
    }
}
