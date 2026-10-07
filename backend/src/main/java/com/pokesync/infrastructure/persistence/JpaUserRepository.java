package com.pokesync.infrastructure.persistence;

import com.pokesync.application.exception.UserAlreadyExistsException;
import com.pokesync.application.port.out.UserRepository;
import com.pokesync.domain.model.User;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class JpaUserRepository implements UserRepository {
    private final SpringDataUserRepository repository;

    public JpaUserRepository(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return repository.findByUsername(username).map(UserEntity::toDomain);
    }

    @Override
    public boolean existsByUsernameOrEmail(String username, String email) {
        return repository.existsByUsernameOrEmail(username, email);
    }

    @Override
    public User save(User user) {
        try {
            return repository.saveAndFlush(new UserEntity(user)).toDomain();
        } catch (DataIntegrityViolationException exception) {
            // Handle only unique violations; other persistence errors remain server errors.
            Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Throwable cause = exception; cause != null && seen.add(cause); cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new UserAlreadyExistsException();
                }
            }
            throw exception;
        }
    }
}
