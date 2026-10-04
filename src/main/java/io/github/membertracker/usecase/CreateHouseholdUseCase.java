package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.model.Household;
import io.github.membertracker.domain.model.HouseholdDetails;
import io.github.membertracker.domain.repository.HouseholdRepository;

import java.util.List;

/** Creates an empty household. The activity entry carries the name only, never the address or the notes. */
public class CreateHouseholdUseCase {

    private final HouseholdRepository householdRepository;
    private final RecordActivityUseCase recordActivity;

    public CreateHouseholdUseCase(HouseholdRepository householdRepository, RecordActivityUseCase recordActivity) {
        this.householdRepository = householdRepository;
        this.recordActivity = recordActivity;
    }

    public HouseholdDetails invoke(String name, String addressLine1, String addressLine2, String city,
                                   String postalCode, String notes) {
        Household saved = householdRepository.save(new Household(name, addressLine1, addressLine2, city, postalCode, notes));
        recordActivity.record(ActivityType.HOUSEHOLD_CREATED, "Household " + saved.getName() + " was created",
                "HOUSEHOLD", saved.getId());
        return new HouseholdDetails(saved, List.of());
    }
}
