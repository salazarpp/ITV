package com.pokesync.domain.model;

public record PokemonDetail(int id, String name, String image, String category, double mass, java.util.List<String> skills, java.util.List<PokemonStat> stats, String description, java.util.List<EvolutionPokemon> evolution) {}
