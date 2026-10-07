package com.pokesync.application.port.out;

import com.pokesync.domain.model.PageResult;
import com.pokesync.domain.model.PokemonDetail;
import com.pokesync.domain.model.PokemonSummary;

public interface PokemonProvider {
    PageResult<PokemonSummary> list(int limit, int offset);
    PokemonDetail find(int id);
}
