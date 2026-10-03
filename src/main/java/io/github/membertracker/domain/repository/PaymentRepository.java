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
    
    Payment save(Payment payment);
}