package io.github.membertracker.domain.model;

/**
 * One row of the household list. {@code memberCount} counts every membership of the household's people, archived
 * ones included; {@code archivedMemberCount} says how many of those are archived, so a caller who may not see
 * archived members can subtract them.
 */
public record HouseholdSummary(Long id, String name, String city, long memberCount, long archivedMemberCount) {
}
