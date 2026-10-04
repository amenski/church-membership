package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Person;
import io.github.membertracker.domain.repository.PersonRepository;

import java.util.Optional;

public class GetPersonByIdUseCase {

    private final PersonRepository personRepository;

    public GetPersonByIdUseCase(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    /** The person even when their membership is archived: hiding that from non-admins is the caller's job. */
    public Optional<Person> invoke(Long id) {
        return personRepository.findById(id);
    }
}
