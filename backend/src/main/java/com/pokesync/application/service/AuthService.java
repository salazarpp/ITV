package com.pokesync.application.service;

import com.pokesync.application.dto.AuthToken;
import com.pokesync.application.exception.AuthenticationFailedException;
import com.pokesync.application.exception.UserAlreadyExistsException;
import com.pokesync.application.port.out.PasswordHasher;
import com.pokesync.application.port.out.TokenIssuer;
import com.pokesync.application.port.out.UserRepository;
import com.pokesync.domain.model.User;
import java.util.Locale;
import java.util.UUID;

public class AuthService {
    private final UserRepository users;
    private final PasswordHasher passwords;
    private final TokenIssuer tokens;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordHasher passwords, TokenIssuer tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyPasswordHash = passwords.hash(UUID.randomUUID().toString());
    }

    public User register(String username, String email, String password) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        if (users.existsByUsernameOrEmail(username, normalizedEmail)) {
            throw new UserAlreadyExistsException();
        }
        User user = new User(UUID.randomUUID(), username, normalizedEmail, passwords.hash(password));
        return users.save(user);
    }

    public AuthToken login(String username, String password) {
        User user = users.findByUsername(username).orElse(null);
        String passwordHash = user == null ? dummyPasswordHash : user.passwordHash();
        boolean matches = passwords.matches(password, passwordHash);
        if (user == null || !matches) {
            throw new AuthenticationFailedException();
        }
        return tokens.issue(user.id());
    }
}
