package com.pokesync.infrastructure.security;

import com.pokesync.application.dto.AuthToken;
import com.pokesync.application.port.out.TokenIssuer;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenIssuer implements TokenIssuer {
    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public JwtTokenIssuer(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public AuthToken issue(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfiguration.ISSUER)
                .subject(userId.toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plusSeconds(properties.expirationSeconds()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthToken(token, properties.expirationSeconds());
    }
}
