package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberJpaRepository extends JpaRepository<MemberEntity, Long> {
    List<MemberEntity> findByStatusOrderByIdAsc(String status);

    List<MemberEntity> findByStatusNotOrderByIdAsc(String status);

    long countByStatus(String status);

    long countByStatusNot(String status);

    List<MemberEntity> findByStatusAndConsecutiveMonthsMissedGreaterThanEqualOrderByIdAsc(String status, int months);

    long countByStatusAndConsecutiveMonthsMissedGreaterThanEqual(String status, int months);

    List<MemberEntity> findByStatusAndConsecutiveMonthsMissedGreaterThanEqualOrderByConsecutiveMonthsMissedDescNameAscIdAsc(String status, int months);
}
