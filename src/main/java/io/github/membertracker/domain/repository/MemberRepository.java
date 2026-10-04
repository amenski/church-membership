package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;

import java.util.List;
import java.util.Optional;

/**
 * "Dues paying" means status MEMBER, the only status for which dues, reminders, messages and payments apply
 * ({@link MemberStatus#countsForDues()}). ARCHIVED members are hidden from every list and count except
 * {@link #findByStatus(MemberStatus)} and {@link #findById(Long)}.
 */
public interface MemberRepository {
    /** Every member except the archived ones, by id. */
    List<Member> findAll();

    Optional<Member> findById(Long id);

    /** Members with exactly this status, by id. */
    List<Member> findByStatus(MemberStatus status);

    /** Status MEMBER, by id. */
    List<Member> findDuesPaying();

    /** Dues-paying members at least this many months behind; other statuses are never returned. */
    List<Member> findByConsecutiveMonthsMissedGreaterThanEqual(int months);

    /** Every member except the archived ones. */
    long countNotArchived();

    /** Status MEMBER. */
    long countDuesPaying();

    /** Dues-paying members at least this many months behind. */
    long countDuesPayingWithMissedAtLeast(int months);

    /** Dues-paying members at least this many months behind, the one furthest behind first. */
    List<Member> findDuesPayingWithMissedAtLeastOrderByMissedDesc(int months);

    Member save(Member member);

    void deleteById(Long id);
}
