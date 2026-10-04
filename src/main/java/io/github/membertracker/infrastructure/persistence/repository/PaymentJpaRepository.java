package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import io.github.membertracker.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.YearMonth;
import java.util.List;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {
    List<PaymentEntity> findByMember(MemberEntity member);

    long countByMemberId(Long memberId);

    boolean existsByMemberAndPeriod(MemberEntity member, YearMonth period);

    @Query("select coalesce(sum(p.amount), 0.0) from PaymentEntity p where p.period = :period")
    double sumAmountByPeriod(@Param("period") YearMonth period);
}
