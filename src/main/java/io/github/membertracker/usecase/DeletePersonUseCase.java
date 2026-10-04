package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.exception.PersonDomainException;
import io.github.membertracker.domain.model.Person;
import io.github.membertracker.domain.repository.PersonRepository;

import java.util.Optional;

/**
 * Deletes a person who has no membership. A person with a membership (whatever its status, archived included) is
 * refused: the membership must be archived, or deleted permanently when it was made by mistake, first, so payments and
 * message history are never touched from here.
 */
public class DeletePersonUseCase {

    private final PersonRepository personRepository;
    private final RecordActivityUseCase recordActivity;

    public DeletePersonUseCase(PersonRepository personRepository, RecordActivityUseCase recordActivity) {
        this.personRepository = personRepository;
        this.recordActivity = recordActivity;
    }

    /**
     * @return true when the person was deleted, false when there is no person with that id
     * @throws PersonDomainException (409) when the person has a membership
     */
    public boolean invoke(Long id) {
        Optional<Person> found = personRepository.findById(id);
        if (found.isEmpty()) {
            return false;
        }
        if (found.get().hasMembership()) {
            throw PersonDomainException.hasMembership();
        }
        personRepository.deleteById(id);
        recordActivity.record(ActivityType.PERSON_DELETED, "Person " + found.get().name() + " was deleted", "PERSON", id);
        return true;
    }
}
