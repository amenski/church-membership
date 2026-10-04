package io.github.membertracker.domain.model;

import java.util.List;

/**
 * A household with everyone in it: its members (the memberships, {@code members}) and its people (members and
 * dependents alike, {@code people}). Archived members are included in both; the caller decides who may see them.
 */
public record HouseholdDetails(Household household, List<Member> members, List<Person> people) {
}
