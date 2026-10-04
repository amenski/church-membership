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

    /**
     * Non-archived members whose person email equals {@code email}, ignoring case and surrounding spaces. A list,
     * because two people may share an address; the caller decides what to do with more than one.
     */
    List<Member> findNotArchivedByEmail(String email);

    Member save(Member member);

    /**
     * Saves a new membership for a person who already exists (and has none): the member is linked to that person
     * instead of a new person being created. Name, email and phone of the member are written to the legacy columns
     * as {@link #save(Member)} does; the person's own values and household are not changed.
     */
    Member saveForPerson(Long personId, Member member);

    void deleteById(Long id);
}
