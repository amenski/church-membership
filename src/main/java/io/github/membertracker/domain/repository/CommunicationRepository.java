package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.model.Communication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CommunicationRepository {
    List<Communication> findAll();

    Optional<Communication> findById(Long id);

    List<Communication> findByType(Communication.CommunicationType type);

    List<Communication> findBySentDateBetween(LocalDateTime start, LocalDateTime end);

    Communication save(Communication communication);
}
