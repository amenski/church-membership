package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.repository.CommunicationRepository;

import java.time.LocalDateTime;

public class CreateCommunicationUseCase {

    private final CommunicationRepository communicationRepository;

    public CreateCommunicationUseCase(CommunicationRepository communicationRepository) {
        this.communicationRepository = communicationRepository;
    }

    public Communication invoke(Communication communication) {
        communication.setCreatedDate(LocalDateTime.now());
        return communicationRepository.save(communication);
    }
}
