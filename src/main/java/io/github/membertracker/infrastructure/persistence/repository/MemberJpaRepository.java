package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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

    /** {@code email} must already be trimmed and lower-cased. */
    @EntityGraph(attributePaths = {"person", "person.household"})
    @Query("select m from MemberEntity m where lower(trim(m.person.email)) = :email and m.status <> 'ARCHIVED' order by m.id asc")
    List<MemberEntity> findNotArchivedByEmail(@Param("email") String email);
}
