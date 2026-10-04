package io.github.membertracker.infrastructure.dto;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Household;
import io.github.membertracker.domain.model.HouseholdDetails;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Person;

import java.time.LocalDate;
import java.util.List;

/**
 * A household with everyone in it: {@code members} (the memberships: id, name and status of each) and {@code people}
 * (every person, dependents included; {@code memberStatus} is null for a person with no membership). Archived members
 * are listed in both only when the caller may see them, like everywhere else.
 */
public record HouseholdResponse(Long id, String name, String addressLine1, String addressLine2, String city,
                                String postalCode, String notes, List<MemberRef> members, List<PersonRef> people) {

    public record MemberRef(Long id, String name, MemberStatus status) {
    }

    public record PersonRef(Long id, String name, LocalDate birthDate, MemberStatus memberStatus) {
    }

    public static HouseholdResponse of(HouseholdDetails details, boolean canSeeArchived) {
        Household household = details.household();
        List<MemberRef> members = details.members().stream()
                .filter(member -> canSeeArchived || member.getStatus() != MemberStatus.ARCHIVED)
                .map(HouseholdResponse::ref)
                .toList();
        List<PersonRef> people = details.people().stream()
                .filter(person -> canSeeArchived || person.memberStatus() != MemberStatus.ARCHIVED)
                .map(HouseholdResponse::ref)
                .toList();
        return new HouseholdResponse(household.getId(), household.getName(), household.getAddressLine1(),
                household.getAddressLine2(), household.getCity(), household.getPostalCode(), household.getNotes(),
                members, people);
    }

    private static MemberRef ref(Member member) {
        return new MemberRef(member.getId(), member.getName(), member.getStatus());
    }

    private static PersonRef ref(Person person) {
        return new PersonRef(person.id(), person.name(), person.birthDate(), person.memberStatus());
    }
}
