package com.pokesync.application.exception;

public class PasswordTooLongException extends RuntimeException {
    public PasswordTooLongException() {
        super("Password exceeds bcrypt's 72-byte limit");
    }
}
