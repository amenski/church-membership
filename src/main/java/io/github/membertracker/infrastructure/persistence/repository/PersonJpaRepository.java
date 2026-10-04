package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.PersonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PersonJpaRepository extends JpaRepository<PersonEntity, Long> {
    long countByHouseholdId(Long householdId);

    String ROW = """
            select new io.github.membertracker.infrastructure.persistence.repository.PersonRow(
                p.id, p.name, p.email, p.phone, p.birthDate, h.id, h.name, m.id, m.status)
            from PersonEntity p
            left join p.household h
            left join MemberEntity m on m.person = p
            """;

    /** One joined statement per list: no person, household or membership is loaded one by one. */
    @Query(ROW + "where m.id is null or m.status <> 'ARCHIVED' order by p.name asc, p.id asc")
    List<PersonRow> findRowsNotArchived();

    @Query(ROW + "where m.id is null order by p.name asc, p.id asc")
    List<PersonRow> findRowsWithoutMembership();

    @Query(ROW + "where p.id = :id")
    Optional<PersonRow> findRowById(Long id);

    @Query(ROW + "where h.id = :householdId order by p.name asc, p.id asc")
    List<PersonRow> findRowsByHousehold(Long householdId);
}
