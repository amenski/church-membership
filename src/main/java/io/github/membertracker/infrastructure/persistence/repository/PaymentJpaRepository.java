package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import io.github.membertracker.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.YearMonth;
import java.util.List;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {
    /**
     * The member, its person and the person's household come in the same query as the payments, not one query per
     * payment. Written as JPQL: an entity graph three levels deep did not join the household.
     */
    @Override
    @Query("select p from PaymentEntity p join fetch p.member m join fetch m.person pe left join fetch pe.household")
    List<PaymentEntity> findAll();

    @Override
    @Query(value = "select p from PaymentEntity p join fetch p.member m join fetch m.person pe left join fetch pe.household",
            countQuery = "select count(p) from PaymentEntity p")
    Page<PaymentEntity> findAll(Pageable pageable);

    @Query("select p from PaymentEntity p join fetch p.member m join fetch m.person pe left join fetch pe.household "
            + "where p.member = :member")
    List<PaymentEntity> findByMember(@Param("member") MemberEntity member);

    long countByMemberId(Long memberId);

    boolean existsByMemberAndPeriod(MemberEntity member, YearMonth period);

    @Query("select coalesce(sum(p.amount), 0.0) from PaymentEntity p where p.period = :period")
    double sumAmountByPeriod(@Param("period") YearMonth period);
}
