package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.ActivityLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityLogJpaRepository extends JpaRepository<ActivityLogEntity, Long> {
    /** Newest first; the id breaks ties between entries written in the same instant. */
    List<ActivityLogEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
