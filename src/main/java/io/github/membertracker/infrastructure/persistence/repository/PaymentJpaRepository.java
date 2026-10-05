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

    /**
     * A filtered page with the member, person and household in the same statement. {@code method} is "" for any
     * method; {@code namePattern} is a LIKE pattern (escape character "!"); {@code receiptId} is the payment id a
     * receipt number in the search points at, or -1 when the search is not one. Written without null parameters so it
     * runs the same on MySQL and H2.
     */
    @Query(value = "select p from PaymentEntity p join fetch p.member m join fetch m.person pe left join fetch pe.household "
            + "where (:method = '' or p.paymentMethod = :method) "
            + "and (lower(pe.name) like :namePattern escape '!' or p.id = :receiptId)",
            countQuery = "select count(p) from PaymentEntity p join p.member m join m.person pe "
            + "where (:method = '' or p.paymentMethod = :method) "
            + "and (lower(pe.name) like :namePattern escape '!' or p.id = :receiptId)")
    Page<PaymentEntity> findPage(@Param("method") String method, @Param("namePattern") String namePattern,
                                 @Param("receiptId") long receiptId, Pageable pageable);

    /** One row per member and paid month, oldest month first within a member. */
    @Query("select p.member.id, p.period from PaymentEntity p where p.period >= :from and p.period <= :to "
            + "group by p.member.id, p.period order by p.member.id, p.period")
    List<Object[]> findPaidMonthsBetween(@Param("from") YearMonth from, @Param("to") YearMonth to);

    @Query("select p from PaymentEntity p join fetch p.member m join fetch m.person pe left join fetch pe.household "
            + "where p.member = :member")
    List<PaymentEntity> findByMember(@Param("member") MemberEntity member);

    long countByMemberId(Long memberId);

    boolean existsByMemberAndPeriod(MemberEntity member, YearMonth period);

    @Query("select coalesce(sum(p.amount), 0.0) from PaymentEntity p where p.period = :period")
    double sumAmountByPeriod(@Param("period") YearMonth period);
}
