package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.HouseholdDetails;
import io.github.membertracker.domain.repository.HouseholdRepository;

import java.util.Optional;

public class GetHouseholdByIdUseCase {

    private final HouseholdRepository householdRepository;

    public GetHouseholdByIdUseCase(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    /** The household with its members and people (archived ones included: hiding them is the caller's job), or empty. */
    public Optional<HouseholdDetails> invoke(Long id) {
        return householdRepository.findById(id)
                .map(household -> new HouseholdDetails(household, householdRepository.findMembers(id),
                        householdRepository.findPeople(id)));
    }
}
