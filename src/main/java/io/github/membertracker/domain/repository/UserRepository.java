package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.model.User;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findByEmail(String email);

    Optional<User> findById(Long id);

    User save(User user);

    User update(User user);

    boolean existsByEmail(String email);
}