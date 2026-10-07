package com.pokesync.domain.model;

public record StoredPokemon(LocalPokemon record, PokemonDetail snapshot) {}
