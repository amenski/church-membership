package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.repository.CommunicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CreateCommunicationUseCase {

    private final CommunicationRepository communicationRepository;

    public CreateCommunicationUseCase(CommunicationRepository communicationRepository) {
        this.communicationRepository = communicationRepository;
    }

    /**
     * Saves a communication to the database.
     *
     * @param communication the communication to save
     * @return the saved communication
     */
    @Transactional
    public Communication invoke(Communication communication) {
        communication.setCreatedDate(LocalDateTime.now());
        return communicationRepository.save(communication);
    }
}
