package io.github.membertracker.infrastructure.dto;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Person;

import java.time.LocalDate;

/**
 * A person as the API returns it: the person's own fields plus the read-only membership and household name.
 * {@code memberId} and {@code memberStatus} are null when the person has no membership (a dependent); the frontend
 * tells a member from a dependent by {@code memberId}.
 */
public record PersonResponse(Long id, String name, String email, String phone, LocalDate birthDate,
                             Long householdId, String householdName, Long memberId, MemberStatus memberStatus) {

    public static PersonResponse of(Person person) {
        return new PersonResponse(person.id(), person.name(), person.email(), person.phone(), person.birthDate(),
                person.householdId(), person.householdName(), person.memberId(), person.memberStatus());
    }
}
