package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.repository.CommunicationRepository;

import java.util.List;

public class GetRecentCommunicationsUseCase {

    private final CommunicationRepository communicationRepository;

    public GetRecentCommunicationsUseCase(CommunicationRepository communicationRepository) {
        this.communicationRepository = communicationRepository;
    }

    /** The newest communications. */
    public List<Communication> invoke(int limit) {
        return communicationRepository.findRecent(limit);
    }
}
