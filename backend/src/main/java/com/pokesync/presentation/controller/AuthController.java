package com.pokesync.presentation.controller;

import com.pokesync.application.dto.AuthToken;
import com.pokesync.application.service.AuthService;
import com.pokesync.domain.model.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = auth.register(request.username(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponse(user.id(), user.username(), user.email()));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthToken token = auth.login(request.username(), request.password());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new TokenResponse(token.accessToken(), "Bearer", token.expiresIn()));
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 255) String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank String password) {
        @Override
        public String toString() {
            return "RegisterRequest[password=<redacted>]";
        }
    }

    public record LoginRequest(@NotBlank @Size(max = 255) String username, @NotBlank String password) {
        @Override
        public String toString() {
            return "LoginRequest[password=<redacted>]";
        }
    }

    public record UserResponse(UUID id, String username, String email) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
        @Override
        public String toString() {
            return "TokenResponse[accessToken=<redacted>, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
        }
    }
}
