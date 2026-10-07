package com.pokesync.infrastructure.persistence;

import com.pokesync.domain.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "app_users")
public class UserEntity implements Persistable<UUID> {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Transient
    private boolean newEntity = true;

    protected UserEntity() {
    }

    public UserEntity(User user) {
        this.id = user.id();
        this.username = user.username();
        this.email = user.email();
        this.passwordHash = user.passwordHash();
    }

    public User toDomain() {
        return new User(id, username, email, passwordHash);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newEntity = false;
    }
}
