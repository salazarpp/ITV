package com.pokesync.application.exception;

public class PokeApiTimeoutException extends RuntimeException {
    public PokeApiTimeoutException() { super("Pokemon provider request timed out"); }
    public PokeApiTimeoutException(Throwable cause) { super("Pokemon provider request timed out", cause); }
}
