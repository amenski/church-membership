package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Person;
import io.github.membertracker.domain.repository.PersonRepository;

import java.util.List;

public class GetPeopleUseCase {

    private final PersonRepository personRepository;

    public GetPeopleUseCase(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    /**
     * @param withoutMembership true lists only people with no membership (dependents); false lists everyone except
     *                          people whose membership is archived
     */
    public List<Person> invoke(boolean withoutMembership) {
        return withoutMembership ? personRepository.findWithoutMembership() : personRepository.findAll();
    }
}
