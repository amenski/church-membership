package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.model.Person;

import java.util.List;
import java.util.Optional;

/**
 * People with or without a membership. Lists exclude people whose membership is ARCHIVED, as the member lists do;
 * {@link #findById(Long)} and {@link #findByHousehold(Long)} return them (the caller hides them from non-admins).
 */
public interface PersonRepository {
    /** Every person except those with an archived membership, by name. */
    List<Person> findAll();

    /** People with no membership at all, by name. */
    List<Person> findWithoutMembership();

    Optional<Person> findById(Long id);

    /** Everyone in the household, archived members included, by name. */
    List<Person> findByHousehold(Long householdId);

    /**
     * Creates the person (no id: no membership) or updates name, email, phone, birth date and household of a stored
     * one. When the person has a membership its legacy member columns are kept in step (dual-write). An unknown
     * household id is refused (HouseholdDomainException, 400). Returns the stored person as read back.
     */
    Person save(Person person);

    /** Removes a person who has no membership; the use case checks that first. */
    void deleteById(Long id);
}
