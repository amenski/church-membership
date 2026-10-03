package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    // Single-row SQL updates: the database does the arithmetic, so parallel attempts cannot overwrite each other.
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserEntity u set u.failedLoginAttempts = u.failedLoginAttempts + 1 where u.id = :id")
    int incrementFailedLoginAttempts(@Param("id") Long id);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserEntity u set u.accountNonLocked = false, u.lockedUntil = :until "
            + "where u.id = :id and u.failedLoginAttempts >= :max and u.accountNonLocked = true")
    int lockWhenThresholdReached(@Param("id") Long id, @Param("max") int max, @Param("until") LocalDateTime until);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserEntity u set u.failedLoginAttempts = 0, u.accountNonLocked = true, u.lockedUntil = null where u.id = :id")
    int resetFailedLogins(@Param("id") Long id);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserEntity u set u.password = :password, u.lastPasswordChange = :changedAt, "
            + "u.updatedAt = :changedAt, u.failedLoginAttempts = 0 where u.id = :id")
    int updatePassword(@Param("id") Long id, @Param("password") String password, @Param("changedAt") LocalDateTime changedAt);
}