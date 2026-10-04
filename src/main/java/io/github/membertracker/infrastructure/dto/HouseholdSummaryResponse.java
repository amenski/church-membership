package io.github.membertracker.infrastructure.dto;

import io.github.membertracker.domain.model.HouseholdSummary;

/** One row of GET /api/households. {@code memberCount} counts the members the caller may see (archived ones: ADMIN only). */
public record HouseholdSummaryResponse(Long id, String name, String city, long memberCount) {

    public static HouseholdSummaryResponse of(HouseholdSummary summary, boolean canSeeArchived) {
        long visible = canSeeArchived ? summary.memberCount() : summary.memberCount() - summary.archivedMemberCount();
        return new HouseholdSummaryResponse(summary.id(), summary.name(), summary.city(), visible);
    }
}
