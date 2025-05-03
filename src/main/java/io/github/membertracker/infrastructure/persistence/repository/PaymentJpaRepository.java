package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import io.github.membertracker.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {
    List<PaymentEntity> findByMember(MemberEntity member);

    List<PaymentEntity> findByMemberAndPeriod(MemberEntity member, YearMonth period);

    Optional<PaymentEntity> findFirstByMemberOrderByPaymentDateDesc(MemberEntity member);
}
