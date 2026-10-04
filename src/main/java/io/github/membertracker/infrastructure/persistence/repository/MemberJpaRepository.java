package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberJpaRepository extends JpaRepository<MemberEntity, Long> {
    List<MemberEntity> findByActive(boolean active);

    List<MemberEntity> findByConsecutiveMonthsMissedGreaterThanEqual(int months);

    long countByActive(boolean active);

    long countByActiveTrueAndConsecutiveMonthsMissedGreaterThanEqual(int months);

    List<MemberEntity> findByActiveTrueAndConsecutiveMonthsMissedGreaterThanEqualOrderByConsecutiveMonthsMissedDescNameAscIdAsc(int months);
}
