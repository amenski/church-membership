package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.model.Communication;

import java.util.List;
import java.util.Optional;

public interface CommunicationRepository {
    List<Communication> findAll();

    Optional<Communication> findById(Long id);

    Communication save(Communication communication);
}
