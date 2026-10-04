package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Person;

import java.time.LocalDate;

/** One joined row (person, household name, membership id and status) read by {@link PersonJpaRepository}. */
public record PersonRow(Long id, String name, String email, String phone, LocalDate birthDate,
                        Long householdId, String householdName, Long memberId, String memberStatus) {

    public Person toDomain() {
        return new Person(id, name, email, phone, birthDate, householdId, householdName, memberId,
                memberStatus == null ? null : MemberStatus.valueOf(memberStatus));
    }
}
