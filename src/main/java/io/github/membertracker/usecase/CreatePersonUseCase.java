package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.model.Person;
import io.github.membertracker.domain.repository.PersonRepository;

import java.time.LocalDate;

/**
 * Creates a person with no membership: a child or a dependent who pays no dues and receives no messages. The activity
 * entry carries the name only, never a contact detail or the birth date.
 */
public class CreatePersonUseCase {

    private final PersonRepository personRepository;
    private final RecordActivityUseCase recordActivity;

    public CreatePersonUseCase(PersonRepository personRepository, RecordActivityUseCase recordActivity) {
        this.personRepository = personRepository;
        this.recordActivity = recordActivity;
    }

    /** @param householdId the household to put the person in, or null; an unknown id is refused (HouseholdDomainException, 400) */
    public Person invoke(String name, String email, String phone, LocalDate birthDate, Long householdId) {
        Person saved = personRepository.save(Person.newPerson(name, email, phone, birthDate, householdId));
        recordActivity.record(ActivityType.PERSON_CREATED, "Person " + saved.name() + " was added", "PERSON", saved.id());
        return saved;
    }
}
