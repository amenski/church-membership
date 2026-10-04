package io.github.membertracker.domain.model;

import java.util.List;

/** A household with the members of its people, archived ones included (the caller decides who may see them). */
public record HouseholdDetails(Household household, List<Member> members) {
}
