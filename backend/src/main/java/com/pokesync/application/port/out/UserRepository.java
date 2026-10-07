package com.pokesync.application.port.out;

import com.pokesync.domain.model.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);

    boolean existsByUsernameOrEmail(String username, String email);

    User save(User user);
}
