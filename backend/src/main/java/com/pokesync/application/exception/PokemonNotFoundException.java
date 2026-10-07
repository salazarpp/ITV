package com.pokesync.application.exception;

public class PokemonNotFoundException extends RuntimeException {
    public PokemonNotFoundException() { super("Pokemon was not found"); }
}
