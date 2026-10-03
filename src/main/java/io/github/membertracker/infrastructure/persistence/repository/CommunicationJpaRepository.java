package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.CommunicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunicationJpaRepository extends JpaRepository<CommunicationEntity, Long> {
}
