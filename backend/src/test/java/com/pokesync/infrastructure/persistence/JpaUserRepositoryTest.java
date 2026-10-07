package com.pokesync.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pokesync.application.exception.UserAlreadyExistsException;
import com.pokesync.domain.model.User;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

class JpaUserRepositoryTest {
    private final SpringDataUserRepository delegate = mock(SpringDataUserRepository.class);
    private final JpaUserRepository repository = new JpaUserRepository(delegate);

    @Test
    void mapsSavedAndRetrievedUser() {
        User user = new User(UUID.randomUUID(), "trainer", "trainer@example.com", "stored-hash");
        when(delegate.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(delegate.findByUsername("trainer")).thenReturn(Optional.of(new UserEntity(user)));

        assertThat(repository.save(user)).isEqualTo(user);
        assertThat(repository.findByUsername("trainer")).contains(user);
    }

    @Test
    void uniqueConstraintRaceBecomesRegistrationConflict() {
        when(delegate.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate", new SQLIntegrityConstraintViolationException("duplicate", "23505")));
        User user = new User(UUID.randomUUID(), "trainer", "trainer@example.com", "stored-hash");

        assertThatThrownBy(() -> repository.save(user)).isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void registrationEntityIsInsertedEvenThoughItsIdIsAssigned() {
        User user = new User(UUID.randomUUID(), "trainer", "trainer@example.com", "stored-hash");
        when(delegate.saveAndFlush(any())).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            assertThat(entity.getId()).isEqualTo(user.id());
            assertThat(entity.isNew()).isTrue();
            return entity;
        });

        assertThat(repository.save(user)).isEqualTo(user);
    }

    @Test
    void mapsHibernateWrappedDuplicateEvenWithDeeperNonSqlCause() {
        SQLException sql = new SQLException("duplicate", "23505", new IllegalStateException("driver detail"));
        when(delegate.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate", new ConstraintViolationException("duplicate", sql, "uk_app_users_username")));
        User user = new User(UUID.randomUUID(), "trainer", "trainer@example.com", "stored-hash");

        assertThatThrownBy(() -> repository.save(user)).isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void nonSqlIntegrityFailureIsNotReportedAsDuplicate() {
        var failure = new DataIntegrityViolationException("other failure", new IllegalStateException("no sql"));
        when(delegate.saveAndFlush(any())).thenThrow(failure);
        User user = new User(UUID.randomUUID(), "trainer", "trainer@example.com", "stored-hash");

        assertThatThrownBy(() -> repository.save(user)).isSameAs(failure);
    }

    @Test
    void otherIntegrityErrorsRemainServerErrors() {
        var failure = new DataIntegrityViolationException(
                "not null", new SQLIntegrityConstraintViolationException("not null", "23502"));
        when(delegate.saveAndFlush(any())).thenThrow(failure);
        User user = new User(UUID.randomUUID(), "trainer", "trainer@example.com", "stored-hash");

        assertThatThrownBy(() -> repository.save(user)).isSameAs(failure);
    }
}
