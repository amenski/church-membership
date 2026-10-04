package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.model.Person;
import io.github.membertracker.domain.repository.PersonRepository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Edits name, email, phone, birth date and household of a person. A person with a membership keeps the legacy
 * member columns in step (the repository does it), so the member screens and this one always agree.
 */
public class UpdatePersonUseCase {

    private final PersonRepository personRepository;
    private final RecordActivityUseCase recordActivity;

    public UpdatePersonUseCase(PersonRepository personRepository, RecordActivityUseCase recordActivity) {
        this.personRepository = personRepository;
        this.recordActivity = recordActivity;
    }

    /**
     * @param setHousehold false leaves the household as it is (the request did not mention it); true applies
     *                     {@code householdId}, where null removes the person from its household. An unknown id is
     *                     refused (HouseholdDomainException, 400)
     * @return the updated person, or empty when there is no person with that id
     */
    public Optional<Person> invoke(Long id, String name, String email, String phone, LocalDate birthDate,
                                   boolean setHousehold, Long householdId) {
        return personRepository.findById(id).map(person -> {
            Person saved = personRepository.save(person.withDetails(name, email, phone, birthDate,
                    setHousehold ? householdId : person.householdId()));
            recordActivity.record(ActivityType.PERSON_UPDATED, "Person " + saved.name() + " was updated", "PERSON", saved.id());
            return saved;
        });
    }
}
