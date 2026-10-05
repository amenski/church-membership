package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.model.User;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByEmail(String email);

    Optional<User> findById(Long id);

    User save(User user);

    User update(User user);

    boolean existsByEmail(String email);

    long count();

    /**
     * Atomically counts one failed sign-in and, once the counter reaches {@code maxAttempts},
     * locks the account until {@code lockedUntil}. Touches only those columns.
     */
    void recordFailedLogin(Long userId, int maxAttempts, LocalDateTime lockedUntil);

    /** Sets the failed-attempt counter to 0 and clears any lock. Touches only those columns. */
    void resetFailedLogins(Long userId);

    /** Stores a new password hash and the change time and resets the counter. Touches only those columns. */
    void updatePassword(Long userId, String encodedPassword, LocalDateTime changedAt);
}