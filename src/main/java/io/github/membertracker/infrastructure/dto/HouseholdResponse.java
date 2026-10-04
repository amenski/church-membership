package io.github.membertracker.infrastructure.dto;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Household;
import io.github.membertracker.domain.model.HouseholdDetails;
import io.github.membertracker.domain.model.Member;

import java.util.List;

/**
 * A household with its members: id, name and status of each. Archived members are listed (status ARCHIVED) only
 * when the caller may see them, like everywhere else.
 */
public record HouseholdResponse(Long id, String name, String addressLine1, String addressLine2, String city,
                                String postalCode, String notes, List<MemberRef> members) {

    public record MemberRef(Long id, String name, MemberStatus status) {
    }

    public static HouseholdResponse of(HouseholdDetails details, boolean canSeeArchived) {
        Household household = details.household();
        List<MemberRef> members = details.members().stream()
                .filter(member -> canSeeArchived || member.getStatus() != MemberStatus.ARCHIVED)
                .map(HouseholdResponse::ref)
                .toList();
        return new HouseholdResponse(household.getId(), household.getName(), household.getAddressLine1(),
                household.getAddressLine2(), household.getCity(), household.getPostalCode(), household.getNotes(),
                members);
    }

    private static MemberRef ref(Member member) {
        return new MemberRef(member.getId(), member.getName(), member.getStatus());
    }
}
