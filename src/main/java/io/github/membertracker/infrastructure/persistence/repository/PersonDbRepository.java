package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.exception.HouseholdDomainException;
import io.github.membertracker.domain.model.Person;
import io.github.membertracker.domain.repository.PersonRepository;
import io.github.membertracker.infrastructure.persistence.entity.HouseholdEntity;
import io.github.membertracker.infrastructure.persistence.entity.PersonEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class PersonDbRepository implements PersonRepository {

    private final PersonJpaRepository personJpaRepository;
    private final HouseholdJpaRepository householdJpaRepository;

    public PersonDbRepository(PersonJpaRepository personJpaRepository,
                              HouseholdJpaRepository householdJpaRepository) {
        this.personJpaRepository = personJpaRepository;
        this.householdJpaRepository = householdJpaRepository;
    }

    @Override
    public List<Person> findAll() {
        return toDomain(personJpaRepository.findRowsNotArchived());
    }

    @Override
    public List<Person> findWithoutMembership() {
        return toDomain(personJpaRepository.findRowsWithoutMembership());
    }

    @Override
    public Optional<Person> findById(Long id) {
        return personJpaRepository.findRowById(id).map(PersonRow::toDomain);
    }

    @Override
    public List<Person> findByHousehold(Long householdId) {
        return toDomain(personJpaRepository.findRowsByHousehold(householdId));
    }

    @Override
    @Transactional
    public Person save(Person person) {
        boolean isStored = person.id() != null;
        PersonEntity entity = isStored
                ? personJpaRepository.findById(person.id()).orElseThrow(() -> new IllegalStateException("Person not found"))
                : new PersonEntity();
        entity.setName(person.name());
        entity.setEmail(person.email());
        entity.setPhone(person.phone());
        entity.setBirthDate(person.birthDate());
        entity.setHousehold(resolveHousehold(person.householdId(), isStored ? entity.getHousehold() : null));
        PersonEntity saved = personJpaRepository.save(entity);
        personJpaRepository.flush();
        return personJpaRepository.findRowById(saved.getId()).map(PersonRow::toDomain).orElseThrow();
    }

    @Override
    public void deleteById(Long id) {
        personJpaRepository.deleteById(id);
    }

    /** The stored household when unchanged (no lookup), else the household with that id; an unknown id is a 400. */
    private HouseholdEntity resolveHousehold(Long wanted, HouseholdEntity current) {
        if (wanted == null) {
            return null;
        }
        if (current != null && wanted.equals(current.getId())) {
            return current;
        }
        return householdJpaRepository.findById(wanted).orElseThrow(HouseholdDomainException::notFound);
    }

    private static List<Person> toDomain(List<PersonRow> rows) {
        return rows.stream().map(PersonRow::toDomain).toList();
    }
}
