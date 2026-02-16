package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MessageDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class GetDeliveriesByCommunicationUseCase {

    private static final Logger logger = LoggerFactory.getLogger(GetDeliveriesByCommunicationUseCase.class);

    private final CommunicationRepository communicationRepository;
    private final MessageDeliveryRepository messageDeliveryRepository;

    public GetDeliveriesByCommunicationUseCase(CommunicationRepository communicationRepository,
                                               MessageDeliveryRepository messageDeliveryRepository) {
        this.communicationRepository = communicationRepository;
        this.messageDeliveryRepository = messageDeliveryRepository;
    }

    /**
     * Get all deliveries for a specific communication.
     *
     * @param communicationId the ID of the communication
     * @return list of message deliveries
     */
    public List<MessageDelivery> invoke(Long communicationId) {
        logger.debug("Fetching deliveries for communication ID: {}", communicationId);

        // Verify communication exists
        Optional<?> communication = communicationRepository.findById(communicationId);
        if (communication.isEmpty()) {
            logger.warn("Communication not found: {}", communicationId);
            return Collections.emptyList();
        }

        List<MessageDelivery> deliveries = messageDeliveryRepository.findByCommunicationId(communicationId);
        logger.info("Found {} deliveries for communication {}", deliveries.size(), communicationId);

        return deliveries;
    }
}
