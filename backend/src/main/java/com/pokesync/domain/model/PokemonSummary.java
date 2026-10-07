package com.pokesync.domain.model;

public record PokemonSummary(int id, String name, String sprite, String category, double mass, java.util.List<String> skills) {}
