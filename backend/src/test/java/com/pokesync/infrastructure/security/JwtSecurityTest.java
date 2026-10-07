package com.pokesync.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

class JwtSecurityTest {
    private final JwtConfiguration configuration = new JwtConfiguration();
    // Public, deterministic test key; never use this value in a running deployment.
    private final JwtProperties properties = new JwtProperties(Base64.getEncoder().encodeToString(new byte[32]), 3600);

    @Test
    void issuedTokenHasExpectedSubjectIssuerAndExpiration() {
        SecretKey key = configuration.jwtSigningKey(properties);
        UUID id = UUID.randomUUID();
        JwtTokenIssuer issuer = new JwtTokenIssuer(configuration.jwtEncoder(key), properties);

        var issued = issuer.issue(id);
        var decoded = configuration.jwtDecoder(key).decode(issued.accessToken());

        assertThat(decoded.getSubject()).isEqualTo(id.toString());
        assertThat(decoded.getClaimAsString("iss")).isEqualTo(JwtConfiguration.ISSUER);
        assertThat(decoded.getExpiresAt()).isAfter(Instant.now());
        assertThat(issued.expiresIn()).isEqualTo(3600);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        SecretKey key = configuration.jwtSigningKey(properties);
        byte[] otherBytes = new byte[32];
        otherBytes[0] = 1;
        SecretKey otherKey = configuration.jwtSigningKey(
                new JwtProperties(Base64.getEncoder().encodeToString(otherBytes), 3600));
        String token = new JwtTokenIssuer(configuration.jwtEncoder(otherKey), properties)
                .issue(UUID.randomUUID()).accessToken();

        assertThatThrownBy(() -> configuration.jwtDecoder(key).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredTokensWrongIssuerAndMissingExpiration() {
        SecretKey key = configuration.jwtSigningKey(properties);
        Instant now = Instant.now();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var encoder = configuration.jwtEncoder(key);
        var decoder = configuration.jwtDecoder(key);
        var expired = JwtClaimsSet.builder().issuer(JwtConfiguration.ISSUER).subject("trainer")
                .issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600)).build();
        var wrongIssuer = JwtClaimsSet.builder().issuer("another-application").subject("trainer")
                .expiresAt(now.plusSeconds(3600)).build();
        var noExpiration = JwtClaimsSet.builder().issuer(JwtConfiguration.ISSUER).subject("trainer").build();
        var noSubject = JwtClaimsSet.builder().issuer(JwtConfiguration.ISSUER)
                .expiresAt(now.plusSeconds(3600)).build();

        for (var claims : new JwtClaimsSet[] {expired, wrongIssuer, noExpiration, noSubject}) {
            String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void rejectsMalformedAndWeakSigningConfiguration() {
        assertThatThrownBy(() -> configuration.jwtSigningKey(new JwtProperties("${JWT_SECRET}", 3600)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("JWT_SECRET must be valid Base64");
        assertThatThrownBy(() -> configuration.jwtSigningKey(new JwtProperties("", 3600)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuration.jwtSigningKey(
                new JwtProperties(Base64.getEncoder().encodeToString(new byte[16]), 3600)))
                .isInstanceOf(IllegalArgumentException.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new JwtProperties("", 0))).hasSize(2);
        }
    }
}
