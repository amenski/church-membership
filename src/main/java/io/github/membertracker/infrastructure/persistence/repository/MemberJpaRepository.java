package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberJpaRepository extends JpaRepository<MemberEntity, Long> {
    @EntityGraph(attributePaths = {"person", "person.household"})
    List<MemberEntity> findByStatusOrderByIdAsc(String status);

    @EntityGraph(attributePaths = {"person", "person.household"})
    List<MemberEntity> findByStatusNotOrderByIdAsc(String status);

    long countByStatus(String status);

    long countByStatusNot(String status);

    @EntityGraph(attributePaths = {"person", "person.household"})
    List<MemberEntity> findByStatusAndConsecutiveMonthsMissedGreaterThanEqualOrderByIdAsc(String status, int months);

    long countByStatusAndConsecutiveMonthsMissedGreaterThanEqual(String status, int months);

    @EntityGraph(attributePaths = {"person", "person.household"})
    List<MemberEntity> findByStatusAndConsecutiveMonthsMissedGreaterThanEqualOrderByConsecutiveMonthsMissedDescPersonNameAscIdAsc(String status, int months);

    @EntityGraph(attributePaths = {"person", "person.household"})
    List<MemberEntity> findByPersonHouseholdIdOrderByPersonNameAscIdAsc(Long householdId);

    Optional<MemberEntity> findByPersonId(Long personId);
}
