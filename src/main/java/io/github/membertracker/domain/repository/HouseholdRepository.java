package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.model.Household;
import io.github.membertracker.domain.model.HouseholdSummary;
import io.github.membertracker.domain.model.Member;

import java.util.List;
import java.util.Optional;

public interface HouseholdRepository {
    /** Every household by name, with its member counts. */
    List<HouseholdSummary> findAllSummaries();

    Optional<Household> findById(Long id);

    /** The members of the household's people, archived ones included, by name. */
    List<Member> findMembers(Long householdId);

    /** People assigned to the household, whether or not they have a membership and whatever its status. */
    long countPeople(Long householdId);

    Household save(Household household);

    void deleteById(Long id);
}
