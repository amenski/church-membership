package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.model.Household;
import io.github.membertracker.domain.model.HouseholdDetails;
import io.github.membertracker.domain.repository.HouseholdRepository;

import java.util.Optional;

/** Replaces the name, address and notes of a household; who lives in it is changed from the member, never here. */
public class UpdateHouseholdUseCase {

    private final HouseholdRepository householdRepository;
    private final RecordActivityUseCase recordActivity;

    public UpdateHouseholdUseCase(HouseholdRepository householdRepository, RecordActivityUseCase recordActivity) {
        this.householdRepository = householdRepository;
        this.recordActivity = recordActivity;
    }

    /** @return the household with its members, or empty when there is no household with that id */
    public Optional<HouseholdDetails> invoke(Long id, String name, String addressLine1, String addressLine2,
                                             String city, String postalCode, String notes) {
        return householdRepository.findById(id).map(household -> {
            household.setName(name);
            household.setAddressLine1(addressLine1);
            household.setAddressLine2(addressLine2);
            household.setCity(city);
            household.setPostalCode(postalCode);
            household.setNotes(notes);
            Household saved = householdRepository.save(household);
            recordActivity.record(ActivityType.HOUSEHOLD_UPDATED, "Household " + saved.getName() + " was updated",
                    "HOUSEHOLD", saved.getId());
            return new HouseholdDetails(saved, householdRepository.findMembers(saved.getId()),
                    householdRepository.findPeople(saved.getId()));
        });
    }
}
