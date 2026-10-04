package io.github.membertracker.domain.model;

/**
 * One row of the household list. {@code memberCount} counts every membership of the household's people, archived
 * ones included; {@code personCount} counts every person (members and dependents), archived members' persons included;
 * {@code archivedMemberCount} says how many of the memberships are archived, so a caller who may not see archived
 * members can subtract them from either count (a person with an archived membership is one person).
 */
public record HouseholdSummary(Long id, String name, String city, long memberCount, long archivedMemberCount,
                               long personCount) {
}
