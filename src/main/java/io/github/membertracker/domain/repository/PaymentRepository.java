package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    List<Payment> findAll();
    
    Optional<Payment> findById(Long id);
    
    List<Payment> findByMember(Member member);
    
    boolean existsByMemberAndPeriod(Member member, YearMonth period);
    
    /** Sum of the amounts whose billing period is {@code period}; 0.0 when there are none. */
    double sumAmountByPeriod(YearMonth period);

    /** Newest payment date first, then newest id. */
    List<Payment> findRecent(int limit);

    /** How many payments this member has, whatever their period. */
    long countByMemberId(Long memberId);

    Payment save(Payment payment);
}