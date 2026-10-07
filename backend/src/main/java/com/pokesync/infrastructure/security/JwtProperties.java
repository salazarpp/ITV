package com.pokesync.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "pokesync.security.jwt")
public record JwtProperties(@NotBlank String secret, @Positive long expirationSeconds) {
    @Override
    public String toString() {
        return "JwtProperties[secret=<redacted>, expirationSeconds=" + expirationSeconds + "]";
    }
}
