package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.repository.CommunicationRepository;

import java.util.List;

public class GetAllCommunicationsUseCase {

    private final CommunicationRepository communicationRepository;

    public GetAllCommunicationsUseCase(CommunicationRepository communicationRepository) {
        this.communicationRepository = communicationRepository;
    }

    public List<Communication> invoke() {
        return communicationRepository.findAll();
    }
}