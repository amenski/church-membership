package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberJpaRepository extends JpaRepository<MemberEntity, Long> {
    List<MemberEntity> findByActive(boolean active);

    List<MemberEntity> findByConsecutiveMonthsMissedGreaterThanEqual(int months);

    boolean existsByEmailIgnoreCase(String email);

    Optional<MemberEntity> findByEmailIgnoreCase(String email);
}
