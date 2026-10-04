package io.github.membertracker.infrastructure.dto;

import io.github.membertracker.domain.model.HouseholdSummary;

/**
 * One row of GET /api/households. {@code memberCount} counts the memberships the caller may see and {@code personCount}
 * the people (members and dependents); archived members are ADMIN only, so their persons leave both counts too.
 */
public record HouseholdSummaryResponse(Long id, String name, String city, long memberCount, long personCount) {

    public static HouseholdSummaryResponse of(HouseholdSummary summary, boolean canSeeArchived) {
        long visibleMembers = canSeeArchived
                ? summary.memberCount()
                : summary.memberCount() - summary.archivedMemberCount();
        long visiblePeople = canSeeArchived
                ? summary.personCount()
                : summary.personCount() - summary.archivedMemberCount();
        return new HouseholdSummaryResponse(summary.id(), summary.name(), summary.city(), visibleMembers, visiblePeople);
    }
}
