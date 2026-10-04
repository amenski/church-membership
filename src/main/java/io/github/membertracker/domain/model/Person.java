package io.github.membertracker.domain.model;

import io.github.membertracker.domain.enumeration.MemberStatus;

import java.time.LocalDate;

/**
 * A person on the register: a member or a dependent (a child, a spouse who does not pay dues) with no membership.
 * {@code memberId} and {@code memberStatus} are null for a person without a membership; they are read-only here,
 * the membership is changed through the member use cases. {@code householdName} is read-only too.
 */
public record Person(Long id, String name, String email, String phone, LocalDate birthDate,
                     Long householdId, String householdName, Long memberId, MemberStatus memberStatus) {

    public boolean hasMembership() {
        return memberId != null;
    }

    public boolean isArchivedMember() {
        return memberStatus == MemberStatus.ARCHIVED;
    }

    /** A person who is not yet stored and has no membership. */
    public static Person newPerson(String name, String email, String phone, LocalDate birthDate, Long householdId) {
        return new Person(null, name, email, phone, birthDate, householdId, null, null, null);
    }

    /** The same person with the editable fields replaced; the membership and the household name are kept. */
    public Person withDetails(String name, String email, String phone, LocalDate birthDate, Long householdId) {
        return new Person(id, name, email, phone, birthDate, householdId, householdName, memberId, memberStatus);
    }
}
