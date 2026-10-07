package com.pokesync.application.exception;

public class PokemonAlreadySynchronizedException extends RuntimeException {
    public PokemonAlreadySynchronizedException() { super("Pokemon is already synchronized"); }
}
