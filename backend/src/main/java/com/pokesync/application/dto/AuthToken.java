package com.pokesync.application.dto;

public record AuthToken(String accessToken, long expiresIn) {
    @Override
    public String toString() {
        return "AuthToken[accessToken=<redacted>, expiresIn=" + expiresIn + "]";
    }
}
