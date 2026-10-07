package com.pokesync.application.exception;

public class PokeApiUnavailableException extends RuntimeException {
    public PokeApiUnavailableException() { super("Pokemon provider is unavailable"); }
    public PokeApiUnavailableException(Throwable cause) { super("Pokemon provider is unavailable", cause); }
}
