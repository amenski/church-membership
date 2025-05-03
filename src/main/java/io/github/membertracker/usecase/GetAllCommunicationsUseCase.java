package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.repository.CommunicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetAllCommunicationsUseCase {

    private final CommunicationRepository communicationRepository;

    public GetAllCommunicationsUseCase(CommunicationRepository communicationRepository) {
        this.communicationRepository = communicationRepository;
    }

    /**
     * Retrieves all communications from the database.
     *
     * @return a list of all communications
     */
    @Transactional(readOnly = true)
    public List<Communication> invoke() {
        return communicationRepository.findAll();
    }
}