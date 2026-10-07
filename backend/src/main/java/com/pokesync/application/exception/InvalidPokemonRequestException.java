package com.pokesync.application.exception;

public class InvalidPokemonRequestException extends RuntimeException {
    public InvalidPokemonRequestException() { super("Invalid Pokemon request"); }
}
