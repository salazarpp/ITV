package com.pokesync.presentation.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pokesync.application.dto.AuthToken;
import com.pokesync.application.exception.AuthenticationFailedException;
import com.pokesync.application.exception.PasswordTooLongException;
import com.pokesync.application.exception.UserAlreadyExistsException;
import com.pokesync.application.service.AuthService;
import com.pokesync.domain.model.User;
import com.pokesync.infrastructure.security.JwtConfiguration;
import com.pokesync.infrastructure.security.SecurityConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = AuthController.class, properties = {
        "pokesync.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "pokesync.security.jwt.expiration-seconds=3600"
})
@Import({SecurityConfiguration.class, JwtConfiguration.class, AuthControllerTest.SecurityProbeController.class})
class AuthControllerTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder encoder;

    @MockitoBean
    private AuthService auth;

    @Test
    void publicRegistrationReturnsUserWithoutHash() throws Exception {
        UUID id = UUID.randomUUID();
        when(auth.register("trainer", "trainer@example.com", "example-password"))
                .thenReturn(new User(id, "trainer", "trainer@example.com", "stored-hash"));

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"trainer","email":"trainer@example.com","password":"example-password"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void publicLoginReturnsBearerTokenAndDisablesCaching() throws Exception {
        when(auth.login("trainer", "example-password")).thenReturn(new AuthToken("test-token", 3600));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"trainer","password":"example-password"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void malformedAndInvalidRegistrationReturn400() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"","email":"not-an-email","password":""}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void tooLongPasswordReturns400() throws Exception {
        when(auth.register(anyString(), anyString(), anyString())).thenThrow(new PasswordTooLongException());

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"trainer","email":"trainer@example.com","password":"example-password"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void internalIllegalArgumentRemains500AndDoesNotExposeItsMessage() throws Exception {
        when(auth.register(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("internal-sensitive-detail"));

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"trainer","email":"trainer@example.com","password":"example-password"}
                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void duplicateRegistrationReturns409() throws Exception {
        when(auth.register(anyString(), anyString(), anyString())).thenThrow(new UserAlreadyExistsException());

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"trainer","email":"trainer@example.com","password":"example-password"}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
    }

    @Test
    void incorrectCredentialsReturn401() throws Exception {
        when(auth.login(anyString(), anyString())).thenThrow(new AuthenticationFailedException());

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"trainer","password":"wrong-password"}
                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void browsingIsPublicButModificationRequiresARealSignedToken() throws Exception {
        mvc.perform(get("/api/v1/pokemon")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/pokemon"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/v1/pokemon").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(JwtConfiguration.ISSUER).subject(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(post("/api/v1/pokemon").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void expiredAndWrongIssuerTokensAreRejectedByTheFilterChain() throws Exception {
        Instant now = Instant.now();
        var expired = JwtClaimsSet.builder().issuer(JwtConfiguration.ISSUER).subject("trainer")
                .issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600)).build();
        var wrongIssuer = JwtClaimsSet.builder().issuer("another-application").subject("trainer")
                .issuedAt(now).expiresAt(now.plusSeconds(3600)).build();

        for (var claims : new JwtClaimsSet[] {expired, wrongIssuer}) {
            String token = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            mvc.perform(post("/api/v1/pokemon").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists("WWW-Authenticate"))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    // Test-only endpoints demonstrate the policy without implementing Pokemon features.
    @RestController
    public static class SecurityProbeController {
        @GetMapping("/api/v1/pokemon")
        String browse() {
            return "public";
        }

        @PostMapping("/api/v1/pokemon")
        String modify() {
            return "protected";
        }
    }
}
