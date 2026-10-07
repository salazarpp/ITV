package com.pokesync.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pokesync.application.exception.PasswordTooLongException;
import org.junit.jupiter.api.Test;

class BCryptPasswordHasherTest {
    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void hashesAreSaltedAndOnlyMatchTheCorrectPassword() {
        String first = hasher.hash("example-password");
        String second = hasher.hash("example-password");

        assertThat(first).isNotEqualTo(second).isNotEqualTo("example-password");
        assertThat(hasher.matches("example-password", first)).isTrue();
        assertThat(hasher.matches("wrong-password", first)).isFalse();
    }

    @Test
    void rejectsMultibytePasswordsThatExceedBcryptByteLimit() {
        String password = "é".repeat(37);

        assertThatThrownBy(() -> hasher.hash(password)).isInstanceOf(PasswordTooLongException.class);
        assertThat(hasher.matches(password, hasher.hash("example-password"))).isFalse();
    }

    @Test
    void acceptsTheExactByteLimit() {
        String password = "a".repeat(72);
        assertThat(hasher.matches(password, hasher.hash(password))).isTrue();
    }
}
