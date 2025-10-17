package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.repository.CommunicationRepository;

import java.util.Optional;

public class GetCommunicationByIdUseCase {

    private final CommunicationRepository communicationRepository;

    public GetCommunicationByIdUseCase(CommunicationRepository communicationRepository) {
        this.communicationRepository = communicationRepository;
    }

    /**
     * Retrieves a communication by its ID.
     *
     * @param id the ID of the communication to retrieve
     * @return an Optional containing the communication if found, or empty if not found
     */
    public Optional<Communication> invoke(Long id) {
        return communicationRepository.findById(id);
    }
}