package com.pokesync.domain.model;

public record LocalPokemon(java.util.UUID id, int pokeApiId, String name, String image, String customName, String region, String internalClassification) {}
