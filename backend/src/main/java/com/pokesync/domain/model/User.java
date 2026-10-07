package com.pokesync.domain.model;

import java.util.UUID;

public record User(UUID id, String username, String email, String passwordHash) {

    @Override
    public String toString() {
        return "User[id=" + id + ", username=" + username + "]";
    }
}
