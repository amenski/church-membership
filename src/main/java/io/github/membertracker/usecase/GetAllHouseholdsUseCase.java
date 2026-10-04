package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.HouseholdSummary;
import io.github.membertracker.domain.repository.HouseholdRepository;

import java.util.List;

public class GetAllHouseholdsUseCase {

    private final HouseholdRepository householdRepository;

    public GetAllHouseholdsUseCase(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    /** Every household by name, with member counts. */
    public List<HouseholdSummary> invoke() {
        return householdRepository.findAllSummaries();
    }
}
