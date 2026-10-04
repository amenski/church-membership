package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.model.HouseholdSummary;
import io.github.membertracker.infrastructure.persistence.entity.HouseholdEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HouseholdJpaRepository extends JpaRepository<HouseholdEntity, Long> {

    /** One statement for the whole list: every household, even an empty one, with its membership counts. */
    @Query("""
            select new io.github.membertracker.domain.model.HouseholdSummary(
                h.id, h.name, h.city, count(m.id), count(case when m.status = 'ARCHIVED' then 1 end))
            from HouseholdEntity h
            left join PersonEntity p on p.household = h
            left join MemberEntity m on m.person = p
            group by h.id, h.name, h.city
            order by h.name asc, h.id asc""")
    List<HouseholdSummary> findAllSummaries();
}
