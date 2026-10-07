package com.pokesync.infrastructure.security;

import com.pokesync.application.exception.PasswordTooLongException;
import com.pokesync.application.port.out.PasswordHasher;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new PasswordTooLongException();
        }
        return encoder.encode(password);
    }

    @Override
    public boolean matches(String password, String passwordHash) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }
        return encoder.matches(password, passwordHash);
    }
}
