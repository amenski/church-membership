package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.exception.HouseholdDomainException;
import io.github.membertracker.domain.model.Household;
import io.github.membertracker.domain.repository.HouseholdRepository;

import java.util.Optional;

/**
 * Deletes a household that nobody belongs to. People are never deleted or moved by it: a household that still has
 * any person (archived members count) is refused, so nobody loses their household behind their back.
 */
public class DeleteHouseholdUseCase {

    private final HouseholdRepository householdRepository;
    private final RecordActivityUseCase recordActivity;

    public DeleteHouseholdUseCase(HouseholdRepository householdRepository, RecordActivityUseCase recordActivity) {
        this.householdRepository = householdRepository;
        this.recordActivity = recordActivity;
    }

    /**
     * @return true when the household was deleted, false when there is no household with that id
     * @throws HouseholdDomainException (409) when people are still assigned to it
     */
    public boolean invoke(Long id) {
        Optional<Household> found = householdRepository.findById(id);
        if (found.isEmpty()) {
            return false;
        }
        if (householdRepository.countPeople(id) > 0) {
            throw HouseholdDomainException.hasPeople();
        }
        householdRepository.deleteById(id);
        recordActivity.record(ActivityType.HOUSEHOLD_DELETED, "Household " + found.get().getName() + " was deleted",
                "HOUSEHOLD", id);
        return true;
    }
}
