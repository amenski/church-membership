package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.repository.CommunicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
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
    @Transactional(readOnly = true)
    public Optional<Communication> invoke(Long id) {
        return communicationRepository.findById(id);
    }
}