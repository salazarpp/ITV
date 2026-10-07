package com.pokesync.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pokesync.application.dto.AuthToken;
import com.pokesync.application.exception.AuthenticationFailedException;
import com.pokesync.application.exception.UserAlreadyExistsException;
import com.pokesync.application.port.out.PasswordHasher;
import com.pokesync.application.port.out.TokenIssuer;
import com.pokesync.application.port.out.UserRepository;
import com.pokesync.domain.model.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthServiceTest {
    private UserRepository users;
    private PasswordHasher passwords;
    private TokenIssuer tokens;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        passwords = mock(PasswordHasher.class);
        tokens = mock(TokenIssuer.class);
        when(passwords.hash(anyString())).thenReturn("dummy-hash");
        auth = new AuthService(users, passwords, tokens);
    }

    @Test
    void registrationStoresHashAndNormalizesEmail() {
        when(passwords.hash("example-password")).thenReturn("encoded-password");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        User registered = auth.register("trainer", "TRAINER@example.com", "example-password");

        assertThat(registered.id()).isNotNull();
        assertThat(registered.email()).isEqualTo("trainer@example.com");
        assertThat(registered.passwordHash()).isEqualTo("encoded-password");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().passwordHash()).isNotEqualTo("example-password");
        verify(tokens, never()).issue(any());
    }

    @Test
    void duplicateRegistrationDoesNotPersistAnotherUser() {
        when(users.existsByUsernameOrEmail("trainer", "trainer@example.com")).thenReturn(true);

        assertThatThrownBy(() -> auth.register("trainer", "trainer@example.com", "example-password"))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(users, never()).save(any());
    }

    @Test
    void loginIssuesTokenForStoredUserId() {
        UUID id = UUID.randomUUID();
        when(users.findByUsername("trainer"))
                .thenReturn(Optional.of(new User(id, "trainer", "trainer@example.com", "stored-hash")));
        when(passwords.matches("example-password", "stored-hash")).thenReturn(true);
        when(tokens.issue(id)).thenReturn(new AuthToken("test-token", 3600));

        assertThat(auth.login("trainer", "example-password")).isEqualTo(new AuthToken("test-token", 3600));
    }

    @Test
    void incorrectPasswordNeverIssuesToken() {
        when(users.findByUsername("trainer")).thenReturn(Optional.of(
                new User(UUID.randomUUID(), "trainer", "trainer@example.com", "stored-hash")));

        assertThatThrownBy(() -> auth.login("trainer", "wrong-password"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");

        verify(tokens, never()).issue(any());
    }

    @Test
    void unknownUserStillPerformsPasswordComparisonAndReturnsSameError() {
        when(users.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.login("unknown", "example-password"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");

        verify(passwords).matches("example-password", "dummy-hash");
        verify(tokens, never()).issue(any());
    }
}
