package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.model.Member;

import java.util.List;
import java.util.Optional;

public interface MemberRepository {
    List<Member> findAll();
    
    Optional<Member> findById(Long id);
    
    List<Member> findByActive(boolean active);
    
    List<Member> findByConsecutiveMonthsMissedGreaterThanEqual(int months);
    
    long countAll();

    long countByActive(boolean active);

    /** Active members only. */
    long countActiveWithMissedAtLeast(int months);

    /** Active members only, the one furthest behind first. */
    List<Member> findActiveWithMissedAtLeastOrderByMissedDesc(int months);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Member> findByEmailIgnoreCase(String email);

    Member save(Member member);
    
    void deleteById(Long id);
}