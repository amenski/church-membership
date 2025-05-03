package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.CommunicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CommunicationJpaRepository extends JpaRepository<CommunicationEntity, Long> {
    List<CommunicationEntity> findByType(CommunicationEntity.CommunicationType type);

    List<CommunicationEntity> findBySentDateBetween(LocalDateTime start, LocalDateTime end);
}
