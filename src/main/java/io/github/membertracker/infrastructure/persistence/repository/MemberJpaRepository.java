package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface MemberJpaRepository extends JpaRepository<MemberEntity, Long> {
    List<MemberEntity> findByActive(boolean active);

    @Query("SELECT m FROM MemberEntity m WHERE m.lastPaymentDate < :date")
    List<MemberEntity> findMembersWithLastPaymentBefore(LocalDate date);

    List<MemberEntity> findByConsecutiveMonthsMissedGreaterThanEqual(int months);
}
