package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.model.Member;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MemberRepository {
    List<Member> findAll();
    
    Optional<Member> findById(Long id);
    
    List<Member> findByActive(boolean active);
    
    List<Member> findMembersWithLastPaymentBefore(LocalDate date);
    
    List<Member> findByConsecutiveMonthsMissedGreaterThanEqual(int months);
    
    Member save(Member member);
    
    void deleteById(Long id);
}